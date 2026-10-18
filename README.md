# Investment OS

Personal investment research terminal for market data, visualization, valuation,
portfolio analytics, and eventually evidence-grounded research agents.

## Repository layout

- [`backend/`](backend/README.md): independent Kotlin / Spring Boot Gradle build.
- [`frontend/`](frontend/README.md): independent Next.js / TypeScript application.
- `python/` (planned): independent quant and agent experiments, when needed.
- [`contracts/`](contracts/README.md): shared API contracts, starting with health/readiness.
- [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md): product and architecture direction.
- [`docs/LEARNING_PLAN.md`](docs/LEARNING_PLAN.md): learning and agent development guide.

The backend and frontend are scaffolded. Each project owns its dependencies and
build; a separate build does not require a separately deployed service.

## Full-stack local setup

Requires Docker with Compose v2. For host-only development, see the independent
backend and frontend setup sections below.

```sh
cp .env.example .env
# Edit .env and choose a local DB_PASSWORD.
docker compose up --build
```

Open <http://127.0.0.1:3000>. The backend is at <http://127.0.0.1:8080>.
PostgreSQL starts first, then the backend applies Flyway migrations and becomes
ready, then the frontend starts. Container traffic uses Compose service names;
published ports bind to loopback. No financial provider credentials are needed.

For detached startup and a repeatable connectivity check:

```sh
docker compose up -d --build --wait
bash scripts/smoke-compose.sh
docker compose stop
```

Stopping preserves database data. The root Compose stack and `backend/compose.yaml`
are alternative workflows: do not start both on the same ports. Root Compose uses
the root `.env`; host backend configuration uses `backend/.env`, and host Next.js
configuration uses `frontend/.env.local`.

### Troubleshooting

- **Port already in use:** set `POSTGRES_PORT`, `BACKEND_PORT`, or `FRONTEND_PORT`
  in the root `.env`. Container-to-container addresses do not change.
- **Database authentication fails after changing `.env`:** PostgreSQL initializes
  credentials only for a fresh volume. Restore the original local credentials or
  deliberately migrate/reset disposable data; changing the file does not rotate them.
- **Backend not ready:** run `docker compose logs backend` and inspect Flyway or
  database errors. `/health` checks process liveness; `/ready` includes the database.
  A database outage must not be treated as a dead JVM.
- **Migration validation fails:** restore the applied migration and add a new
  versioned migration. Do not edit applied scripts or bypass checksum validation.
- **Docker socket unavailable:** start Docker Desktop and check `docker version`.
- **Host Node version mismatch or Turbopack sandbox errors:** see the
  [frontend troubleshooting notes](frontend/README.md#checks-and-production-build).

Backend console logs use Spring Boot's structured ECS JSON format. Health responses
omit internal details. The [operational contract](contracts/openapi.yaml) defines
the public health/readiness response. CI runs independent backend/frontend checks
and a disposable full-stack smoke check; local checks remain usable without CI.

## Contribution workflow

Use a separate branch for every implementation task, user story, feature, bug fix,
or standalone documentation change. Review the change and its verification results
before merging into the default branch (`main` or `master`); merging requires the
owner's explicit approval. See [AGENTS.md](AGENTS.md#branches-and-review) for branch
naming, handling existing work, and the full review rules.

## Backend checks

Requires JDK 21. No system Gradle or database is needed for the checks:

```sh
cd backend
./gradlew clean build ktlintCheck
```

See the [backend guide](backend/README.md) for PostgreSQL setup and application startup.

## Frontend setup

Requires Node 24 and npm:

```sh
cd frontend
nvm use # If using nvm.
npm ci
npm run dev
```

Open <http://127.0.0.1:3000>. See the [frontend guide](frontend/README.md) for
tests, production builds, and the optional backend connection check.
