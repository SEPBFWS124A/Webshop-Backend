# dev.ps1 - Start, stop, restart or rebuild the Spring Boot backend (incl. Docker/PostgreSQL)
# Usage:  dev start   | stop | restart | rebuild
#         dev stop --keep-db     (keeps the PostgreSQL container running)
#         dev restart --keep-db
#         dev rebuild --keep-db

param(
    [Parameter(Mandatory)][ValidateSet("start","stop","restart","rebuild")]
    [string]$Command,
    [switch]$KeepDb
)

$Root    = $PSScriptRoot
$LogFile = "$Root\target\dev.log"
$Port    = 8080

# --- helpers -----------------------------------------------------------------

function Get-AppPid {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -First 1
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
        Write-Host "Stopping Spring Boot (PID $appPid)..."
        Stop-Process -Id $appPid -Force
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

    # 1. Start PostgreSQL if not already up
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

    # 2. Start Spring Boot in background, redirect stdout+stderr to log
    New-Item -ItemType Directory -Path "$Root\target" -Force | Out-Null
    Write-Host "Starting Spring Boot... (log: target\dev.log)"

    $process = Start-Process -FilePath "java" `
        -ArgumentList `
            "-Dmaven.multiModuleProjectDirectory=`"$Root`"", `
            "-classpath", "`"$Root\.mvn\wrapper\maven-wrapper.jar`"", `
            "org.apache.maven.wrapper.MavenWrapperMain", `
            "spring-boot:run" `
        -WorkingDirectory $Root `
        -RedirectStandardOutput $LogFile `
        -RedirectStandardError  "$Root\target\dev-err.log" `
        -WindowStyle Hidden `
        -PassThru

    # 3. Wait until "Started" or "FAILED" appears in the merged log
    Write-Host "Waiting for startup" -NoNewline
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2

        $log = Get-Content $LogFile -ErrorAction SilentlyContinue
        $err = Get-Content "$Root\target\dev-err.log" -ErrorAction SilentlyContinue
        $combined = ($log + $err) -join "`n"

        if ($combined -match "Started WebshopApplication") {
            Write-Host "`nBackend is up at http://localhost:$Port"
            return
        }
        if ($combined -match "APPLICATION FAILED TO START") {
            Write-Host "`nStartup FAILED. Check target\dev.log and target\dev-err.log"
            return
        }
        Write-Host -NoNewline "."
    }
    Write-Host "`nTimed out - check target\dev.log"
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
