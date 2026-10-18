# Project Context — Personal Investment Research Platform

## Vision
Build a personal Bloomberg-inspired web application combining market data, portfolio and company analysis, research workflows, and AI agents. It should help the owner learn investing, analyze evidence, and improve decisions; it is not intended initially to place trades.

## Owner profile and working style
- Fluent Kotlin engineer; some Python experience, including data pipelines.
- Wants to learn AI-agent architecture and personally implement parts of it.
- Wants Codex to handle much of routine coding while retaining understanding and ownership of architectural decisions.
- Values correctness, maintainability, practical explanations, and learning through production-like features.

## Architecture direction (not immutable requirements)
- Next.js / TypeScript UI for dashboards and charts.
- Kotlin / Spring Boot API and core business logic.
- PostgreSQL storage for portfolios, prices, fundamentals, user configuration, and research history.
- Python for quant analytics and agent orchestration when useful; do not assume a separate Python service is needed in MVP.
- Market-data adapters with clear provider boundaries, source timestamps, normalization, caching, and quality checks.
- Start with a modular monolith and add queues/workers or distinct services only on demonstrated need.

## Functional areas (candidate roadmap)
1. Market data ingestion and normalized historical price retrieval.
2. Company overview, fundamentals, watchlists, and interactive charts.
3. Portfolio tracking, returns and risk metrics.
4. Evidence-grounded stock research assistant with controlled tool calls.
5. Advanced research: document retrieval, specialist agents where justified, evaluations, backtesting, screening.

## First vertical slice
Build a stock research assistant that can:
- Answer a research question about a selected company.
- Retrieve authentic, timestamped market/fundamental records via explicit tools.
- Call a deterministic calculation tool for ratios or metrics.
- Produce a structured report separating evidence, computed values, interpretation, and uncertainties.
- Handle absent, stale, or conflicting provider data without fabricating answers.
- Expose a simple UI and automated tests for the data tools and report schema.

## Nonfunctional priorities
- Evidence/provenance, security, reproducible results, sensible cost controls, observability, retry and timeout handling.
- Protect market-data licenses and API terms; choose providers based on reliability, coverage, licensing, and price.
- No automatic trade execution in the initial scope.

## Decisions not finalized
- Exact market-data provider(s), geographic/exchange coverage and subscription budget.
- Scope of portfolio imports and brokerage integrations.
- Hosting and deployment vendor.
- Whether to use Python agents in a separate service or initially call AI APIs directly from Kotlin.
- Specific skills/plugins to install; evaluate gstack, pstack and grill-me separately, including compatibility.

## Development philosophy
Codex should propose options and implement code; the owner reviews high-impact choices. Prefer pragmatic increments over framework-heavy design.
