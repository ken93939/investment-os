# Investment OS

Personal investment research terminal for market data, visualization, valuation,
portfolio analytics, and eventually evidence-grounded research agents.

## Repository layout

- [`backend/`](backend/README.md): independent Kotlin / Spring Boot Gradle build.
- `frontend/` (planned): independent Next.js / TypeScript application.
- `python/` (planned): independent quant and agent experiments, when needed.
- [`docs/PROJECT_CONTEXT.md`](docs/PROJECT_CONTEXT.md): product and architecture direction.
- [`docs/LEARNING_PLAN.md`](docs/LEARNING_PLAN.md): learning and agent development guide.

Only the backend is scaffolded today. Each future project will own its dependency
manager and build; a separate build does not require a separately deployed service.

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
