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

function Invoke-Checked {
    param(
        [string]$Description,
        [scriptblock]$Command
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Stop-RocketServers {
    $rocketPorts = 8000, 8001, 8080

    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $rocketPorts } |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object {
            Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue
        }

    Get-ChildItem $PidRoot -Filter "*.pid" -ErrorAction SilentlyContinue | ForEach-Object {
        $savedPid = Get-Content $_.FullName -ErrorAction SilentlyContinue
        if ($savedPid) {
            Stop-Process -Id ([int]$savedPid) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
    }

    Start-Sleep -Seconds 2

    $remainingPorts = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $rocketPorts }
    if ($remainingPorts) {
        throw "One or more Rocket Server ports could not be stopped."
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
    Invoke-Checked "Python dependency installation" {
        & $venvPython -m pip install --disable-pip-version-check -r (Join-Path $BookingRoot "requirements.txt")
    }

    Push-Location $FrontendRoot
    try {
        Invoke-Checked "Frontend dependency installation" { & $npm ci }
        Invoke-Checked "Frontend lint" { & $npm run lint }
        Invoke-Checked "Frontend build" { & $npm run build }
    }
    finally {
        Pop-Location
    }

    Push-Location $BackendRoot
    try {
        Invoke-Checked "Spring build" {
            & (Join-Path $BackendRoot "mvnw.cmd") clean package -DskipTests
        }
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
    $env:ROCKET_STAFF_FRONTEND_URL = "https://rocketpubserver.co.uk/staff"
    $env:MICROSOFT_REDIRECT_URI = "https://rocketpubserver.co.uk/api/email/microsoft/callback"

    Start-HiddenProcess "flask" $venvPython @("run.py") $BookingRoot
    Start-HiddenProcess "spring" $java @("-jar", $jar.FullName) $BackendRoot
    Start-HiddenProcess "frontend" $npm @("run", "start") $FrontendRoot

    Write-Host "Rocket Server is running in the background."
    Write-Host "Customer: https://rocketpubserver.co.uk/"
    Write-Host "Booking:  https://rocketpubserver.co.uk/booking"
    Write-Host "Staff:    https://rocketpubserver.co.uk/staff"
    Write-Host "Logs:     $LogRoot"
}

Push-Location $ProjectRoot
try {
    $hasUpdate = $Force

    if (-not $Force) {
        Invoke-Checked "Git fetch" { & git fetch origin }
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
        Invoke-Checked "Git update" { & git merge --ff-only $remoteRef }
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
