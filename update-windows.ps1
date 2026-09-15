param(
    [switch]$Force,
    [switch]$Rebuild,
    [switch]$Startup
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BookingRoot = Join-Path $ProjectRoot "Rocket-Booking-Portal"
$FrontendRoot = Join-Path $ProjectRoot "Rocket-Portal"
$BackendRoot = Join-Path $ProjectRoot "Rocket-API"
$RuntimeRoot = Join-Path $ProjectRoot "runtime"
$LogRoot = Join-Path $RuntimeRoot "logs"
$PidRoot = Join-Path $RuntimeRoot "pids"
$RocketPorts = 8000, 8001, 8080

New-Item -ItemType Directory -Force $LogRoot, $PidRoot | Out-Null

function Write-Stage {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Message"
}

function Invoke-Checked {
    param([string]$Description, [scriptblock]$Command)
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Description failed with exit code $LASTEXITCODE."
    }
}

function Get-RocketListeners {
    @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
        Where-Object { $_.LocalPort -in $RocketPorts })
}

function Test-RocketServersRunning {
    $listeningPorts = @(Get-RocketListeners | Select-Object -ExpandProperty LocalPort -Unique)
    return (@($RocketPorts | Where-Object { $_ -notin $listeningPorts }).Count -eq 0)
}

function Stop-RocketServers {
    Write-Stage "Stopping Rocket Server processes..."

    $savedProcessIds = @(Get-ChildItem $PidRoot -Filter "*.pid" -ErrorAction SilentlyContinue |
        ForEach-Object { Get-Content $_.FullName -ErrorAction SilentlyContinue } |
        Where-Object { $_ } |
        ForEach-Object { [int]$_ })

    for ($attempt = 1; $attempt -le 6; $attempt++) {
        $listeningProcessIds = @(Get-RocketListeners |
            Select-Object -ExpandProperty OwningProcess -Unique)
        $processIds = @($savedProcessIds + $listeningProcessIds | Select-Object -Unique)

        foreach ($processId in $processIds) {
            # taskkill /T closes the complete npm, Java or Python process tree.
            # Stopping only the listening child can leave npm or Next holding
            # files such as next-swc.win32-x64-msvc.node open.
            & taskkill.exe /PID $processId /T /F *> $null
            if ($LASTEXITCODE -ne 0) {
                Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            }
        }

        # Also remove project processes that have stopped listening but still
        # hold build files. Limit this to commands launched from this project.
        $projectProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                $_.ProcessId -ne $PID -and
                $_.Name -match '^(node|java|python|pythonw|npm|cmd)\.exe$' -and
                $_.CommandLine -and
                $_.CommandLine -like "*$ProjectRoot*"
            })

        foreach ($process in $projectProcesses) {
            & taskkill.exe /PID $process.ProcessId /T /F *> $null
        }

        Start-Sleep -Seconds 1
        if (@(Get-RocketListeners).Count -eq 0 -and $projectProcesses.Count -eq 0) {
            break
        }
    }

    Get-ChildItem $PidRoot -Filter "*.pid" -ErrorAction SilentlyContinue |
        Remove-Item -Force -ErrorAction SilentlyContinue

    $remaining = @(Get-RocketListeners)
    if ($remaining.Count -gt 0) {
        $details = $remaining | ForEach-Object {
            $process = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
            "port $($_.LocalPort): $($process.ProcessName) (PID $($_.OwningProcess))"
        }
        throw "Rocket Server could not release $($details -join ', '). Restart Windows, then run the update again."
    }

    Write-Stage "Rocket Server processes stopped."
}

function Start-HiddenProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )
    $process = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory -WindowStyle Hidden `
        -RedirectStandardOutput (Join-Path $LogRoot "$Name.log") `
        -RedirectStandardError (Join-Path $LogRoot "$Name-error.log") -PassThru
    Set-Content (Join-Path $PidRoot "$Name.pid") $process.Id
}

function Install-PythonDependencies {
    Write-Stage "Checking Python dependencies..."
    $python = (Get-Command python.exe -ErrorAction Stop).Source
    $venvPython = Join-Path $BookingRoot ".venv\Scripts\python.exe"
    if (-not (Test-Path $venvPython)) {
        Invoke-Checked "Python virtual environment creation" {
            & $python -m venv (Join-Path $BookingRoot ".venv")
        }
    }
    Invoke-Checked "Python dependency installation" {
        & $venvPython -m pip install --disable-pip-version-check -r (Join-Path $BookingRoot "requirements.txt")
    }
    Write-Stage "Python dependencies are ready."
}

function Install-FrontendDependencies {
    Write-Stage "Installing frontend dependencies..."
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    Push-Location $FrontendRoot
    try {
        Invoke-Checked "Frontend dependency installation" { & $npm ci }
    }
    finally {
        Pop-Location
    }
    Write-Stage "Frontend dependencies are ready."
}

function Build-Frontend {
    Write-Stage "Checking frontend code..."
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    Push-Location $FrontendRoot
    try {
        Invoke-Checked "Frontend lint" { & $npm run lint }
        Write-Stage "Frontend code check completed."
        Write-Stage "Building the Rocket Portal..."
        Invoke-Checked "Frontend build" { & $npm run build }
    }
    finally {
        Pop-Location
    }
    Write-Stage "Rocket Portal build completed."
}

function Build-Backend {
    Write-Stage "Building the Rocket API..."
    Push-Location $BackendRoot
    try {
        Invoke-Checked "Spring build" {
            & (Join-Path $BackendRoot "mvnw.cmd") package -DskipTests
        }
    }
    finally {
        Pop-Location
    }
    Write-Stage "Rocket API build completed."
}

function Get-SpringJar {
    Get-ChildItem (Join-Path $BackendRoot "target") -Filter "*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*.original" } |
        Select-Object -First 1
}

function Start-RocketServers {
    Write-Stage "Starting Rocket Server in the background..."
    $venvPython = Join-Path $BookingRoot ".venv\Scripts\python.exe"
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $jar = Get-SpringJar

    if (-not (Test-Path $venvPython)) {
        throw "Python dependencies are missing. Run setup-windows.ps1 again."
    }
    if (-not $jar) {
        throw "The Spring application is missing. Run setup-windows.ps1 again."
    }
    if (-not (Test-Path (Join-Path $FrontendRoot ".next\BUILD_ID"))) {
        throw "The web portal build is missing. Run setup-windows.ps1 again."
    }

    $env:ROCKET_FLASK_PORT = "8001"
    $env:ROCKET_STAFF_FRONTEND_URL = "https://rocketpubserver.co.uk/staff"
    $env:MICROSOFT_REDIRECT_URI = "https://rocketpubserver.co.uk/api/email/microsoft/callback"

    Start-HiddenProcess "flask" $venvPython @("run.py") $BookingRoot
    Start-HiddenProcess "spring" $java @("-jar", $jar.FullName) $BackendRoot
    Start-HiddenProcess "frontend" $npm @("run", "start") $FrontendRoot

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        if (Test-RocketServersRunning) {
            break
        }
        Start-Sleep -Seconds 2
    }

    if (-not (Test-RocketServersRunning)) {
        $listeningPorts = @(Get-RocketListeners | Select-Object -ExpandProperty LocalPort -Unique)
        $missingPorts = @($RocketPorts | Where-Object { $_ -notin $listeningPorts })
        throw "Rocket Server did not start on port(s) $($missingPorts -join ', '). Check $LogRoot."
    }

    Write-Stage "All Rocket Server services are running."
    Write-Host "Rocket Server is running in the background."
    Write-Host "Customer: https://rocketpubserver.co.uk/"
    Write-Host "Booking:  https://rocketpubserver.co.uk/booking"
    Write-Host "Staff:    https://rocketpubserver.co.uk/staff"
    Write-Host "Logs:     $LogRoot"
}

Push-Location $ProjectRoot
try {
    Write-Stage "Checking Git for updates..."
    $changedFiles = @()
    $hasUpdate = $false

    if (-not $Startup -and -not $Force) {
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
        if ($hasUpdate) {
            $changedFiles = @(& git diff --name-only $localCommit $remoteCommit)
            Write-Stage "A new Git update was found."
        }
        else {
            Write-Stage "No new Git update was found."
        }
    }

    if (-not $hasUpdate -and -not $Force -and -not $Startup) {
        if (Test-RocketServersRunning) {
            Write-Host "Rocket Server is already up to date and running."
            exit 0
        }
        Write-Host "Rocket Server is up to date. Restarting missing services."
    }

    if ($hasUpdate) {
        $localChanges = & git status --porcelain
        if ($localChanges) {
            throw "Local project files have changes. Update stopped so they are not overwritten."
        }
    }

    Stop-RocketServers
    if ($hasUpdate) {
        Write-Stage "Downloading and applying the Git update..."
        Invoke-Checked "Git update" { & git merge --ff-only $remoteRef }
        Write-Stage "Git update completed."
    }

    $venvPython = Join-Path $BookingRoot ".venv\Scripts\python.exe"
    $frontendBuild = Join-Path $FrontendRoot ".next\BUILD_ID"
    $nodeModules = Join-Path $FrontendRoot "node_modules"
    $jar = Get-SpringJar

    $pythonRequirementsChanged = $Rebuild -or
        ($changedFiles -contains "Rocket-Booking-Portal/requirements.txt") -or
        -not (Test-Path $venvPython)
    $frontendChanged = $Rebuild -or
        @($changedFiles | Where-Object { $_ -like "Rocket-Portal/*" }).Count -gt 0 -or
        -not (Test-Path $frontendBuild)
    $frontendDependenciesChanged = $Rebuild -or
        ($changedFiles -contains "Rocket-Portal/package.json") -or
        ($changedFiles -contains "Rocket-Portal/package-lock.json") -or
        -not (Test-Path $nodeModules)
    $backendChanged = $Rebuild -or
        @($changedFiles | Where-Object { $_ -like "Rocket-API/*" }).Count -gt 0 -or
        -not $jar

    if ($pythonRequirementsChanged) { Install-PythonDependencies }
    else { Write-Stage "Python dependencies have not changed." }
    if ($frontendDependenciesChanged) { Install-FrontendDependencies }
    else { Write-Stage "Frontend dependencies have not changed." }
    if ($frontendChanged) { Build-Frontend }
    else { Write-Stage "Rocket Portal build is already current." }
    if ($backendChanged) { Build-Backend }
    else { Write-Stage "Rocket API build is already current." }

    Start-RocketServers
}
catch {
    Write-Error $_
    exit 1
}
finally {
    Pop-Location
}
