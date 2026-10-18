# Investment OS backend

Independent Kotlin / Spring Boot modular monolith. Build and run from this directory;
neither the frontend nor planned Python project is needed.

## Toolchain

| Component | Version / choice |
| --- | --- |
| Java | 21 |
| Kotlin | 2.3.21 |
| Spring Boot | 4.1.0 |
| Gradle wrapper | 8.14.5 |
| ktlint Gradle plugin | 14.2.0 |
| Pinterest ktlint engine | 1.5.0 |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL driver |
| Tests | Boot-managed JUnit Jupiter, Kotlin test assertions, Zonky embedded PostgreSQL |

Boot, Kotlin, and Gradle match the local `architect-framework` baseline. Versions
are pinned for reproducibility, rather than claimed to be the latest releases.
The ktlint Gradle plugin runs the actual `com.pinterest.ktlint` engine. JUnit uses
the version managed by Boot (JUnit 6), with the Jupiter API and JUnit Platform.

## Build and lint

```sh
./gradlew clean build ktlintCheck
./gradlew test
./gradlew ktlintFormat
```

`build` includes tests and ktlint checks. `ktlintFormat` changes source formatting.
The wrapper downloads Gradle and dependencies on first use. Open `backend/` as a
Gradle project in IntelliJ and select JDK 21.

## Run locally with PostgreSQL

Requires Docker Compose, or an existing PostgreSQL instance.

```sh
cp .env.example .env
# Edit .env and choose a local password.
docker compose up -d --wait
set -a
. ./.env
set +a
./gradlew bootRun
```

Compose reads `.env`; Spring does not automatically read that file, which is why
the shell exports it above. Keep values shell-compatible when sourcing it: quote
values containing spaces or shell metacharacters. For an existing database, skip
Compose and export `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` yourself.

Check `http://localhost:8080/actuator/health`; a connected database returns status
`UP`. No stock or portfolio API exists yet. Only health is exposed through Actuator.
HTTP and the Compose database port bind to loopback for local use.
`/health` reports process liveness; `/ready` reports readiness including database
connectivity. Backend logs use Spring Boot's built-in ECS JSON format.

```sh
./gradlew bootJar
java -jar build/libs/investment-os-backend-0.1.0-SNAPSHOT.jar
docker compose stop
```

The JAR requires the same exported database variables. Compose stores data in a
named volume; stopping containers preserves it. Changing `.env` credentials does
not change credentials in an already initialized PostgreSQL volume.

## Main flow and design choices

The [backend architecture guide](../docs/BACKEND_ARCHITECTURE.md) describes the
capability modules, ports and adapters, typed provider clients, mapping, and
dependency checks. Domain and application code remain independent of Spring,
Jackson, JPA, and HTTP client libraries.

1. `InvestmentOsApplication.kt` starts Spring and scans `com.kenkwok.investmentos`.
2. `application.yaml` configures PostgreSQL and JPA from environment variables.
3. Capability packages own domain models, application logic, ports, and adapters.
   Services should define transaction boundaries; `open-in-view: false` prevents
   controllers from silently fetching lazy database state after a service returns.

The Spring Kotlin plugin opens Spring classes for proxies. The JPA plugin generates
entity no-argument constructors, and `allOpen` enables Hibernate entity proxies.
Use ordinary classes for entities; reserve data classes for value objects and DTOs.

Flyway runs before JPA and owns the `investment_os` schema. The initial
`db/migration/V1__initialize_schema.sql` migration establishes the baseline without
inventing domain tables. Hibernate validates rather than modifying the runtime
schema. Add the first domain tables in V2 or later; preserve applied scripts so
Flyway can detect checksum changes. Destructive Flyway clean is disabled.
JDBC timestamps use UTC; future financial records must also retain provider,
source as-of time, exchange, currency, and data freshness explicitly.

## Verification and failure boundaries

Tests use [Zonky embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres)
2.2.2 with PostgreSQL binaries pinned to 17.8.0, matching the Compose database's
major version. Zonky starts a native PostgreSQL process without Docker or a local
database installation. Gradle downloads its binaries as test dependencies, including
ARM64 binaries for macOS and Linux alongside the library's default x86-64 binaries.

`EmbeddedPostgresConfiguration` starts one database on a randomly assigned port
per Spring test context and supplies its datasource directly to JPA. Spring closes
the database after JPA when the context closes; Zonky's independent JVM shutdown
hook is disabled to preserve that order. Initialization explicitly uses the portable
`C` locale with UTF-8 encoding, avoiding failures from inherited host locales.
This gives tests C collation; locale-specific sorting needs explicit tests when used.
No `DB_*` environment variables are needed;
the test datasource does not use the application's external database configuration.
The `test` profile creates/drops only the test entity schema in this temporary
database. The JUnit tests verify PostgreSQL 17, startup/JPA wiring, generated IDs,
database reload, rollback after a flushed write, fresh migration/repeatability,
migration checksum protection, and operational endpoint/readiness behavior. The write tests roll back
their changes so they can share a context without retaining test records.

Run tests normally from Gradle or IntelliJ. Native PostgreSQL startup requires a
supported OS/architecture, permission to execute the extracted binaries, and a
non-root user. Startup failures fail the tests. Add domain migration and concurrency
tests alongside the first domain schema. Flyway runs in these tests as it does at
runtime; only the test-only JPA probe uses Hibernate create/drop.

Missing credentials, an unavailable database, or schema mismatches prevent normal
startup. Embedded PostgreSQL is a test-only dependency. Pool connection acquisition
has a five second timeout. The health endpoint also checks database availability.

## Financial data providers

The `marketdata` module implements US-002's provider contracts and the initial
Alpha Vantage adapter. `application.MarketDataGateway` calls application-owned
outbound ports; the Alpha Vantage adapter calls a typed `RestClient` client and
maps provider DTOs into domain models. ArchUnit tests enforce the dependency rules.
It is disabled by default and needs no key for normal
startup/tests. See [the provider guide](../docs/US-002_DATA_PROVIDERS.md) for
configuration, dataset limitations, provenance, typed failures, and the explicit
`./gradlew liveProviderTest` command. Financial HTTP endpoints and persistence
belong to later stories.

## Architect Framework adoption

The sibling `~/IdeaProjects/architect-framework` is available as a reference, but is
not a build dependency. Its current starter also brings Kafka/Avro and requires
reliability security configuration. Pulling it in now would add infrastructure
before this application has a workflow requiring it.

When an import or research workflow needs safe retries, evaluate its idempotency
capability first. Use durable events/outbox or sagas only when an actual consistency
requirement justifies them. Prefer a versioned library dependency with compatibility
tests over an absolute path to a sibling checkout.

To explore the scaffold, run the rollback test and remove the simulated failure:
observe why a flush sends SQL but does not itself commit a transaction.
