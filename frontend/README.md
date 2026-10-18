# Investment OS frontend

An independent Next.js App Router / TypeScript application with Tailwind CSS.
The initial terminal provides responsive navigation, an overview, empty watchlist,
valuation, and research pages, and an on-demand connection check to the Kotlin backend.
Financial data, saved watchlists, valuation calculations, and agents are not implemented.

## Local setup

Requires Node 24 and npm. If you use nvm, activate the project's runtime first:

```sh
cd frontend
nvm install
nvm use
npm ci
npm run dev
```

Open <http://127.0.0.1:3000>. With an existing Node 24 installation, skip the nvm
commands. `.npmrc` rejects unsupported Node versions. If `node --version` still
reports an older runtime, run `nvm use` in the same terminal or select Node 24 as
the Node interpreter in IntelliJ. The development and production servers bind to
loopback by default.

The UI runs without the backend, PostgreSQL, API keys, or remote font downloads.
To enable the connection check, start the backend using its own
[setup guide](../backend/README.md). Its health endpoint includes database health.
The default backend origin is `http://127.0.0.1:8080`. To change it:

```sh
cp .env.example .env.local
# Edit BACKEND_URL, then restart Next.js.
```

`BACKEND_URL` must be an HTTP(S) origin without credentials, a path, or query
parameters. It is server-only configuration; keep provider credentials out of
`NEXT_PUBLIC_*` variables, which are exposed to the browser.

## Checks and production build

```sh
npm run lint
npm run format:check
npm run typecheck
npm test
npm run build
npm start
```

Commit `package-lock.json` and use `npm ci` for repeatable installation.
Lint runs explicitly; the Next.js build is not a replacement for the lint command.
Use `npm run format` to apply Prettier formatting.
In restricted execution environments, Turbopack's CSS worker may fail with
`binding to a port: Operation not permitted`. Use the supported Webpack fallback:

```sh
npm run build -- --webpack
# For development in the same restricted environment:
npm run dev -- --webpack
```

The default scripts retain Next.js's normal Turbopack behavior. A local preview
server itself also needs permission to listen on its loopback port.

`typecheck` generates route types before running TypeScript, so it works on a
fresh checkout. Vitest / React Testing Library tests use mocked network responses
and follow the `GIVEN … WHEN … THEN …` naming and comment convention.

## Main code paths

- `src/app/layout.tsx`: shared terminal shell and navigation.
- `src/app/page.tsx`: overview and honest unavailable-data states.
- `src/components/backend-status.tsx`: idle, loading, success, failure, and retry UI.
- `src/app/api/backend/health/route.ts`: server-side health proxy to Spring Actuator.
- `src/app/globals.css`: dark palette, responsive layout, and keyboard focus styles.

Pages and the shared shell are Server Components by default. Only navigation and
the interactive connection panel are Client Components. The browser requests
`/api/backend/health` on the same origin; Next.js calls `/ready` on the
configured backend, so no browser CORS configuration is required for this check.
The proxy has a three-second timeout, disables caching and redirects, validates
the response, and returns only `connected` or `unavailable`. The client has a
five-second timeout and permits retry after failure. Backend connection success
does not imply that a market data provider is configured.

The backend remains responsible for business logic and deterministic financial
calculations. Next.js currently hosts only presentation and the health proxy.
Future financial responses must retain provenance, currency, source timestamps,
exchange, and freshness. Missing values stay missing; no fixture prices are shown.

The tests cover health response validation, network/timeout failure mapping,
connection UI transitions and retry, and accessible active navigation. They do
not exercise a live backend. The root Compose smoke check exercises real
frontend-to-backend connectivity; see the [full-stack setup](../README.md#full-stack-local-setup).
Market APIs remain separate work. The Docker build sets `BUILD_STANDALONE=true`
to package a standalone production Node server. Host builds keep the standard
output used by `npm start`. The backend origin is read at runtime.

The dependency audit currently reports a development-tool advisory in `braces`,
pulled in by Next.js's ESLint configuration through `fast-glob` and `micromatch`.
The [upstream advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm) lists no
patched version. These tools process repository lint patterns, not request input.
Track the upstream fix rather than forcing npm's suggested downgrade to Next 14's
lint configuration. Check runtime dependencies separately with `npm audit --omit=dev`.
ESLint remains on version 9 for compatibility with the React/import/accessibility
plugins bundled by Next's configuration; migrate to ESLint 10 when those peers support it.

The [Next.js installation guide](https://nextjs.org/docs/app/getting-started/installation)
and [Vitest guide](https://nextjs.org/docs/app/guides/testing/vitest) describe the
framework setup. To explore the client/server boundary, stop the backend and run
the connection check, then restart it and retry from the same page.
