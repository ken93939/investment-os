# Learning Agreement — Codex as Pair Programmer

## Objective
Learn to build, operate, evaluate, and debug AI agents while using Codex to accelerate routine application development.

## What the owner should understand
- Architecture: modules, API contracts, transactions, consistency and failure scenarios.
- Financial pipelines: source quality, timestamps, data lineage, adjusted prices and validation.
- Agent fundamentals: model calls, structured outputs, tool schemas, tool execution, orchestration, context management, guardrails, tracing and evaluation.
- Risk controls: prompt injection via external data, secrets, permissions, cost/latency limits and hallucinated numerical outputs.
- Verification: tests, deterministic metrics, traces, and evidence-based reports.

## Preferred teaching pattern after each substantial change
1. Explain the high-level data flow and identify 2–5 key files/functions to read.
2. Explain one or two nonobvious engineering decisions and the alternatives.
3. Identify key failure cases and the tests guarding them.
4. Give one short hands-on exercise for the owner (especially agent code).
5. Optionally quiz the owner on a failure scenario or design decision; do not quiz after every trivial change.

## Suggested progression (flexible)
1. Repository conventions and basic Kotlin backend.
2. Financial-data adapter, persistence and API.
3. First Python agent with a retrieval tool and deterministic calculation tool.
4. Integrate agent with Kotlin and UI.
5. Tracing, guardrails, eval cases, source citation, failure handling.
6. Broader research workflows and quantitative analysis.

## Skill workflow policy
- Try a comprehension skill such as grill-me after notable implementation milestones.
- Use one planning workflow (e.g. pstack) for large features if compatible.
- Use code review/QA workflows (e.g. selected gstack capabilities) when valuable.
- Do not make progress contingent on third-party skills being installed.

## Definition of done
Feature works against specified acceptance criteria; tests and limitations are reported; owner can explain its main flow, tools, and failure modes.
