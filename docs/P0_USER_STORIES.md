# InvestmentOS — P0 MVP User Stories

**Document Version:** 1.0  
**Product:** InvestmentOS — AI-Powered Personal Investment Research Platform  
**Status:** Ready for implementation planning  
**Scope:** US equities and ETFs, end-of-day or delayed data, read-only investment research

---

## 1. Project Context

InvestmentOS is a personal investment research application that combines traditional financial analysis tools with an AI-powered research agent.

The user must be able to:

1. Search for stocks and ETFs.
2. View historical market prices and company financial information.
3. Manage a personal watchlist.
4. Perform fundamental and valuation analysis.
5. Ask an AI agent to research a company using verified financial data.
6. Review research reports with traceable sources.
7. Save and revisit research results.

### Required Technology Stack

- Frontend: Next.js, TypeScript, Tailwind CSS
- Backend: Python, FastAPI, Pydantic, SQLAlchemy
- Database: PostgreSQL
- Database migrations: Alembic
- AI Agent: LangGraph
- Vector storage: pgvector, only when document retrieval requires it
- Background processing: Redis and a worker where needed
- Financial charts: TradingView Lightweight Charts
- Containerization: Docker Compose
- Testing: pytest, Vitest, Playwright

### Development Principles

- Use a modular monolith architecture.
- Keep business logic outside API controllers.
- Use typed request and response schemas.
- All financial calculations must be deterministic and unit-tested.
- Do not allow the AI model to invent financial figures.
- Persist source metadata and data timestamps.
- Do not hardcode API credentials.
- External data providers must be replaceable through adapters.
- Include loading, empty, and error states in every UI feature.
- Never execute real trades in P0.
- Use UTC internally and ISO 8601 timestamps.
- Use decimal-safe arithmetic for monetary calculations.
- Do not treat stale or delayed financial data as real-time data.

---

# EPIC 01 — Market Data Foundation

**Objective:** Establish a reliable data layer shared by the application and AI agent.

## US-001 — Project Initialization

**User Story**

As a developer, I want a reproducible development environment so that the application can be developed, tested, and deployed consistently.

**Implementation Requirements**

- Initialize a monorepo with separate frontend, backend, and shared contracts.
- Configure Next.js, FastAPI, PostgreSQL, and Docker Compose.
- Introduce configuration through environment variables.
- Add database migrations using Alembic.
- Implement structured application logging.
- Provide `/health` and `/ready` backend endpoints.
- Include setup documentation and example environment configuration.
- Create automated linting, type checking, and testing commands.

**Acceptance Criteria**

- [ ] `docker compose up --build` starts required services.
- [ ] Frontend can reach the backend API.
- [ ] Backend connects to PostgreSQL.
- [ ] Database migrations execute successfully on a fresh database.
- [ ] No credentials are committed to source control.
- [ ] README includes local setup, tests, and troubleshooting.
- [ ] CI executes linting, type checks, and basic tests.

**Dependencies:** None

## US-002 — Financial Data Provider Abstraction

**User Story**

As an investor, I want market information from reliable providers so that I can analyze companies without manually gathering data.

**Implementation Requirements**

Implement provider interfaces for:

- Instrument search and metadata
- Historical OHLCV prices
- Company financial statements
- Macroeconomic time series

Provider adapters must normalize responses into application-owned schemas.

Normalized results must include provider identity, retrieval timestamp, currency, and relevant market-data timestamps.

Provide a configurable primary provider. Support adding alternative providers without changing business services.

Handle provider rate limits, request failures, missing fields, and unsupported instruments.

**Acceptance Criteria**

- [ ] Providers implement defined interfaces.
- [ ] Services do not depend directly on provider-specific response formats.
- [ ] Input symbols are normalized and validated.
- [ ] Provider errors map to standardized application errors.
- [ ] Returned data includes source and timestamp metadata.
- [ ] Unit tests use mocked providers without network access.
- [ ] An integration test can fetch a supported US instrument when configured with valid provider credentials.

**Dependencies:** US-001

## US-003 — Instrument Registry and Historical Price Storage

**User Story**

As an investor, I want historical prices to be available consistently so that I can compare market performance over time.

**Implementation Requirements**

Create database models for:

- `instruments`
- `price_bars`
- `data_sources`
- `data_ingestion_runs`

Support daily OHLCV records and instrument metadata.

Include exchange, asset type, trading currency, and provider identifiers. Preserve raw versus adjusted price semantics.

Add incremental synchronization with idempotent upserts.

Expose a backend endpoint for historical price queries with date-range filtering.

**Acceptance Criteria**

- [ ] Repeated ingestion does not produce duplicate price bars.
- [ ] Historical prices are returned chronologically.
- [ ] Date-range filtering works correctly.
- [ ] Invalid symbols and date ranges return appropriate errors.
- [ ] Missing prices are not fabricated.
- [ ] Price adjustment methodology is explicitly identifiable.
- [ ] Data can be queried without fetching the same remote history on every request.

**Dependencies:** US-002

## US-004 — Data Quality, Freshness and Validation

**User Story**

As an investor, I want to know whether financial information is accurate, current, and complete so that I do not base decisions on misleading data.

**Implementation Requirements**

Introduce data validation for:

- Required fields and types
- Timestamp consistency
- Duplicate observations
- Invalid OHLC relationships
- Negative volume
- Missing or stale observations
- Currency and unit mismatch

Create a reusable data-quality status and metadata response.

Track provider errors and ingestion outcomes.

**Acceptance Criteria**

- [ ] Invalid records are rejected or quarantined with an explanation.
- [ ] Data freshness is visible through API metadata.
- [ ] No delayed quote is labeled as real-time.
- [ ] Missing values remain missing rather than becoming zero.
- [ ] Failed ingestion does not corrupt previously validated records.
- [ ] Validation and stale-data scenarios have automated tests.

**Dependencies:** US-002, US-003

---

# EPIC 02 — Market Dashboard and Watchlist

**Objective:** Give the investor a centralized view of markets and tracked securities.

## US-005 — Market Overview Dashboard

**User Story**

As an investor, I want to see major market indicators in one dashboard so that I can quickly understand overall market conditions.

**Implementation Requirements**

Create a market overview page covering, where supported by configured providers:

- S&P 500
- NASDAQ Composite
- Dow Jones Industrial Average
- Russell 2000
- VIX
- US 10-year Treasury yield
- Gold
- Crude oil

Display latest available values, relevant changes, and data timestamps.

Use provider-supported benchmarks or identified ETF proxies. Never silently replace an index with an ETF.

**Acceptance Criteria**

- [ ] Dashboard loads available indicators.
- [ ] Each indicator displays its source or data timestamp.
- [ ] Percentage changes use a defined reference period.
- [ ] Unavailable indicators display an unavailable state.
- [ ] Layout works on desktop and mobile.
- [ ] Dashboard does not rely on hardcoded market values.
- [ ] UI loading and error states are tested.

**Dependencies:** US-003, US-004

## US-006 — Stock and ETF Search

**User Story**

As an investor, I want to search stocks and ETFs by ticker or company name so that I can quickly open a security's research page.

**Implementation Requirements**

- Create a searchable instrument selector.
- Support US-listed stocks and ETFs.
- Search by ticker and company name.
- Display symbol, instrument name, exchange, and asset type.
- Support partial matching with debounced API requests.
- Route users to the instrument research workspace.

**Acceptance Criteria**

- [ ] A user can search by ticker.
- [ ] A user can search by company name.
- [ ] Duplicate ticker symbols across exchanges are distinguishable.
- [ ] Selecting a result opens the correct instrument.
- [ ] No-result and provider-error states display clearly.
- [ ] Search does not trigger a network request on every keystroke without debounce.

**Dependencies:** US-002, US-003

## US-007 — Personal Watchlist Management

**User Story**

As an investor, I want to maintain a watchlist so that I can monitor companies I may invest in.

**Implementation Requirements**

Create watchlist functionality with:

- Add instrument
- Remove instrument
- View watchlist
- Latest available price
- Percentage change
- Last updated timestamp

Persist watchlist data in PostgreSQL.

P0 may operate as a single-user local application, but ownership boundaries should allow future authentication.

**Acceptance Criteria**

- [ ] Instruments can be added and removed.
- [ ] Watchlist entries persist after restarting the application.
- [ ] Duplicate entries within a watchlist are prevented.
- [ ] Watchlist items open their research pages.
- [ ] Missing market data does not remove an instrument.
- [ ] API endpoints enforce the configured local-user ownership boundary.

**Dependencies:** US-003, US-006

---

# EPIC 03 — Stock Research Workspace

**Objective:** Centralize company information, prices, and financial research.

## US-008 — Company Overview

**User Story**

As an investor, I want to understand a company's business and major financial characteristics so that I can evaluate whether it deserves further research.

**Implementation Requirements**

Create an instrument research page displaying available:

- Company name and ticker
- Business description
- Sector and industry
- Exchange and reporting currency
- Latest available price
- Market capitalization
- Shares outstanding
- Basic financial ratios

Show data origin and retrieval time.

**Acceptance Criteria**

- [ ] Company details load from normalized data services.
- [ ] Missing fields are represented explicitly.
- [ ] Ratios are not invented when required inputs are unavailable.
- [ ] The page supports stocks and identifies ETFs separately.
- [ ] Price, market cap, and other market-sensitive data show relevant timestamps.
- [ ] Company metadata remains accessible when the quote provider fails.

**Dependencies:** US-002, US-003, US-006

## US-009 — Historical Price Chart

**User Story**

As an investor, I want an interactive historical stock chart so that I can study price movements and trends.

**Implementation Requirements**

Use TradingView Lightweight Charts to support:

- Candlestick chart
- Line chart
- Daily OHLCV
- Volume histogram
- Date ranges: 1M, 3M, 6M, 1Y, 5Y, MAX
- Optional moving averages: MA20, MA50, MA200

Calculate moving averages from appropriate historical data.

Display adjusted/unadjusted price semantics.

**Acceptance Criteria**

- [ ] Historical chart renders successfully.
- [ ] Changing the selected date range updates the chart.
- [ ] Users can switch between candlestick and line views.
- [ ] Moving averages are mathematically correct.
- [ ] Chart handles missing trading dates.
- [ ] Data is not interpolated into fabricated trading sessions.
- [ ] The configured chart library's licensing and attribution requirements are followed.

**Dependencies:** US-003, US-008

## US-010 — Financial Statements Workspace

**User Story**

As an investor, I want to examine company financial statements so that I can evaluate profitability, growth, and financial strength.

**Implementation Requirements**

Provide three financial statement views:

- Income Statement
- Balance Sheet
- Cash Flow Statement

Support annual and quarterly periods.

Include key metrics such as:

- Revenue
- Gross profit
- Operating income
- Net income
- Cash and equivalents
- Total assets
- Total liabilities
- Operating cash flow
- Capital expenditure
- Free cash flow

Store filing source, fiscal period, period type, and publication timestamp where available.

**Acceptance Criteria**

- [ ] Users can switch between income statement, balance sheet, and cash flow.
- [ ] Annual and quarterly financials are distinguishable.
- [ ] Monetary units and currencies are clearly displayed.
- [ ] Missing figures are not substituted with zeros.
- [ ] Period-over-period growth calculations use comparable periods.
- [ ] Financial statement data is traceable to its source.
- [ ] Financial figures are checked against fixture-based test cases.

**Dependencies:** US-002, US-004, US-008

---

# EPIC 04 — Financial and Valuation Engine

**Objective:** Provide reliable deterministic calculations to support investment analysis.

## US-011 — Financial Ratio Calculation Engine

**User Story**

As an investor, I want financial ratios calculated consistently so that I can compare company performance and valuation.

**Implementation Requirements**

Implement a Python financial calculation module covering:

- Revenue growth
- Gross margin
- Operating margin
- Net margin
- ROE
- ROIC, where inputs and methodology are available
- P/E
- P/S
- EV/EBITDA
- Free cash flow
- Free cash flow margin

Record calculation definitions and input periods.

Explicitly handle zero, negative, unavailable, and noncomparable values.

**Acceptance Criteria**

- [ ] Formulas are implemented in pure, independently testable functions.
- [ ] Input financial periods and units are preserved.
- [ ] Invalid denominators produce appropriate undefined results.
- [ ] Ratios are calculated from consistent reporting periods.
- [ ] No LLM is used for arithmetic.
- [ ] Golden test cases verify expected numerical output.

**Dependencies:** US-010

## US-012 — Valuation Scenario Simulator

**User Story**

As an investor, I want to change valuation assumptions so that I can understand how different growth and profitability scenarios affect a company's estimated value.

**Implementation Requirements**

Create an interactive valuation calculator supporting:

- Base revenue
- Revenue growth assumptions
- Profit margin assumptions
- Forecast horizon
- Exit P/E multiple
- Shares outstanding
- Discount rate

Support Bear, Base, and Bull scenarios.

Calculate forecast revenue, net income, terminal equity value, discounted present equity value, and implied value per share.

Expose scenario assumptions and calculations through a backend service.

Clearly identify this as a simplified earnings-multiple valuation model rather than a full DCF.

**Acceptance Criteria**

- [ ] Users can edit scenario assumptions.
- [ ] Calculations update when assumptions change.
- [ ] All valuation calculations execute in the backend.
- [ ] Currency and units are explicit.
- [ ] Invalid inputs are rejected.
- [ ] Missing or invalid share counts prevent per-share valuation.
- [ ] Results identify the forecast horizon and discounting convention.
- [ ] Reference test cases verify valuation formulas.

**Dependencies:** US-011

## US-013 — Valuation Scenario Persistence

**User Story**

As an investor, I want to save valuation assumptions so that I can compare analyses over time without losing my original investment reasoning.

**Implementation Requirements**

Create persistent valuation scenarios linked to an instrument.

Save:

- Scenario name
- Instrument
- Assumptions
- Calculation methodology version
- Output metrics
- Creation timestamp
- Underlying financial-data reference or snapshot

Support listing, opening, editing, and recalculating scenarios.

Preserve historical results when underlying data changes.

**Acceptance Criteria**

- [ ] Scenarios persist after application restart.
- [ ] Saved assumptions can be reloaded.
- [ ] Historical results can be reproduced using their stored inputs and calculation version.
- [ ] Recalculation does not silently overwrite the previous result.
- [ ] A company may have multiple valuation scenarios.
- [ ] Scenario names and input values are validated.

**Dependencies:** US-012

---

# EPIC 05 — AI Research Agent v1

**Objective:** Enable evidence-based company research using structured tools.

## US-014 — AI Agent Runtime and Tool Registry

**User Story**

As an investor, I want an AI research assistant that can retrieve and analyze verified financial data so that I can research companies more efficiently.

**Implementation Requirements**

Implement a LangGraph-based research agent with a defined state schema.

Create a tool registry with typed input/output schemas.

Initial tools:

- `get_market_snapshot`
- `get_historical_prices`
- `get_company_profile`
- `get_financial_statements`
- `get_financial_ratios`
- `calculate_valuation`
- `get_company_filings`, when a suitable source is configured

The agent must use these tools rather than relying on model memory for current financial figures.

Persist research-run status and tool execution traces.

Set maximum tool calls, execution timeout, and configurable token budget.

**Acceptance Criteria**

- [ ] Agent can execute an instrument research request.
- [ ] Tool calls use validated schemas.
- [ ] Numeric figures originate from tools or explicitly labeled calculations.
- [ ] Tool failures are visible in the execution result.
- [ ] Agent can report insufficient data rather than fabricate answers.
- [ ] Research runs record timestamps and tool execution metadata.
- [ ] Agent cannot call unauthorized tools or trading functions.
- [ ] Mocked end-to-end agent tests are repeatable.

**Dependencies:** US-008, US-010, US-011, US-012

## US-015 — AI Company Research Workflow

**User Story**

As an investor, I want the AI agent to analyze a company's business, financial health, valuation, and risks so that I can make a better-informed investment decision.

**Implementation Requirements**

Create a guided research workflow:

1. Interpret user request.
2. Identify instrument.
3. Determine necessary data.
4. Retrieve financial and market information.
5. Calculate relevant ratios.
6. Evaluate valuation assumptions.
7. Analyze business strengths and growth drivers.
8. Evaluate downside risks and contradictory evidence.
9. Generate a structured research result.

The structured result must include:

- Company summary
- Financial performance
- Growth drivers
- Valuation analysis
- Bull case
- Bear case
- Major risks
- Key uncertainties
- Missing information
- Source references

The agent must distinguish sourced facts, calculated outputs, and model interpretation.

**Acceptance Criteria**

- [ ] User can submit a natural-language research question.
- [ ] Agent executes relevant financial-data tools.
- [ ] Output follows a defined structured schema.
- [ ] Important numerical claims are linked to evidence.
- [ ] Bull and Bear cases are both represented.
- [ ] Missing data is clearly identified.
- [ ] Agent does not present unsupported price targets.
- [ ] Failures produce meaningful diagnostic information.

**Dependencies:** US-014

## US-016 — AI Research Interface and Execution Status

**User Story**

As an investor, I want to submit research questions and view execution progress so that I can understand what the AI agent is doing.

**Implementation Requirements**

Create an AI Research interface with:

- Prompt input
- Selected instrument context
- Start research action
- Research execution status
- Tool execution progress
- Final structured response
- Error and retry controls

Use persisted backend jobs so research continues independently of the browser connection.

Define run states such as:

- QUEUED
- RUNNING
- COMPLETED
- FAILED
- CANCELLED

Provide polling or server-sent events for progress updates.

**Acceptance Criteria**

- [ ] User can initiate a research task.
- [ ] UI receives a research-run identifier.
- [ ] Run state is displayed accurately.
- [ ] Refreshing the browser does not lose persisted run status.
- [ ] Completed results can be retrieved using the run identifier.
- [ ] Failed jobs show an actionable error state.
- [ ] Duplicate submissions are controlled.
- [ ] Backend timeouts and cancellation behavior are tested.

**Dependencies:** US-014, US-015

---

# EPIC 06 — Research Reports and Source Citations

**Objective:** Make AI research auditable, reusable, and accessible.

## US-017 — Research Evidence and Citation Validation

**User Story**

As an investor, I want to verify the sources behind AI-generated claims so that I can judge whether the research is trustworthy.

**Implementation Requirements**

Create structured evidence records linked to research runs.

Each evidence record should contain, where applicable:

- Evidence identifier
- Source provider
- Source document or endpoint
- Source URL
- Instrument identifier
- Reporting period
- Publication timestamp
- Retrieval timestamp
- Referenced metric or text passage

Create validation rules connecting report claims to source evidence.

Do not fabricate links, source names, or publication dates.

**Acceptance Criteria**

- [ ] Research claims can reference persisted evidence records.
- [ ] Major financial figures can be traced to input data.
- [ ] Citations link to the correct underlying evidence.
- [ ] Missing evidence is explicitly indicated.
- [ ] Citation validation flags missing or invalid references.
- [ ] A source's existence is not treated as proof that it supports an unrelated claim.
- [ ] Tests cover valid, missing, and mismatched evidence.

**Dependencies:** US-015

## US-018 — Research Report Persistence and History

**User Story**

As an investor, I want to save and reopen AI research reports so that I can compare findings over time.

**Implementation Requirements**

Persist:

- Research question
- Instrument
- Generated report
- Structured findings
- Linked evidence
- Valuation scenario references
- Model identifier
- Workflow version
- Research timestamp
- Research-run status

Create report list and detail pages.

Support searching or filtering by instrument and creation date.

Reports must retain historical content rather than silently change when source data is updated.

**Acceptance Criteria**

- [ ] Completed reports persist in PostgreSQL.
- [ ] User can open previous research reports.
- [ ] Reports display original research dates.
- [ ] Linked evidence remains accessible.
- [ ] Report history survives application restart.
- [ ] Reports are not silently regenerated during viewing.
- [ ] Basic report filtering works.
- [ ] Persistence and retrieval are covered by integration tests.

**Dependencies:** US-016, US-017

---

# 2. Implementation Order

Implement stories in dependency order.

### Foundation

1. US-001 — Project Initialization
2. US-002 — Financial Data Provider Abstraction
3. US-003 — Instrument Registry and Historical Prices
4. US-004 — Data Quality and Freshness

### Core Application

5. US-006 — Stock and ETF Search
6. US-008 — Company Overview
7. US-009 — Historical Price Chart
8. US-005 — Market Dashboard
9. US-007 — Watchlist
10. US-010 — Financial Statements

### Financial Analysis

11. US-011 — Financial Ratio Engine
12. US-012 — Valuation Scenario Simulator
13. US-013 — Valuation Persistence

### AI Research

14. US-014 — Agent Runtime and Tool Registry
15. US-015 — Company Research Workflow
16. US-016 — Agent Research Interface

### Evidence and Reports

17. US-017 — Research Evidence and Citations
18. US-018 — Report Persistence and History

Treat this sequence as the default dependency order. Independent UI and testing work may be implemented in parallel.

---

# 3. Definition of Done

Every user story is complete only when:

- The implementation meets its acceptance criteria.
- Relevant API contracts are defined.
- Database schema changes include migrations.
- Business logic includes automated tests.
- UI features handle loading, empty, and error states.
- Financial outputs preserve provenance and units.
- No secrets or API keys are hardcoded.
- Existing tests continue to pass.
- Relevant documentation is updated.
- Codex provides a short implementation summary, changed-file list, and test results.

Do not mark a story complete when required tests are failing.

---

# 4. Instructions for Codex

You are implementing InvestmentOS, a personal AI-powered investment research platform.

Follow these rules:

1. Read the repository structure and current implementation before making changes.
2. Identify the selected user story and its dependencies.
3. Never assume that another story has already been implemented.
4. Write a brief implementation plan before significant changes.
5. Implement only the selected story and essential dependencies.
6. Prefer reusable, maintainable code over shortcuts.
7. Avoid unrelated refactoring.
8. Do not replace verified financial figures with fabricated sample values in production code.
9. Use fixtures and mocks for deterministic tests.
10. Include database migrations for schema changes.
11. Run applicable automated tests and report their actual results.
12. Explain any acceptance criterion that cannot be completed.
13. Do not claim completion without verification.
14. Do not introduce live order execution or brokerage trading features.

When a story depends on an external provider that is unavailable, implement the provider contract and test fixtures, then explicitly report the blocked live integration.

**Recommended execution pattern:**

- Read the relevant user story.
- Inspect the current repository.
- Review dependencies.
- Propose a concise implementation plan.
- Implement the story.
- Add or update tests.
- Run checks.
- Summarize changed files, results, and remaining limitations.

**First task:** Start with US-001 — Project Initialization. Do not implement the entire P0 backlog in a single coding session.