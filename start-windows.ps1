$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BookingPortal = Join-Path $ProjectRoot "Rocket-Booking-Portal"
$StaffFrontend = Join-Path $ProjectRoot "Rocket-Staff-Portal\staff-portal-frontend"
$StaffBackend = Join-Path $ProjectRoot "Rocket-Staff-Portal\staff-portal-backend"
$Python = Join-Path $BookingPortal ".venv\Scripts\python.exe"

if (-not (Test-Path $Python)) {
    throw "Python packages are not installed. Run .\setup-windows.ps1 first."
}

if (-not (Test-Path (Join-Path $ProjectRoot "data\rocket_integration.db"))) {
    throw "data\rocket_integration.db is missing. Copy the live database into the data folder first."
}

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$StaffBackend'; .\mvnw.cmd spring-boot:run"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$StaffFrontend'; npm run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$BookingPortal'; & '$Python' run.py"

Write-Host "Rocket Server is starting in three PowerShell windows."
Write-Host "Customer and Booking portals: http://localhost:8000"
Write-Host "Staff Portal: http://localhost:3000"
