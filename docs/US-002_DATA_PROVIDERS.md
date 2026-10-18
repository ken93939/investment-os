# US-002: financial data providers

## Scope and provider choice

The `marketdata` Kotlin package defines application-owned models and capability
interfaces for instrument search/metadata, daily OHLCV, financial statements, and
macroeconomic time series. `MarketDataGateway` validates application inputs and
selects the configured provider. Business callers see these models and typed
errors, not vendor JSON or HTTP details.

Alpha Vantage is the first adapter because its documented endpoints span all four
capabilities. Its [current free allowance](https://www.alphavantage.co/support/)
is 25 requests per day; this is a starter integration, not a polling strategy.
Coverage and entitlements depend on the account. Read the
[provider documentation](https://www.alphavantage.co/documentation/) and applicable
data terms before using the data beyond personal research. No subscription or
provider account is created by this implementation.

Persistence/ingestion (US-003), a full quality pipeline (US-004), public search/UI
(US-006), and financial calculations remain separate stories. No provider data is
persisted and no new HTTP business endpoint is introduced here.

## Configure and run

Normal startup uses `MARKET_DATA_PRIMARY=none` and needs no provider credentials.
The Kotlin gateway then returns `NOT_CONFIGURED` for data requests. When you have
a key, set these in the appropriate ignored environment file:

```dotenv
MARKET_DATA_PRIMARY=alpha-vantage
ALPHA_VANTAGE_API_KEY=your-own-key
```

Use the root `.env` for Compose, or `backend/.env` exported into the shell for
host startup as described in the backend guide. Restart the backend after changing
configuration. Selecting Alpha Vantage without a key fails startup with a safe
configuration message. Unknown or duplicate provider IDs also fail configuration.
The key stays server-side; never put it in a `NEXT_PUBLIC_*` variable.

```sh
cd backend
./gradlew build ktlintCheck
# With ALPHA_VANTAGE_API_KEY exported into this shell:
./gradlew liveProviderTest
```

The ordinary `test` task excludes the `live-provider` JUnit tag. The explicit live
task requires the key, bypasses Gradle up-to-date caching, and fetches IBM metadata.
It does not require a running Spring application or database. Quota exhaustion,
network failure, invalid keys, or unavailable account entitlements fail that test.
Without credentials, the live acceptance criterion remains unverified.

## Interface and data flow

`MarketDataGateway` → selected `FinancialDataProvider` → Alpha Vantage adapter →
bounded HTTP transport → provider. Parsing and error mapping happen inside the
adapter before application models return to the caller.

For example, a future Kotlin service can inject `MarketDataGateway` and call
`instrument(" ibm ")`; the provider receives `IBM`. Changing `market-data.primary`
selects a different registered adapter without changing business callers. To add a
provider, implement the capability interfaces through `FinancialDataProvider`,
register a bean with a unique `id`, and test normalization/error behavior. An
unselected adapter must not make requests at startup.

There is no automatic cross-provider fallback: changing providers silently could
mix adjustment, coverage, or revision semantics. There are also no automatic
application retries. Typed failures expose retry hints when available, keeping
quota and retry policy explicit for the later ingestion/agent workflow.

## Capabilities and limits

| Capability | Adapter behavior |
| --- | --- |
| Search | `SYMBOL_SEARCH`, filtered to US equities and ETFs; other regions/types are omitted. Empty matches remain an empty result. |
| Metadata | `OVERVIEW` for companies. If absent, an exact ETF match from search supplies limited metadata with search provenance. Unknown instruments return `NOT_FOUND`. ETF fallback can cost a second request. |
| Prices | `TIME_SERIES_DAILY`, raw daily OHLCV, compact response (up to 100 recent bars). Bars are sorted and filtered to the requested inclusive date window. Adjusted-price requests return `UNSUPPORTED` without a network call. |
| Statements | Income, balance sheet, and cash-flow endpoints; annual or quarterly reports. Selected currency-unit metrics use application field names and exact decimal amounts. Unavailable fields remain null. |
| Macro | Real GDP, CPI, unemployment, federal funds rate, and monthly 10-year Treasury yield. Observation values retain source units and missing-value markers become null. |

`PriceHistory.availableRange` records the response's available window before date
filtering. A request outside the compact window may return fewer or no bars; that
does not prove the instrument has no older history. Trading-day completeness is
not guaranteed. Full/adjusted history requires a deliberate entitlement/design
change; raw prices must not be used as adjusted total-return series.

The raw price response does not supply currency or exchange. Those fields remain
null instead of assuming USD/NYSE. Search can supply a fixed-offset timezone,
which is not an exchange's historical daylight-saving calendar. Join with verified
instrument metadata before calculations that require currency or exchange context.

## Provenance and numerical meaning

- Every result carries provider, dataset, retrieval `Instant`, and freshness.
- Date-only provider refresh values remain `sourceDate`; no midnight timestamp is
  fabricated. `sourceAsOf` remains null when an actual instant is not supplied.
- Price bars use source trading dates and the supplied timezone. Daily bars,
  statements, and macro observations are labeled historical, never live.
- Fiscal period end is separate from publication time. `publishedAt` remains null
  where the endpoint supplies no publication evidence. Current statement/macro
  responses may be revised; they are not point-in-time backtest inputs.
- Financial report currency is required. Missing optional metrics remain null;
  malformed numbers, missing required fields, mismatched symbols, invalid OHLC,
  and duplicate report/observation dates fail normalization.
- Amounts use `BigDecimal`, volumes use exact `Long` conversion. No floating-point
  valuation arithmetic or synthetic prices are introduced. Documented numeric
  strings are parsed directly; unexpected JSON number types are rejected to avoid
  silently rounding them through a floating-point tree value. Macro unit labels are
  retained (for example percent or billions of dollars), not silently rescaled.

## Failure behavior

`MarketDataException` supplies a stable `MarketDataError` code, optional provider
identity, and optional retry delay. Errors distinguish invalid input, disabled
configuration, authentication, rate limits, unsupported requests, missing records,
timeouts, transport unavailability, and malformed responses. These are internal
application errors; later HTTP endpoints should map them to public error contracts.

The HTTP transport uses an eight-second total request/body deadline, a two-second
connect timeout, and an 8 MiB response limit. It rejects redirects so a
credential-bearing request cannot follow an unexpected destination. HTTP 429
retains `Retry-After` when supplied; JSON rate notices and entitlement failures
inside HTTP 200 responses are also recognized.

Alpha Vantage requires the key in its query string. The adapter never logs URLs,
provider bodies, or raw transport/parser causes. Avoid enabling JDK HTTP request
debug logging or outbound URL logging for this client; those facilities could
expose query credentials outside the adapter's error handling.

## Verification and what to learn

Tests use test-only providers, synthetic JSON fixtures, fixed clocks, and a local
JDK HTTP server. They require no remote provider or key. Tests exercise primary
selection, input rejection, all dataset mappings, exact decimals, nullable fields,
error envelopes, HTTP failures, retry metadata, timeouts during body reading,
response limits, and disabled startup configuration.

`./gradlew build ktlintCheck --no-daemon` passed with 40 tests, including the
existing migration/readiness tests. Compose configuration, image builds, isolated
stack startup with integration disabled, and `scripts/smoke-compose.sh` also passed.
The ordinary test report excludes the live provider class. The live integration
test exists but has not been run without credentials. This
story's final acceptance checkbox remains pending until it passes with a valid
key. The preceding US-001 hosted CI run is independently pending; no CI success
is inferred from local checks.

Start reading `MarketDataGateway.kt`, `FinancialDataProvider.kt`,
`MarketDataModels.kt`, and `alphavantage/AlphaVantageProvider.kt`. Trace one income
statement through normalization. Then remove `reportedCurrency` from a test fixture
and compare that failure with a missing optional `operatingIncome` value: one makes
the report unusable; the other remains an explicit unknown.
