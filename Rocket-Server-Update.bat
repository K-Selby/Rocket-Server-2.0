@echo off
setlocal

net session >nul 2>&1
if not "%errorlevel%"=="0" (
    powershell.exe -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "PROJECT=%USERPROFILE%\Documents\Rocket-Server-2.0"
set "LOG=%USERPROFILE%\Desktop\Rocket-Server-Update.log"

cd /d "%PROJECT%"

echo Updating Rocket Server...
echo Please wait. This window will remain open when finished.
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "& { & '%PROJECT%\update-windows.ps1' 2>&1 | Tee-Object -FilePath '%LOG%'; exit 0 }"
set "EXIT_CODE=%errorlevel%"

echo.

if not "%EXIT_CODE%"=="0" (
    echo The update encountered an error.
    echo The full output is saved at:
    echo %LOG%
) else (
    echo Rocket Server update completed successfully.
)

echo.
pause
exit /b %EXIT_CODE%
