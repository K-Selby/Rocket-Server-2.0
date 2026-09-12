# Rocket Server 2.0 local development

The project runs as three local processes behind one browser address:

- Customer and Booking portals (one Flask server): `http://localhost:8001`
- Staff Portal frontend and local gateway (Next.js): `http://localhost:8000`
- Staff Portal API and authentication (Spring): `http://localhost:8080`

Use these browser addresses:

- Customer Portal: `http://localhost:8000/` or `http://localhost:8000/customer`
- Booking Portal: `http://localhost:8000/booking`
- Staff Portal: `http://localhost:8000/staff`

The source folders are:

- `Rocket-Staff-Portal`
- `Rocket-Customer-Portal`
- `Rocket-Booking-Portal`
- `data` for the shared `rocket_integration.db`

Use `localhost` for all three addresses. The Booking Portal checks the Spring
`JSESSIONID` cookie, and browser cookies are shared between localhost ports.

## 1. Start Spring

Open a Terminal in:

```text
Rocket-Staff-Portal/staff-portal-backend
```

Run:

```bash
./mvnw spring-boot:run
```

## 2. Start the Staff Portal

Open another Terminal in:

```text
Rocket-Staff-Portal/staff-portal-frontend
```

Run:

```bash
npm install
npm run dev
```

## 3. Start Flask

Open a third Terminal in:

```text
Rocket-Booking-Portal
```

Run:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python3 run.py
```

The public Customer Portal is available at `http://localhost:8000`. Open
`http://localhost:8000/staff/login`, sign in, and use the Booking Portal link to open
`http://localhost:8000/booking/dashboard`.
Opening a protected Flask page without a valid Spring session redirects to the
Staff Portal login screen.

Optional environment variables:

```bash
export ROCKET_STAFF_PORTAL_URL=http://localhost:8000/staff
export ROCKET_STAFF_API_URL=http://localhost:8080
export ROCKET_FLASK_PORT=8001
export NEXT_PUBLIC_BOOKING_PORTAL_URL=http://localhost:8000/booking/dashboard
```

These defaults are already built in, so they are only needed when using
different ports or deployed addresses.
