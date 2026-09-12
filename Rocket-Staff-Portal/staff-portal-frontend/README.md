# Rocket Staff Portal

Internal staff portal for Rocket Pub. The frontend is built with Next.js/React and the backend uses Spring Boot with the shared Rocket SQLite database.

## Frontend

From `staff-portal-frontend`:

```bash
npm install
npm run dev
```

The frontend runs as the local gateway at `http://localhost:8000`, with the
Staff Portal available at `http://localhost:8000/staff`.

## Backend

From `staff-portal-backend`:

```bash
./mvnw spring-boot:run
```

The backend runs at `http://localhost:8080` and uses SQLite only. By default it expects the shared database at `../../data/rocket_integration.db`. Set `ROCKET_DB_PATH` to an absolute database path when the project layout differs.
