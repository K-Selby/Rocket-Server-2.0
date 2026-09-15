# Windows setup

Open PowerShell as Administrator in the Rocket Server folder and allow local
scripts for the current Windows account:

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

Run the one-time setup:

```powershell
.\setup-windows.ps1
```

The setup installs Git, Python 3.12, Node.js LTS, npm, and Java 21 when they are
missing. It then installs project dependencies, builds the single Next.js
application in `Rocket-Portal`, starts the Booking Portal's Flask service,
Spring, and Next.js in the background, and registers a Windows task that
starts Rocket Server automatically when you sign in.

For normal updates, run:

```powershell
.\update-windows.ps1
```

Alternatively, copy `Rocket-Server-Update.bat` from the repository to the
Desktop and double-click it. The batch file requests Administrator access,
records the complete result in `Rocket-Server-Update.log` on the Desktop, and
keeps the result window open.

The updater checks GitHub first. When there is no new commit and all three
services are running, it exits. If a service is missing, it restarts the server.
When an update exists, it stops the background processes, fast-forwards the
repository, and rebuilds only the parts that changed. Python and Node packages
are reinstalled only when their dependency files change. Output is stored under
`runtime\logs`.

The Customer Portal uses Next.js and Spring without Flask. The Booking Portal
continues to use Flask. The public Cloudflare tunnel can continue pointing at `http://localhost:8000`.
The browser routes are `/` and `/customer` for customers, `/booking` for the
Booking Portal, and `/staff` for the Staff Portal.
