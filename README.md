# Investment OS

Personal investment research terminal for market data, visualization, valuation,
portfolio analytics, and eventually evidence-grounded research agents.

## Repository layout

- [`backend/`](backend/README.md): independent Kotlin / Spring Boot Gradle build.
- [`frontend/`](frontend/README.md): independent Next.js / TypeScript application.
- `python/` (planned): independent quant and agent experiments, when needed.
- [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md): product and architecture direction.
- [`docs/LEARNING_PLAN.md`](docs/LEARNING_PLAN.md): learning and agent development guide.

The backend and frontend are scaffolded. Each project owns its dependencies and
build; a separate build does not require a separately deployed service.

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
