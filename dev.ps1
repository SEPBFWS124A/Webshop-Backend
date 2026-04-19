# dev.ps1 - Start, stop, restart or rebuild the Webshop backend via Docker
# Usage:  ./dev.bat start   | stop | restart | rebuild
#         ./dev.bat stop    --keep-db   (stop backend container, keep PostgreSQL running)
#         ./dev.bat restart --keep-db
#         ./dev.bat rebuild --keep-db
#
# Requirements: Docker Desktop (no Java or Maven needed locally)
#
# How it works:
#   start   -> docker compose up -d --build
#              (first run: Maven downloads ~200 MB of dependencies inside the container)
#              Polls /api/health until the app responds, then prints the ready message.
#   stop    -> docker compose down  (or stop only backend with --keep-db)
#   restart -> stop backend + start backend
#   rebuild -> docker compose up -d --build --force-recreate

param(
    [Parameter(Mandatory)][ValidateSet("start","stop","restart","rebuild")]
    [string]$Command,
    [switch]$KeepDb
)

$Root = $PSScriptRoot
$Port = 8080
$HealthUrl = "http://localhost:$Port/api/health"

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
            Write-Host "GPU detected: NVIDIA — enabling GPU acceleration for Ollama."
            $composeFiles += "`"$RootPath\docker-compose.gpu-nvidia.yml`""
        }
        "amd" {
            Write-Host "GPU detected: AMD — GPU acceleration requires ROCm (Linux only). Running Ollama on CPU."
        }
        "intel" {
            Write-Host "GPU detected: Intel — GPU acceleration for Ollama in Docker is not supported on Windows. Running on CPU."
        }
        default {
            Write-Host "No dedicated GPU detected — Ollama will run on CPU."
        }
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
        Invoke-Expression "docker compose -f $composeFiles up -d --build --force-recreate backend"
    } else {
        Invoke-Expression "docker compose -f $composeFiles down"
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

# --- dispatch ----------------------------------------------------------------

switch ($Command) {
    "start"   { Start-Backend }
    "stop"    { Stop-Backend }
    "restart" { Stop-Backend; Start-Sleep -Seconds 1; Start-Backend }
    "rebuild" { Rebuild-Backend }
}
