# Rocket Booking Portal

The internal Booking Portal runs from Flask. Staff authentication is supplied
by the Rocket API.
The application uses the shared SQLite database at
data/rocket_integration.db.

## Local development

From this folder on macOS:

    python3 -m venv .venv
    source .venv/bin/activate
    python -m pip install -r requirements.txt
    python run.py

On Windows, use `Rocket-Server-Update.bat` in the project root.

The Flask server provides the Booking Portal internally on port 8001. The
Rocket Portal exposes it at http://localhost:8000/booking.

Protected Booking Portal pages require a valid Staff Portal session. Start the
Spring backend on port 8080, Flask internally on port 8001, and the Staff
frontend gateway on port 8000 before signing
in.

## Configuration

Copy .env.example to .env for local settings. Keep .env, the live database,
Microsoft tokens, logs, backups, virtual environments, and generated cache
folders out of Git.
