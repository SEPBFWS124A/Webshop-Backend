# dev.ps1 - Start, stop, restart or rebuild the Spring Boot backend (incl. Docker/PostgreSQL)
# Usage:  dev start   | stop | restart | rebuild
#         dev stop --keep-db     (keeps the PostgreSQL container running)
#         dev restart --keep-db
#         dev rebuild --keep-db
#
# How it works:
#   start   -> docker compose up  ->  mvnw spring-boot:start  (blocks until app is ready)
#   stop    -> mvnw spring-boot:stop  (graceful JMX shutdown)  ->  docker compose down
#   restart -> stop + start
#   rebuild -> stop + delete target/ + start  (forces full recompile)
#
# mvnw is called directly from PowerShell - NOT via cmd.exe - so paths with special
# characters (e.g. ! in folder names) are handled correctly.

param(
    [Parameter(Mandatory)][ValidateSet("start","stop","restart","rebuild")]
    [string]$Command,
    [switch]$KeepDb
)

$Root = $PSScriptRoot
$Port = 8080

# mvnw.cmd v3.3.2 no longer passes -Dmaven.multiModuleProjectDirectory to the JVM,
# but Maven 3.9.x requires it. Setting it via MAVEN_OPTS is the official workaround.
# PowerShell handles the path correctly even when it contains ! characters.
$env:MAVEN_OPTS = "$($env:MAVEN_OPTS) -Dmaven.multiModuleProjectDirectory=`"$Root`"".Trim()

# --- helpers -----------------------------------------------------------------

function Get-AppPid {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -First 1
}

function Test-Prerequisites {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        Write-Host "ERROR: 'java' not found in PATH."
        Write-Host "  Install Java 21: https://adoptium.net"
        return $false
    }
    docker info 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "ERROR: Docker is not running. Start Docker Desktop and retry."
        return $false
    }
    return $true
}

function Wait-ForPostgres {
    Write-Host "Waiting for PostgreSQL to be healthy..." -NoNewline
    for ($i = 0; $i -lt 30; $i++) {
        $health = docker inspect "--format={{.State.Health.Status}}" webshop-postgres 2>$null
        if ($health -eq "healthy") { Write-Host " ready."; return $true }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 2
    }
    Write-Host " timed out!"
    return $false
}

# --- stop --------------------------------------------------------------------

function Stop-Backend {
    $appPid = Get-AppPid
    if ($appPid) {
        Write-Host "Stopping Spring Boot..."
        # Graceful shutdown via the Maven plugin (uses JMX internally)
        & "$Root\mvnw.cmd" spring-boot:stop --quiet
        if ($LASTEXITCODE -ne 0) {
            # Fallback: kill the Java process that owns port 8080
            Write-Host "JMX stop failed - killing process $appPid..."
            Stop-Process -Id $appPid -Force -ErrorAction SilentlyContinue
        }
        Write-Host "Spring Boot stopped."
    } else {
        Write-Host "Spring Boot is not running."
    }

    if (-not $KeepDb) {
        Write-Host "Stopping PostgreSQL container..."
        docker compose -f "$Root\docker-compose.yml" down
    }
}

# --- start -------------------------------------------------------------------

function Start-Backend {
    if (Get-AppPid) {
        Write-Host "Spring Boot is already running on port $Port. Use 'dev stop' first."
        return
    }

    if (-not (Test-Prerequisites)) { return }

    # Start PostgreSQL if not already running
    $containerState = docker inspect "--format={{.State.Status}}" webshop-postgres 2>$null
    if ($containerState -ne "running") {
        Write-Host "Starting PostgreSQL container..."
        docker compose -f "$Root\docker-compose.yml" up -d
    } else {
        Write-Host "PostgreSQL container already running."
    }

    if (-not (Wait-ForPostgres)) {
        Write-Host "ERROR: PostgreSQL did not become healthy. Aborting."
        return
    }

    # spring-boot:start forks Spring Boot into a background JVM, then BLOCKS here
    # until the application context is fully loaded (via JMX handshake).
    # Maven output (downloads, compilation, Spring Boot startup logs) is printed
    # directly to this terminal. Returns only when the app is ready - or fails.
    #
    # First run: Maven downloads ~200 MB of dependencies. This can take several
    # minutes. Subsequent runs use the local ~/.m2 cache and start in ~20 seconds.
    Write-Host ""
    Write-Host "Starting Spring Boot (first run downloads dependencies - may take a few minutes)..."
    Write-Host "-------------------------------------------------------------------------------"
    & "$Root\mvnw.cmd" spring-boot:start

    if ($LASTEXITCODE -eq 0) {
        Write-Host "-------------------------------------------------------------------------------"
        Write-Host "Backend is up at     http://localhost:$Port"
        Write-Host "Swagger UI:          http://localhost:$Port/swagger-ui/index.html"
        Write-Host ""
        Write-Host "Run 'dev stop' to shut down."
    } else {
        Write-Host "-------------------------------------------------------------------------------"
        Write-Host "ERROR: Spring Boot failed to start (exit code $LASTEXITCODE)."
        Write-Host "Scroll up to see the error in the Maven output above."
    }
}

# --- rebuild -----------------------------------------------------------------

function Reset-Backend {
    Stop-Backend

    Write-Host "Deleting target/ (forces full recompile)..."
    $targetPath = "$Root\target"
    if (Test-Path $targetPath) {
        Remove-Item -Recurse -Force $targetPath -ErrorAction SilentlyContinue
        if (Test-Path $targetPath) {
            Write-Host "WARNING: Could not fully delete target/ (OneDrive lock?). Continuing anyway."
        } else {
            Write-Host "target/ deleted."
        }
    }

    Start-Sleep -Seconds 1
    Start-Backend
}

# --- dispatch ----------------------------------------------------------------

switch ($Command) {
    "start"   { Start-Backend }
    "stop"    { Stop-Backend }
    "restart" { Stop-Backend; Start-Sleep -Seconds 1; Start-Backend }
    "rebuild" { Reset-Backend }
}
