# Codex Project Instructions

## Who I am
- I am an experienced software engineer, fluent in Kotlin, with some Python experience including data pipelines.
- I use Codex for most implementation, but I want to understand architecture, critical data flows, failure modes, and AI-agent behavior.
- Work with me as a senior engineering partner, not as an opaque code generator.

## Goal
Build a personal Bloomberg-inspired investment research application with portfolio analytics, financial data, and AI research agents. This is initially a personal research and learning tool, not an automated trading platform.

## Technical direction (provisional; validate against actual repository)
- Main backend: Kotlin + Spring Boot.
- Frontend: Next.js + TypeScript.
- Persistence: PostgreSQL.
- AI-agent/quant research experiments: Python, introduced where they add real value.
- Begin with a modular monolith. Do not introduce separate services, Kafka, Kubernetes, or multiple runtimes without a concrete requirement and rationale.
- Read `docs/PROJECT_CONTEXT.md` before proposing architectural changes.
- Use `docs/LEARNING_PLAN.md` when implementing agent-related features.

## Workflow
1. Inspect relevant existing code and project documentation.
2. For a nontrivial task, give a short plan, acceptance criteria, and architectural trade-offs before making large changes.
3. Implement incrementally and keep changes reviewable.
4. Add automated tests for nontrivial logic and failure cases.
5. Run relevant tests, lint, and type checks when possible; report commands and actual results. Never claim an unexecuted test passed.
6. At the end explain: what changed, important code paths, key design decisions, failure modes, and what I should learn from this task.
7. Ask before destructive changes, production data operations, real transactions, or exposing credentials.

## Maintaining this guide
- Keep `AGENTS.md` current as part of ongoing work. When we agree on an engineering practice, coding standard, testing convention, or discover a recurring pitfall, update the relevant section in the same change without waiting for a separate request.
- Record durable, actionable guidance and the reason for nonobvious constraints. Distinguish agreed decisions from provisional proposals.
- Revise or remove outdated guidance rather than accumulating contradictory rules. Keep each rule in one authoritative place.
- Keep task history, transient failures, and individual test-run results out of this guide. Put detailed setup and walkthroughs in the relevant README or `docs/` file and link to them here.
- Before finishing a task, check whether its accepted decisions or lessons require a guide update; summarize any changes to the user.

## Branches and review
- Create a separate branch for each implementation task, user story, feature, or bug fix before editing files. Apply the same workflow to standalone documentation and engineering-practice changes.
- Keep the repository's default branch (`main` or `master`) reserved for reviewed changes. Do not implement or commit directly on it, or merge into it without explicit user approval following review.
- Use descriptive branch names such as `codex/feat-<story-id>-<description>`, `codex/fix-<description>`, or `codex/docs-<description>`. Continue on an existing branch only when it belongs to the same task.
- Start independent work from the default branch. If work depends on an unmerged branch, state that dependency and the review base explicitly.
- Check the current branch and working-tree status before starting. Preserve unrelated user changes; use an isolated worktree when needed rather than moving those changes into another task's branch.
- Keep implementation, tests, and related documentation together on the task branch. Present the change and actual verification results for review before merging.
- If permissions prevent branch creation, report the limitation before making changes and leave the default branch's history untouched. Do not bypass the review requirement or claim a branch was created.

## Repository and backend conventions
- Keep `backend/` independently buildable with its own Gradle wrapper. Future `frontend/` and `python/` projects should own their builds and dependencies; separate builds do not imply separate deployed services.
- For backend setup, IntelliJ import, database configuration, and build commands, read `backend/README.md` before changing those workflows. In IntelliJ, link `backend/build.gradle.kts` as a Gradle project and use the configured JDK.
- Use Kotlin with Spring Boot, Spring Data JPA, and PostgreSQL for the backend. Treat the Gradle build as the source of truth for toolchain and dependency versions.
- Enforce Kotlin formatting with Pinterest ktlint and `backend/.editorconfig`. Run `./gradlew ktlintCheck` from `backend/`; use `ktlintFormat` to apply formatting fixes.
- Put database transaction boundaries in the service layer. Keep JPA open-in-view disabled. Flyway owns the `investment_os` schema and runs before Hibernate validation; add versioned migrations for schema changes and preserve applied scripts/checksums. Keep destructive Flyway clean disabled.
- Use ordinary classes for JPA entities and data classes for appropriate value objects and DTOs. Preserve the Kotlin Spring/JPA compiler support needed for proxies and entity construction.
- Evaluate `~/IdeaProjects/architect-framework` when a concrete workflow needs idempotency, durable events, or sagas. Inspect its current dependencies and configuration before adoption, and prefer a versioned dependency over coupling builds to an absolute sibling-checkout path.

## Testing conventions
- Use JUnit Jupiter for backend tests. Name every test in the `GIVEN <precondition> WHEN <scenario> THEN <expectation>` format, with uppercase keywords. In Kotlin, use backtick function names so the scenario reads as a sentence.
- Separate each test body with `// GIVEN`, `// WHEN`, and `// THEN`: arrange the initial state, execute the behavior under test, then assert the expected outcome. Keep failure assertions in the THEN section by capturing the outcome in WHEN when necessary.
- Keep unit tests independent of Spring and database startup when they exercise pure logic. Use integration tests for persistence, framework wiring, and transaction behavior.
- Use Zonky embedded PostgreSQL for database tests; do not use H2. Keep embedded database dependencies and configuration in the test scope, and align the PostgreSQL major version with the application's database.
- Reuse `backend/src/test/kotlin/com/kenkwok/investmentos/EmbeddedPostgresConfiguration.kt` for Spring database tests. It supplies an isolated datasource without Docker or `DB_*` credentials; tests must not connect to a developer or production database.
- Use an ephemeral database port, initialize with the portable `C` locale and UTF-8 encoding, and let Spring close JPA before stopping PostgreSQL. Keep Zonky's independent JVM shutdown hook disabled to avoid cleanup races. Add explicit collation tests if a feature depends on locale-specific sorting.
- Keep tests isolated through rollback or explicit cleanup. Cover meaningful failure cases, including transaction rollback after flushed writes where persistence is involved.
- For backend code changes, run `./gradlew test ktlintCheck` from `backend/`; run `./gradlew build ktlintCheck` when build configuration, dependencies, or packaging change. Report failures and environmental limitations accurately.
- Add migration, PostgreSQL-specific SQL, and concurrency tests when those behaviors are introduced; the existing persistence smoke tests do not establish those guarantees.

## Market-data provider conventions
- Use the application-owned contracts in the backend `marketdata` package. Keep provider JSON, HTTP status handling, and field mappings inside adapters; callers use `MarketDataGateway` and typed `MarketDataException` codes.
- Read `docs/US-002_DATA_PROVIDERS.md` before changing provider configuration, coverage, adjustment semantics, or error handling. Provider integration stays optional; ordinary builds/tests make no remote provider calls.
- Preserve provider/dataset provenance, retrieval time, source dates, units, and explicit unknown metadata. Keep publication time distinct from fiscal period end; current/revised records are not point-in-time backtest evidence.
- Keep currency-dependent financial amounts as `BigDecimal`. Never replace missing fields with zero or label raw daily prices as adjusted/live data. Preserve available history windows and nullable currency/exchange information.
- Bound provider request and response-body time, response size, and redirects. Map HTTP and successful-status error envelopes to safe application errors. Keep automatic retries and cross-provider fallback explicit rather than silently consuming quota or mixing data semantics.
- Keep API keys server-side and out of generated `toString`, URLs in logs, raw response/error messages, and exception causes. The opt-in `liveProviderTest` task is separate from the ordinary test task and requires credentials supplied through environment variables.

## Frontend conventions
- Keep `frontend/` independently buildable with npm and its own lockfile. Use Node 24 (`frontend/.nvmrc`) and `npm ci`; read `frontend/README.md` before changing setup, checks, or backend connectivity.
- Use Next.js App Router, strict TypeScript, and Tailwind CSS, with ESLint for linting and Prettier for formatting. Keep pages as Server Components by default and limit Client Components to interactive UI.
- Keep financial business logic and deterministic calculations in the backend. Next.js route handlers may adapt server-side API calls for the UI; provider credentials and backend origins belong in server-only configuration.
- Represent missing financial values as unavailable and preserve provenance and freshness when data is introduced. Every data-driven UI needs loading, empty, and error states; backend health and market-data availability are distinct.
- Use semantic navigation, keyboard focus indicators, accessible status messages, and responsive layouts. Use local/system fonts so basic builds do not require external font services.
- Use Vitest and React Testing Library for frontend logic and interaction tests, with mocked network responses. Apply the same GIVEN/WHEN/THEN test names and comment sections used by the backend.
- For frontend changes, run `npm run lint`, `npm run format:check`, `npm run typecheck`, `npm test`, and `npm run build` from `frontend/`. Run a browser smoke check when UI behavior or layout changes, and state any verification limits.

## Full-stack setup and operations
- Use the root `compose.yaml` for the full stack and `backend/compose.yaml` only as an alternative host-development database. Read the root README before changing container networking, ports, or environment-file ownership.
- Keep container-to-container origins on service DNS names and publish local development ports on loopback. Supply database credentials through ignored environment files; exclude local environment files from Docker build contexts.
- Keep `/health` for process liveness and `/ready` for readiness including PostgreSQL. Database outages should fail readiness without failing liveness. The frontend connection check uses readiness.
- Maintain shared API schemas in `contracts/` alongside endpoint changes. Use Spring Boot's built-in structured JSON logging rather than adding another logging stack.
- When changing full-stack setup, validate Compose configuration, build/start an isolated stack with disposable credentials and its own volume, and run `bash scripts/smoke-compose.sh`. Preserve existing developer data. Report CI configuration separately from checks actually executed.

## Engineering and financial safety
- Never commit secrets; use environment variables and documented local setup.
- Validate untrusted inputs and outputs; handle timeouts, rate limits, retries, and idempotency.
- In financial data, track provider, as-of timestamp, timezone, exchange, currency, and whether data is live, delayed, or historical.
- Use deterministic, unit-tested code for calculations; the LLM interprets results, not invents numbers.
- Distinguish factual data, computed metrics, assumptions, and model opinions; cite underlying records where possible.
- Avoid look-ahead bias in backtests; consider splits, dividends, missing observations, and restated financial statements.
- Do not enable automated trading or external financial transactions without a separately approved design and explicit human authorization.

## Skills and tools
- External skills such as grill-me, pstack, or gstack may be evaluated, but are optional. Verify Codex compatibility and inspect their scripts/permissions before installation.
- Prefer a small, nonconflicting set of capabilities; do not blindly install overlapping frameworks.
