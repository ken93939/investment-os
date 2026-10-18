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
