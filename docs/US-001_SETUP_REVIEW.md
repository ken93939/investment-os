# US-001 setup review

## Scope and architecture

The existing independent Kotlin/Spring backend and Next.js/TypeScript frontend
already provided build tooling, tests, environment examples, and a frontend health
proxy. The missing foundation was full-stack startup, migrations, operational
endpoint semantics, structured logging, shared health contracts, and CI.

This implementation follows the agreed Kotlin backend direction. US-001's older
FastAPI/Alembic references were reconciled to Spring Boot/Flyway. Later Python,
calculation, and agent choices remain decisions for their own stories.

## Acceptance review

| Criterion | Implementation and verification |
| --- | --- |
| Compose starts required services | Root Compose builds PostgreSQL 17.8, backend, and frontend; healthy dependency ordering; verified in an isolated project. |
| Frontend reaches backend | Runtime `BACKEND_URL` uses Compose DNS; `scripts/smoke-compose.sh` checks the Next.js proxy and backend readiness. |
| Backend connects to PostgreSQL | JPA/Flyway run against PostgreSQL; local tests use Zonky, and Compose uses a real container. |
| Fresh database migrations | Flyway creates the owned `investment_os` schema, records V1, validates checksums, and runs before Hibernate validation. Tests cover repeat migration and checksum mismatch. |
| Credentials stay out of source | Only placeholder environment examples are added. Local environment files are ignored and excluded from Docker builds; verification uses temporary random credentials. |
| Setup and troubleshooting | Root/component READMEs cover environment-file ownership, ports, migrations, authentication pitfalls, and checks. |
| CI runs checks | GitHub Actions defines backend build/test/lint, frontend lint/format/types/test/build, and a disposable Compose smoke job. First hosted execution remains pending. |

## Important paths

- `compose.yaml`, `backend/Dockerfile`, `frontend/Dockerfile`: build, runtime
  configuration, non-root application processes, and startup dependencies.
- `backend/src/main/resources/application.yaml`: Flyway, schema validation,
  ECS JSON logging, liveness `/health`, and database-aware readiness `/ready`.
- `backend/src/main/resources/db/migration/V1__initialize_schema.sql`: schema
  baseline without domain tables. Add subsequent migrations rather than editing V1.
- `contracts/openapi.yaml`: operational response contract.
- `.github/workflows/ci.yaml`, `scripts/smoke-compose.sh`: repeatable checks.

## Verification

- `cd backend && ./gradlew build ktlintCheck --no-daemon`: 7 tests passed.
- Frontend lint, format, TypeScript, and Vitest: passed, 18 tests.
- Host frontend build with `--webpack`: passed; the normal Turbopack build passed
  inside Docker, where local worker ports are available.
- Isolated Compose startup with image builds and a fresh volume: passed.
- `bash scripts/smoke-compose.sh` against that stack: passed.
- Database outage check: `/health` stayed `UP`, `/ready` returned HTTP 503, and
  the frontend proxy returned `unavailable`. Restarting PostgreSQL restored the
  full-stack smoke check. V1 remained recorded once after backend restarts.
- Structured backend logs were parsed as ECS JSON records.
- GitHub Actions configuration is present; no hosted CI run is claimed.

## What to learn

Liveness answers whether the process is alive. Readiness answers whether it can
serve useful requests. A PostgreSQL outage makes this application unready, while
the JVM remains live. Keep liveness independent of database and readiness signals
so a database outage does not trigger pointless application restarts.

Try stopping only a disposable local stack's PostgreSQL service, compare `/health`
and `/ready`, then restart PostgreSQL and rerun the smoke check. Preserve local
volumes unless you deliberately intend to discard that development data.
