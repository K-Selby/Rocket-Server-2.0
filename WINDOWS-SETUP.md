# Rocket Server 2.0 on Windows 10

Rocket Server runs as three local processes: one Flask server for the Customer
and Booking portals on port 8000, one Next.js frontend for the Staff Portal on
port 3000, and one Spring Boot API for login and staff features on port 8080.

## Install the required software

Install Git, Python 3, Node.js LTS, and Java 21. During Python installation,
enable the option that adds Python to PATH. Restart PowerShell after installing
them.

Check the installations:

    git --version
    py --version
    node --version
    npm --version
    java --version

## Download and prepare the project

Clone the repository, open PowerShell in the Rocket-Server-2.0 folder, and run:

    Set-ExecutionPolicy -Scope Process Bypass
    .\setup-windows.ps1

The setup script creates the Python virtual environment and installs the Python
and Node packages. Maven is supplied by mvnw.cmd.

## Copy the live data

Git deliberately excludes the live database and Microsoft sign-in token.
Transfer these files privately from the Mac into the Windows project's data
folder:

    data\rocket_integration.db
    data\microsoft_email_token.json

Do not copy rocket_integration.db-shm or rocket_integration.db-wal while the Mac
servers are running. Stop Spring and Flask first so SQLite writes everything
into the main database file.

## Add the Microsoft Entra settings

Set the values once in PowerShell, replacing the examples with the Entra values:

    setx MICROSOFT_CLIENT_ID "your-client-id"
    setx MICROSOFT_CLIENT_SECRET "your-client-secret"
    setx MICROSOFT_TENANT_ID "consumers"
    setx MICROSOFT_REDIRECT_URI "http://localhost:8080/api/email/microsoft/callback"
    setx ROCKET_EMAIL_FROM "rocketpubserver@outlook.com"

Close PowerShell and open it again so the saved values become available.

## Start Rocket Server

From the project root:

    Set-ExecutionPolicy -Scope Process Bypass
    .\start-windows.ps1

Three PowerShell windows will open. Wait until each server reports that it is
ready, then visit http://localhost:3000/login.

The Customer Portal is at http://localhost:8000. The Booking Portal is available
through the Staff Portal after login.
