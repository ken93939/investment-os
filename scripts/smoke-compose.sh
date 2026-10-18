#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# COMPOSE_PROJECT_NAME and COMPOSE_ENV_FILE allow checking an isolated test stack.
docker compose --env-file "${COMPOSE_ENV_FILE:-.env}" exec -T frontend node --input-type=module <<'JS'
import assert from 'node:assert/strict';

for (const path of ['/', '/watchlist', '/valuation', '/research']) {
  const response = await fetch(`http://127.0.0.1:3000${path}`, {signal: AbortSignal.timeout(5000)});
  assert.equal(response.status, 200, path);
}
for (const path of ['/health', '/ready']) {
  const response = await fetch(`${process.env.BACKEND_URL}${path}`, {signal: AbortSignal.timeout(5000)});
  assert.equal(response.status, 200, path);
  assert.equal((await response.json()).status, 'UP');
}
const proxy = await fetch('http://127.0.0.1:3000/api/backend/health', {signal: AbortSignal.timeout(5000)});
assert.equal(proxy.status, 200);
assert.deepEqual(await proxy.json(), {status: 'connected'});
console.log('PASS: frontend pages, backend liveness/readiness, and frontend-to-backend connectivity');
JS
