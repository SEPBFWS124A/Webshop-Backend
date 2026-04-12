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
    Write-Host "  docker exec -i webshop-postgres psql -U webshop -d webshop < src/main/resources/db/dev-seed.sql"
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

    if ($KeepDb) {
        # PostgreSQL is already running - only start/rebuild the backend service
        docker compose -f "$Root\docker-compose.yml" up -d --build backend
    } else {
        docker compose -f "$Root\docker-compose.yml" up -d --build
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

    if ($KeepDb) {
        docker compose -f "$Root\docker-compose.yml" up -d --build --force-recreate backend
    } else {
        docker compose -f "$Root\docker-compose.yml" down
        docker compose -f "$Root\docker-compose.yml" up -d --build --force-recreate
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
