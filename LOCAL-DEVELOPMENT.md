# Rocket Server 2.0 local development

The project runs as two local processes behind one browser address:

- Customer, Booking and Staff Portal frontend, shared assets, and local gateway (Next.js): `http://localhost:8000`
- Shared API and authentication (Spring): `http://localhost:8080`

Use these browser addresses:

- Customer Portal: `http://localhost:8000/` or `http://localhost:8000/customer`
- Booking Portal: `http://localhost:8000/booking`
- Staff Portal: `http://localhost:8000/staff`

The source folders are:

- `Rocket-Portal` for the single Next.js application, with separate customer, booking and staff route folders
- `Rocket-API` for the shared Spring API
- `data` for the shared `rocket_integration.db`

## 1. Start Spring

Open a Terminal in:

```text
Rocket-API
```

Run:

```bash
./mvnw spring-boot:run
```

## 2. Start the Rocket Portals

Open another Terminal in:

```text
Rocket-Portal
```

Run:

```bash
npm install
npm run dev
```

Open `http://localhost:8000/staff/login`, sign in, and use the Booking Portal link.
Every protected portal page uses the same Spring session.

Optional environment variables:

```bash
export ROCKET_STAFF_PORTAL_URL=http://localhost:8000/staff
export ROCKET_STAFF_API_URL=http://localhost:8080
export NEXT_PUBLIC_BOOKING_PORTAL_URL=http://localhost:8000/booking
```

These defaults are already built in, so they are only needed when using
different ports or deployed addresses.
