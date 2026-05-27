# dev.ps1 - Start, stop, restart or rebuild the Webshop backend via Docker
# Usage:  ./dev.bat start   | stop | restart | rebuild
#         ./dev.bat stop    --keep-db   (stop backend container, keep PostgreSQL running)
#         ./dev.bat restart --keep-db
#         ./dev.bat rebuild --keep-db
#         ./dev.bat         (no argument: shows help and prompts interactively)
#
# Requirements: Docker Desktop (no Java or Maven needed locally)
#
# How it works:
#   start   -> docker compose up -d --build
#              Auto-detects source changes (src/, pom.xml, Dockerfile) and switches
#              to a full rebuild automatically if any file is newer than the last image.
#              (first run: Maven downloads ~200 MB of dependencies inside the container)
#              Polls /api/health until the app responds, then prints the ready message.
#   stop    -> docker compose down  (or stop only backend with --keep-db)
#   restart -> stop backend + start backend
#   rebuild -> docker compose up -d --build --force-recreate

param(
    [string]$Command = "",
    [switch]$KeepDb,
    [string]$TestFilter = "",
    [switch]$Yes
)

$Root = $PSScriptRoot
$Port = 8080
$HealthUrl = "http://localhost:$Port/api/health"
$IncludeMailpit = $false   # set by 'loadtest' so outgoing mail is captured by Mailpit

# --- helpers -----------------------------------------------------------------

function Get-GpuVendor {
    # Fastest check: nvidia-smi is available if NVIDIA drivers are installed
    if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
        return "nvidia"
    }
    # Fallback: query WMI for video controllers
    try {
        $gpuControllers = Get-WmiObject Win32_VideoController |
            Where-Object { $_.Name -notmatch "Virtual|Remote|Basic" -and $_.AdapterCompatibility -notmatch "Microsoft" }
        foreach ($gpuController in $gpuControllers) {
            $gpuInfo = "$($gpuController.Name) $($gpuController.AdapterCompatibility)"
            if ($gpuInfo -match "NVIDIA")                          { return "nvidia" }
            if ($gpuInfo -match "AMD|Radeon|Advanced Micro Devices") { return "amd" }
            if ($gpuInfo -match "Intel")                           { return "intel" }
        }
    } catch {}
    return "none"
}

function Get-OllamaComposeFiles {
    param([string]$RootPath)

    $gpuVendor = Get-GpuVendor
    $composeFiles = @("`"$RootPath\docker-compose.yml`"")

    switch ($gpuVendor) {
        "nvidia" {
            Write-Host "GPU detected: NVIDIA - enabling GPU acceleration for Ollama."
            $composeFiles += "`"$RootPath\docker-compose.gpu-nvidia.yml`""
        }
        "amd" {
            Write-Host "GPU detected: AMD - GPU acceleration requires ROCm (Linux only). Running Ollama on CPU."
        }
        "intel" {
            Write-Host "GPU detected: Intel - GPU acceleration for Ollama in Docker is not supported on Windows. Running on CPU."
        }
        default {
            Write-Host "No dedicated GPU detected - Ollama will run on CPU."
        }
    }

    if ($script:IncludeMailpit) {
        Write-Host "Mailpit override enabled - outgoing mail is captured locally (no real SMTP)."
        $composeFiles += "`"$RootPath\docker-compose.mailpit.yml`""
    }

    return ($composeFiles -join " -f ")
}

function Test-DockerRunning {
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker is not running. Start Docker Desktop and retry."
        return $false
    }
    return $true
}

function Show-Usage {
    Write-Host ""
    Write-Host "Usage:  ./dev.bat <command> [--keep-db]"
    Write-Host ""
    Write-Host "Commands:"
    Write-Host "  start    Start all containers (auto-detects if rebuild is needed)"
    Write-Host "  stop     Stop all containers"
    Write-Host "  restart  Stop backend, then start backend"
    Write-Host "  rebuild  Force full rebuild - also deletes the PostgreSQL volume (fresh DB)"
    Write-Host "           Use --keep-db to skip the volume deletion and keep existing data"
    Write-Host "  test     Run the backend test suite in a Maven container (no local Maven needed)"
    Write-Host "           Integration tests spin up PostgreSQL via Testcontainers (Docker required)"
    Write-Host "           Optional filter: ./dev.bat test CartFlowIntegrationTest"
    Write-Host "  loadtest Rebuild (fresh DB) + seed load-test data + run the k6 load test"
    Write-Host "           DESTRUCTIVE: deletes the database. Asks for confirmation (skip with --yes)"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  --keep-db   Keep PostgreSQL data (combine with stop/restart/rebuild)"
    Write-Host "  --yes       Skip the loadtest confirmation prompt"
    Write-Host ""
}

function Read-CommandInteractive {
    Show-Usage
    $inputCommand = (Read-Host "Enter command (start/stop/restart/rebuild/test/loadtest)").Trim().ToLower()

    if ($inputCommand -notin @("start", "stop", "restart", "rebuild", "test", "loadtest")) {
        Write-Host "ERROR: Unknown command '$inputCommand'."
        exit 1
    }

    $keepDbAnswer = (Read-Host "Keep PostgreSQL running? [y/N]").Trim().ToLower()
    if ($keepDbAnswer -in @("y", "j")) {
        $script:KeepDb = $true
    }

    return $inputCommand
}

function Get-BackendImageCreated {
    $imageId = docker inspect webshop-backend --format "{{.Image}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $imageId) { return $null }

    $createdString = docker inspect $imageId --format "{{.Created}}" 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $createdString) { return $null }

    try {
        return [datetime]::Parse(
            $createdString,
            $null,
            [System.Globalization.DateTimeStyles]::RoundtripKind
        )
    } catch {
        return $null
    }
}

function Test-RebuildNeeded {
    $imageCreated = Get-BackendImageCreated
    if ($null -eq $imageCreated) { return $false }

    $trackedPaths = @(
        (Join-Path $Root "src"),
        (Join-Path $Root "pom.xml"),
        (Join-Path $Root "Dockerfile")
    )

    foreach ($trackedPath in $trackedPaths) {
        if (-not (Test-Path $trackedPath)) { continue }

        $pathItem = Get-Item $trackedPath
        $filesToCheck = if ($pathItem.PSIsContainer) {
            Get-ChildItem -Path $trackedPath -Recurse -File
        } else {
            @($pathItem)
        }

        foreach ($fileItem in $filesToCheck) {
            if ($fileItem.LastWriteTimeUtc -gt $imageCreated) {
                $relativePath = $fileItem.FullName.Substring($Root.Length).TrimStart('\', '/')
                Write-Host "Changed since last build: $relativePath"
                return $true
            }
        }
    }

    return $false
}

function Wait-ForBackend {
    Write-Host "Waiting for backend to be ready..." -NoNewline
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $response = Invoke-WebRequest -Uri $HealthUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                Write-Host " ready."
                return $true
            }
        } catch {
            # not ready yet
        }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 2
    }
    Write-Host " timed out!"
    Write-Host "Check container logs: docker logs webshop-backend"
    return $false
}

function Show-SeedHint {
    Write-Host ""
    Write-Host "--- Seed data (optional, run once) ---"
    Write-Host "  Windows (PowerShell):"
    Write-Host "    Get-Content src/main/resources/db/dev-seed.sql | docker exec -i webshop-postgres psql -U webshop -d webshop"
    Write-Host "  Linux / macOS (bash):"
    Write-Host "    docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql"
    Write-Host ""
    Write-Host "--- Shoppi KI-Assistent (Ollama model, run once) ---"
    Write-Host "  docker exec webshop-ollama ollama pull gemma4:e4b"
    Write-Host "  (This downloads ~10 GB on first run. The model is cached in the ollama_data volume.)"
    Write-Host ""
    Write-Host "--- Monitoring & Alerting ---"
    Write-Host "  Grafana:    http://localhost:3001  (admin / admin)"
    Write-Host "  Dashboards: JVM Overview, HTTP Requests, Spring Boot Overview"
    Write-Host "  Prometheus scrapes backend:8081 internally - not reachable from outside Docker."
    Write-Host ""
}

# --- stop --------------------------------------------------------------------

function Stop-Backend {
    if ($KeepDb) {
        Write-Host "Stopping backend container (keeping PostgreSQL running)..."
        docker compose -f "$Root\docker-compose.yml" stop backend
    } else {
        Write-Host "Stopping all containers..."
        docker compose -f "$Root\docker-compose.yml" down
    }
}

# --- start -------------------------------------------------------------------

function Start-Backend {
    if (-not (Test-DockerRunning)) { return }

    if (Test-RebuildNeeded) {
        Write-Host "Source changes detected since last build - rebuilding automatically."
        Write-Host "-------------------------------------------------------------------------------"
        Rebuild-Backend
        return
    }

    Write-Host ""
    Write-Host "Building and starting backend..."
    Write-Host "(First run: Maven downloads dependencies inside the container - may take a few minutes)"
    Write-Host "-------------------------------------------------------------------------------"

    $composeFiles = Get-OllamaComposeFiles -RootPath $Root

    if ($KeepDb) {
        Invoke-Expression "docker compose -f $composeFiles up -d --build backend"
    } else {
        Invoke-Expression "docker compose -f $composeFiles up -d --build"
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: docker compose up failed."
        return
    }

    if (Wait-ForBackend) {
        Write-Host "-------------------------------------------------------------------------------"
        Write-Host "Backend is up at     http://localhost:$Port"
        Write-Host "Swagger UI:          http://localhost:$Port/swagger-ui/index.html"
        Show-SeedHint
        Write-Host "Run './dev.bat stop' to shut down."
    }
}

# --- rebuild -----------------------------------------------------------------

function Rebuild-Backend {
    Write-Host "Forcing full rebuild (no layer cache)..."
    if (-not (Test-DockerRunning)) { return }

    $composeFiles = Get-OllamaComposeFiles -RootPath $Root

    if ($KeepDb) {
        Write-Host "Keeping PostgreSQL data (--keep-db)."
        Invoke-Expression "docker compose -f $composeFiles up -d --build --force-recreate backend"
    } else {
        Invoke-Expression "docker compose -f $composeFiles down"
        $postgresVolume = docker volume ls --format "{{.Name}}" | Where-Object { $_ -match "postgres_data" }
        if ($postgresVolume) {
            Write-Host "Removing PostgreSQL volume ($postgresVolume) for a clean database..."
            docker volume rm $postgresVolume
        }
        Invoke-Expression "docker compose -f $composeFiles up -d --build --force-recreate"
    }

    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: docker compose rebuild failed."
        return
    }

    if (Wait-ForBackend) {
        Write-Host "-------------------------------------------------------------------------------"
        Write-Host "Backend is up at     http://localhost:$Port"
        Write-Host "Swagger UI:          http://localhost:$Port/swagger-ui/index.html"
        Show-SeedHint
        Write-Host "Run './dev.bat stop' to shut down."
    }
}

# --- test --------------------------------------------------------------------

function Invoke-Tests {
    if (-not (Test-DockerRunning)) { return }

    Write-Host "Running backend tests in a Maven container (no local Java/Maven needed)..."
    Write-Host "Integration tests start a PostgreSQL container via Testcontainers using the host Docker daemon."
    if ($TestFilter) {
        Write-Host "Filter: -Dtest=$TestFilter"
    }
    Write-Host "(First run downloads dependencies into the cached volume and pulls the postgres image.)"
    Write-Host "-------------------------------------------------------------------------------"

    # Built as an argument array and splatted, so the '!' in the project path is passed
    # literally to docker.exe (no shell re-parsing). The mounted Docker socket lets
    # Testcontainers start sibling containers; TESTCONTAINERS_HOST_OVERRIDE makes the
    # Maven container reach the mapped database port on the host.
    $dockerArguments = @(
        "run", "--rm",
        "-v", "${Root}:/app",
        "-v", "webshop-mvn-repo:/root/.m2",
        "-v", "//var/run/docker.sock:/var/run/docker.sock",
        "-e", "TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal",
        "-w", "/app",
        "maven:3.9-eclipse-temurin-21-alpine",
        "mvn", "-B", "test"
    )
    if ($TestFilter) {
        $dockerArguments += "-Dtest=$TestFilter"
    }

    & docker @dockerArguments

    Write-Host "-------------------------------------------------------------------------------"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Tests failed (exit code $LASTEXITCODE)."
        return
    }
    Write-Host "All tests passed."
}

# --- loadtest ----------------------------------------------------------------

function Import-SeedFile {
    param([string]$RelativePath, [string]$Label)
    $seedPath = Join-Path $Root $RelativePath
    if (-not (Test-Path $seedPath)) {
        Write-Host "ERROR: Seed file not found: $seedPath"
        return
    }
    Write-Host "Seeding $Label ..."
    Get-Content $seedPath | docker exec -i webshop-postgres psql -U webshop -d webshop
}

function Invoke-K6Script {
    param([string]$ScriptName, [bool]$PlaceOrders = $false)

    Write-Host "-------------------------------------------------------------------------------"
    Write-Host "Running k6: $ScriptName  (real orders: $(if ($PlaceOrders) { 'yes' } else { 'no' }))"
    Write-Host "Live k6 output follows. Watch Grafana http://localhost:3001, Mailpit http://localhost:8025"
    Write-Host "-------------------------------------------------------------------------------"

    # Foreground docker run → k6's progress and final report stream straight to this console.
    $dockerArguments = @(
        "run", "--rm",
        "-e", "BASE_URL=http://host.docker.internal:$Port",
        "-e", "LOGIN_USER_PREFIX=loaduser",
        "-e", "LOGIN_USER_COUNT=1000"
    )
    if ($PlaceOrders) {
        $dockerArguments += @("-e", "PLACE_ORDERS=true")
    }
    $dockerArguments += @("-v", "${Root}\loadtest:/scripts", "grafana/k6", "run", "/scripts/$ScriptName")

    & docker @dockerArguments
}

function Invoke-LoadTest {
    if (-not (Test-DockerRunning)) { return }

    if (-not $Yes) {
        Write-Host "WARNING: 'loadtest' rebuilds the stack and DELETES the PostgreSQL database,"
        Write-Host "         then seeds load-test data."
        $answer = (Read-Host "Continue? [y/N]").Trim().ToLower()
        if ($answer -notin @("y", "j")) {
            Write-Host "Aborted."
            return
        }
    }

    # Fresh database + Mailpit override (captures all outgoing mail locally; no real SMTP).
    $script:KeepDb = $false
    $script:IncludeMailpit = $true
    Rebuild-Backend
    if (-not (Wait-ForBackend)) {
        Write-Host "ERROR: Backend did not become ready - aborting load test."
        return
    }

    Import-SeedFile "src\main\resources\db\dev-seed.sql" "base data (dev-seed)"
    Import-SeedFile "src\main\resources\db\loadtest-seed.sql" "load-test data"

    Write-Host ""
    Write-Host "Backend is ready and seeded."
    $choice = (Read-Host "Lasttest jetzt starten? [j]a / [n]ein / [a] ja inkl. Bestellungen (max. Auslastung)").Trim().ToLower()

    $ordersIncluded = $false
    $readTestRan = $false
    if ($choice -in @("a", "alles", "b", "ja-inkl")) {
        Invoke-K6Script -ScriptName "load-test.js" -PlaceOrders $true
        $ordersIncluded = $true
        $readTestRan = $true
    } elseif ($choice -in @("j", "ja", "y", "")) {
        Invoke-K6Script -ScriptName "load-test.js" -PlaceOrders $false
        $readTestRan = $true
    } else {
        Write-Host "Lasttest übersprungen."
    }

    # Offer a focused order-only run afterwards (only if a read-only test ran).
    if ($readTestRan -and -not $ordersIncluded) {
        $orderChoice = (Read-Host "Jetzt noch ein reines Bestell-Lastszenario ausführen? [j/N]").Trim().ToLower()
        if ($orderChoice -in @("j", "ja", "y")) {
            Invoke-K6Script -ScriptName "order-load-test.js" -PlaceOrders $false
        }
    }

    Write-Host "-------------------------------------------------------------------------------"
    Write-Host "Done. The stack is still running for inspection:"
    Write-Host "  Grafana: http://localhost:3001  (admin / admin)"
    Write-Host "  Mailpit: http://localhost:8025  (captured emails)"
    Write-Host "Run './dev.bat stop' to shut down."
}

# --- dispatch ----------------------------------------------------------------

if (-not $Command) {
    $Command = Read-CommandInteractive
}

if ($Command -notin @("start", "stop", "restart", "rebuild", "test", "loadtest")) {
    Write-Host "ERROR: Unknown command '$Command'. Run './dev.bat' without arguments for help."
    exit 1
}

switch ($Command) {
    "start"    { Start-Backend }
    "stop"     { Stop-Backend }
    "restart"  { Stop-Backend; Start-Sleep -Seconds 1; Start-Backend }
    "rebuild"  { Rebuild-Backend }
    "test"     { Invoke-Tests }
    "loadtest" { Invoke-LoadTest }
}
