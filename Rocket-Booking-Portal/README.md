# Rocket Booking Portal

The internal Booking Portal and public Customer Portal run together from one
Flask process. Staff authentication is supplied by the Spring Staff Portal API.
The application uses the shared SQLite database at
data/rocket_integration.db.

## Local development

From this folder on macOS:

    python3 -m venv .venv
    source .venv/bin/activate
    python -m pip install -r requirements.txt
    python run.py

On Windows, use the setup-windows.ps1 and start-windows.ps1 scripts in the
project root.

The Flask server provides:

- Customer Portal at http://localhost:8000
- Booking Portal at http://localhost:8000/booking/dashboard
- shared images and menus under http://localhost:8000/assets

Protected Booking Portal pages require a valid Staff Portal session. Start the
Spring backend on port 8080 and the Staff frontend on port 3000 before signing
in.

## Configuration

Copy .env.example to .env for local settings. Keep .env, the live database,
Microsoft tokens, logs, backups, virtual environments, and generated cache
folders out of Git.
