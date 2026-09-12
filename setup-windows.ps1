$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

function Refresh-Path {
    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machinePath;$userPath"
}

function Ensure-WingetPackage {
    param(
        [string]$Command,
        [string]$PackageId,
        [string]$DisplayName
    )

    if (Get-Command $Command -ErrorAction SilentlyContinue) {
        Write-Host "$DisplayName is installed."
        return
    }

    Write-Host "Installing $DisplayName..."
    winget install --exact --id $PackageId --accept-package-agreements --accept-source-agreements
    Refresh-Path

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "$DisplayName was installed, but Windows has not refreshed PATH yet. Restart PowerShell and run this setup again."
    }
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    throw "Windows Package Manager is required. Install App Installer from Microsoft Store, then run this setup again."
}

Ensure-WingetPackage "git.exe" "Git.Git" "Git"
Ensure-WingetPackage "python.exe" "Python.Python.3.12" "Python 3.12"
Ensure-WingetPackage "node.exe" "OpenJS.NodeJS.LTS" "Node.js LTS"
Ensure-WingetPackage "java.exe" "EclipseAdoptium.Temurin.21.JDK" "Java 21"

Write-Host "Preparing Python, Node.js, and Java dependencies..."
& (Join-Path $ProjectRoot "update-windows.ps1") -Force
