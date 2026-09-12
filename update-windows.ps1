param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BookingRoot = Join-Path $ProjectRoot "Rocket-Booking-Portal"
$FrontendRoot = Join-Path $ProjectRoot "Rocket-Staff-Portal\staff-portal-frontend"
$BackendRoot = Join-Path $ProjectRoot "Rocket-Staff-Portal\staff-portal-backend"
$RuntimeRoot = Join-Path $ProjectRoot "runtime"
$LogRoot = Join-Path $RuntimeRoot "logs"
$PidRoot = Join-Path $RuntimeRoot "pids"

New-Item -ItemType Directory -Force $LogRoot, $PidRoot | Out-Null

function Stop-RocketServers {
    Get-ChildItem $PidRoot -Filter "*.pid" -ErrorAction SilentlyContinue | ForEach-Object {
        $savedPid = Get-Content $_.FullName -ErrorAction SilentlyContinue
        if ($savedPid) {
            Stop-Process -Id ([int]$savedPid) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }
}

function Start-HiddenProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )

    $process = Start-Process `
        -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogRoot "$Name.log") `
        -RedirectStandardError (Join-Path $LogRoot "$Name-error.log") `
        -PassThru

    Set-Content (Join-Path $PidRoot "$Name.pid") $process.Id
}

function Install-And-Build {
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source

    $venvPython = Join-Path $BookingRoot ".venv\Scripts\python.exe"
    if (-not (Test-Path $venvPython)) {
        & $python -m venv (Join-Path $BookingRoot ".venv")
    }
    & $venvPython -m pip install --disable-pip-version-check -r (Join-Path $BookingRoot "requirements.txt")

    Push-Location $FrontendRoot
    try {
        & $npm ci
        & $npm run lint
        & $npm run build
    }
    finally {
        Pop-Location
    }

    Push-Location $BackendRoot
    try {
        & (Join-Path $BackendRoot "mvnw.cmd") clean package -DskipTests
    }
    finally {
        Pop-Location
    }
}

function Start-RocketServers {
    $venvPython = Join-Path $BookingRoot ".venv\Scripts\python.exe"
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $jar = Get-ChildItem (Join-Path $BackendRoot "target") -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*.original" } |
        Select-Object -First 1

    if (-not $jar) {
        throw "The Spring application was not built. Run setup-windows.ps1 again."
    }

    $env:ROCKET_FLASK_PORT = "8001"
    $env:ROCKET_STAFF_FRONTEND_URL = "http://localhost:8000/staff"

    Start-HiddenProcess "flask" $venvPython @("run.py") $BookingRoot
    Start-HiddenProcess "spring" $java @("-jar", $jar.FullName) $BackendRoot
    Start-HiddenProcess "frontend" $npm @("run", "start") $FrontendRoot

    Write-Host "Rocket Server is running in the background."
    Write-Host "Customer: http://localhost:8000/"
    Write-Host "Booking:  http://localhost:8000/booking"
    Write-Host "Staff:    http://localhost:8000/staff"
    Write-Host "Logs:     $LogRoot"
}

Push-Location $ProjectRoot
try {
    $hasUpdate = $Force

    if (-not $Force) {
        & git fetch origin
        $branch = (& git branch --show-current).Trim()
        $remoteRef = "origin/$branch"
        & git rev-parse --verify $remoteRef 2>$null | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "The remote branch $remoteRef was not found."
        }

        $localCommit = (& git rev-parse HEAD).Trim()
        $remoteCommit = (& git rev-parse $remoteRef).Trim()
        $hasUpdate = $localCommit -ne $remoteCommit
    }

    if (-not $hasUpdate) {
        Write-Host "Rocket Server is already up to date. Nothing was stopped."
        exit 0
    }

    $localChanges = & git status --porcelain
    if ($localChanges) {
        throw "Local project files have changes. Update stopped so they are not overwritten."
    }

    Stop-RocketServers

    if (-not $Force) {
        & git merge --ff-only $remoteRef
    }

    Install-And-Build
    Start-RocketServers
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Pop-Location
}

