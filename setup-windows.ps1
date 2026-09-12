$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$BookingPortal = Join-Path $ProjectRoot "Rocket-Booking-Portal"
$StaffFrontend = Join-Path $ProjectRoot "Rocket-Staff-Portal\staff-portal-frontend"

Write-Host "Creating the Booking Portal Python environment..."
Set-Location $BookingPortal
py -3 -m venv .venv
& ".\.venv\Scripts\python.exe" -m pip install --upgrade pip
& ".\.venv\Scripts\python.exe" -m pip install -r requirements.txt

Write-Host "Installing Staff Portal frontend packages..."
Set-Location $StaffFrontend
npm ci

Write-Host ""
Write-Host "Setup complete. Copy the live data files into the data folder, then run .\start-windows.ps1"
