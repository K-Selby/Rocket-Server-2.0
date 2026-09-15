@echo off
setlocal EnableExtensions EnableDelayedExpansion

net session >nul 2>&1
if not "%errorlevel%"=="0" (
    powershell.exe -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

set "PROJECT=%USERPROFILE%\Documents\Rocket-Server-2.0"
set "BOOKING=%PROJECT%\Rocket-Booking-Portal"
set "FRONTEND=%PROJECT%\Rocket-Portal"
set "BACKEND=%PROJECT%\Rocket-API"
set "RUNTIME=%PROJECT%\runtime"
set "LOGS=%RUNTIME%\logs"
set "REMOTE_FILE=%TEMP%\Rocket-Server-Update-Remote.bat"
set "CHANGES=%TEMP%\Rocket-Server-Changes.txt"
set "FAILED_STAGE=Unknown stage"

if not exist "%PROJECT%" (
    echo Rocket Server was not found at:
    echo %PROJECT%
    pause
    exit /b 1
)

if not exist "%LOGS%" mkdir "%LOGS%"

call :stage "Checking required programs"
where git.exe
if errorlevel 1 (
    set "FAILED_STAGE=Git is not installed or is not available in PATH"
    goto :fail
)
where python.exe
if errorlevel 1 (
    set "FAILED_STAGE=Python is not installed or is not available in PATH"
    goto :fail
)
where npm.cmd
if errorlevel 1 (
    set "FAILED_STAGE=Node.js and npm are not installed or are not available in PATH"
    goto :fail
)
where java.exe
if errorlevel 1 (
    set "FAILED_STAGE=Java is not installed or is not available in PATH"
    goto :fail
)

cd /d "%PROJECT%"

call :stage "Checking Git for updates"
set "FAILED_STAGE=Git fetch"
git fetch origin
if errorlevel 1 goto :fail

for /f "delims=" %%B in ('git branch --show-current') do set "BRANCH=%%B"
if not defined BRANCH (
    set "FAILED_STAGE=Reading the current Git branch"
    goto :fail
)

set "REMOTE=origin/%BRANCH%"

for /f "delims=" %%C in ('git rev-parse HEAD') do set "LOCAL_COMMIT=%%C"
for /f "delims=" %%C in ('git rev-parse "%REMOTE%"') do set "REMOTE_COMMIT=%%C"

if not defined LOCAL_COMMIT (
    set "FAILED_STAGE=Reading the local Git version"
    goto :fail
)

if not defined REMOTE_COMMIT (
    set "FAILED_STAGE=Reading the remote Git version"
    goto :fail
)

if "%LOCAL_COMMIT%"=="%REMOTE_COMMIT%" goto :no_git_update

call :stage "A new Git update was found"

set "FAILED_STAGE=Checking for an updater update"
git show "%REMOTE%:Rocket-Server-Update.bat" > "%REMOTE_FILE%" 2>nul
if errorlevel 1 goto :fail

fc /b "%~f0" "%REMOTE_FILE%" >nul 2>&1
if errorlevel 1 (
    set "REPLACE_HELPER=%TEMP%\Rocket-Replace-Updater-%RANDOM%.cmd"
    > "!REPLACE_HELPER!" echo @echo off
    >> "!REPLACE_HELPER!" echo timeout /t 4 /nobreak ^>nul
    >> "!REPLACE_HELPER!" echo copy /y "%REMOTE_FILE%" "%~f0" ^>nul
    >> "!REPLACE_HELPER!" echo del /q "%REMOTE_FILE%" ^>nul 2^>^&1
    >> "!REPLACE_HELPER!" echo del /q "%%~f0" ^>nul 2^>^&1
    start "" /min cmd.exe /c "!REPLACE_HELPER!"
    echo.
    echo A new updater has been downloaded.
    echo This window will now close safely.
    echo Wait 10 seconds and then run the batch file again.
    timeout /t 3 /nobreak >nul
    exit /b 20
)

del /q "%REMOTE_FILE%" >nul 2>&1

git status --porcelain > "%TEMP%\Rocket-Local-Changes.txt"
for %%A in ("%TEMP%\Rocket-Local-Changes.txt") do if %%~zA GTR 0 (
    set "FAILED_STAGE=Local project files have changes"
    echo Local project files have changes. The update stopped to protect them.
    goto :fail
)

git diff --name-only "%LOCAL_COMMIT%" "%REMOTE_COMMIT%" > "%CHANGES%"

set "BOOKING_CHANGED=0"
set "PYTHON_CHANGED=0"
set "FRONTEND_CHANGED=0"
set "FRONTEND_PACKAGES_CHANGED=0"
set "BACKEND_CHANGED=0"

findstr /B /C:"Rocket-Booking-Portal/" "%CHANGES%" >nul && set "BOOKING_CHANGED=1"
findstr /X /C:"Rocket-Booking-Portal/requirements.txt" "%CHANGES%" >nul && set "PYTHON_CHANGED=1"
findstr /B /C:"Rocket-Portal/" "%CHANGES%" >nul && set "FRONTEND_CHANGED=1"
findstr /X /C:"Rocket-Portal/package.json" /C:"Rocket-Portal/package-lock.json" "%CHANGES%" >nul && set "FRONTEND_PACKAGES_CHANGED=1"
findstr /B /C:"Rocket-API/" "%CHANGES%" >nul && set "BACKEND_CHANGED=1"

goto :perform_update

:no_git_update
call :stage "No new Git update was found"
call :servers_running
if "!SERVERS_RUNNING!"=="1" (
    call :stage "Rocket Server is already current and running"
    goto :success
)

set "BOOKING_CHANGED=0"
set "PYTHON_CHANGED=0"
set "FRONTEND_CHANGED=0"
set "FRONTEND_PACKAGES_CHANGED=0"
set "BACKEND_CHANGED=0"

:perform_update
call :stop_servers
if errorlevel 1 goto :fail

if not "%LOCAL_COMMIT%"=="%REMOTE_COMMIT%" (
    call :stage "Downloading and applying the Git update"
    set "FAILED_STAGE=Applying the Git update"
    git merge --ff-only "%REMOTE%"
    if errorlevel 1 goto :fail
    call :stage "Git update completed"
)

if not exist "%BOOKING%\.venv\Scripts\python.exe" set "PYTHON_CHANGED=1"
if not exist "%FRONTEND%\node_modules" set "FRONTEND_PACKAGES_CHANGED=1"
if not exist "%FRONTEND%\.next\BUILD_ID" set "FRONTEND_CHANGED=1"

dir /b /a-d "%BACKEND%\target\*.jar" >nul 2>&1 || set "BACKEND_CHANGED=1"

if "!PYTHON_CHANGED!"=="1" (
    call :stage "Installing Python dependencies"
    set "FAILED_STAGE=Installing Python dependencies"

    if not exist "%BOOKING%\.venv\Scripts\python.exe" (
        python.exe -m venv "%BOOKING%\.venv"
        if errorlevel 1 goto :fail
    )

    "%BOOKING%\.venv\Scripts\python.exe" -m pip install --disable-pip-version-check -r "%BOOKING%\requirements.txt"
    if errorlevel 1 goto :fail
    call :stage "Python dependencies are ready"
) else (
    call :stage "Python dependencies have not changed"
)

if "!FRONTEND_PACKAGES_CHANGED!"=="1" (
    call :stage "Installing frontend dependencies"
    set "FAILED_STAGE=Installing frontend dependencies"
    cd /d "%FRONTEND%"
    call npm.cmd ci
    if errorlevel 1 goto :fail
    call :stage "Frontend dependencies are ready"
) else (
    call :stage "Frontend dependencies have not changed"
)

if "!FRONTEND_CHANGED!"=="1" (
    call :stage "Checking the frontend code"
    set "FAILED_STAGE=Checking the frontend code"
    cd /d "%FRONTEND%"
    call npm.cmd run lint
    if errorlevel 1 goto :fail
    call :stage "Frontend code check completed"

    call :stage "Building the Rocket Portal"
    set "FAILED_STAGE=Building the Rocket Portal"
    call npm.cmd run build
    if errorlevel 1 goto :fail
    call :stage "Rocket Portal build completed"
) else (
    call :stage "Rocket Portal build is already current"
)

if "!BACKEND_CHANGED!"=="1" (
    call :stage "Building the Rocket API"
    set "FAILED_STAGE=Building the Rocket API"
    cd /d "%BACKEND%"
    call mvnw.cmd package -DskipTests
    if errorlevel 1 goto :fail
    call :stage "Rocket API build completed"
) else (
    call :stage "Rocket API build is already current"
)

call :start_servers
if errorlevel 1 goto :fail

goto :success

:stop_servers
call :stage "Stopping all Rocket Server processes"
set "FAILED_STAGE=Stopping Rocket Server processes"

taskkill.exe /IM node.exe /T /F >nul 2>&1
taskkill.exe /IM java.exe /T /F >nul 2>&1
taskkill.exe /IM python.exe /T /F >nul 2>&1
taskkill.exe /IM pythonw.exe /T /F >nul 2>&1

for /l %%R in (1,1,15) do (
    call :count_open_ports
    if "!OPEN_PORTS!"=="0" goto :servers_stopped
    timeout /t 1 /nobreak >nul
)

echo One or more Rocket Server ports could not be stopped.
echo Restart Windows and run this updater again.
exit /b 1

:servers_stopped
if exist "%RUNTIME%\pids" del /q "%RUNTIME%\pids\*.pid" >nul 2>&1
timeout /t 2 /nobreak >nul
call :stage "All Rocket Server processes have stopped"
exit /b 0

:start_servers
call :stage "Starting Rocket Server in the background"
set "FAILED_STAGE=Starting Rocket Server"

set "SPRING_JAR="
for /f "delims=" %%J in ('dir /b /a-d "%BACKEND%\target\*.jar" 2^>nul ^| findstr /V /I ".original"') do set "SPRING_JAR=%BACKEND%\target\%%J"

if not defined SPRING_JAR (
    echo The Rocket API JAR file was not found.
    exit /b 1
)

if not exist "%FRONTEND%\.next\BUILD_ID" (
    echo The Rocket Portal production build was not found.
    exit /b 1
)

if not exist "%BOOKING%\.venv\Scripts\python.exe" (
    echo The Python environment was not found.
    exit /b 1
)

set "ROCKET_FLASK_PORT=8001"
set "ROCKET_STAFF_FRONTEND_URL=https://rocketpubserver.co.uk/staff"
set "MICROSOFT_REDIRECT_URI=https://rocketpubserver.co.uk/api/email/microsoft/callback"

start "" /b /D "%BOOKING%" "%BOOKING%\.venv\Scripts\python.exe" run.py 1>>"%LOGS%\flask.log" 2>>"%LOGS%\flask-error.log"
start "" /b /D "%BACKEND%" java.exe -jar "%SPRING_JAR%" 1>>"%LOGS%\spring.log" 2>>"%LOGS%\spring-error.log"
start "" /b /D "%FRONTEND%" cmd.exe /c npm.cmd run start 1>>"%LOGS%\frontend.log" 2>>"%LOGS%\frontend-error.log"

for /l %%W in (1,1,30) do (
    call :servers_running
    if "!SERVERS_RUNNING!"=="1" goto :servers_started

    if %%W==5 call :stage "Still waiting for the services to start"
    if %%W==10 call :stage "The services are still starting"
    if %%W==20 call :stage "Startup is taking longer than usual"

    timeout /t 2 /nobreak >nul
)

echo One or more services did not start on ports 8000, 8001 and 8080.
echo Check the logs in %LOGS%.
exit /b 1

:servers_started
call :stage "All Rocket Server services are running"
echo.
echo Customer: https://rocketpubserver.co.uk/
echo Booking:  https://rocketpubserver.co.uk/booking
echo Staff:    https://rocketpubserver.co.uk/staff
echo Logs:     %LOGS%
exit /b 0

:count_open_ports
set "OPEN_PORTS=0"
for %%Q in (8000 8001 8080) do (
    powershell.exe -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %%Q -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>&1
    if not errorlevel 1 set /a OPEN_PORTS+=1
)
exit /b 0

:servers_running
call :count_open_ports
set "SERVERS_RUNNING=0"
if "!OPEN_PORTS!"=="3" set "SERVERS_RUNNING=1"
exit /b 0

:stage
echo.
echo [%time:~0,8%] %~1
exit /b 0

:success
echo.
echo Rocket Server update completed successfully.
del /q "%CHANGES%" >nul 2>&1
del /q "%TEMP%\Rocket-Local-Changes.txt" >nul 2>&1
pause
exit /b 0

:fail
echo.
echo UPDATE FAILED: %FAILED_STAGE%
del /q "%CHANGES%" >nul 2>&1
del /q "%TEMP%\Rocket-Local-Changes.txt" >nul 2>&1
pause
exit /b 1
