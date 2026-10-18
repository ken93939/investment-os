# Shared API contracts

`openapi.yaml` describes the Kotlin backend's operational endpoints. `/health`
reports process liveness; `/ready` also requires database connectivity. Both return
a status and withhold internal connection details. The frontend's same-origin
`/api/backend/health` route adapts readiness to its own UI status.

Add domain request/response schemas here as APIs are introduced. Keep their
implementation and contract updates together, with tests for observable behavior.
There is no shared runtime library or code generation until domain APIs require it.
