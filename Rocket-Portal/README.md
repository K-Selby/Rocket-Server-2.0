# Rocket Portal

The single Next.js application for the Rocket Pub Customer Portal and Staff Portal.

## Frontend

Public addresses live under `src/app`. The customer implementation is kept in
`src/customer-portal`, while the staff implementation is kept in
`src/staff-portal`. Small route files under `src/app` connect each public URL
to the appropriate portal.

From `Rocket-Portal`:

```bash
npm install
npm run dev
```

The application runs as the local gateway at `http://localhost:8000`. The
Customer Portal is available at `/` and the Staff Portal at `/staff`.

## Backend

From `Rocket-API`:

```bash
./mvnw spring-boot:run
```

The backend runs at `http://localhost:8080` and uses SQLite only. By default it expects the shared database at `../data/rocket_integration.db`. Set `ROCKET_DB_PATH` to an absolute database path when the project layout differs.
