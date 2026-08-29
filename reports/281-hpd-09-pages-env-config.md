# Report 281 — HPD-09: generate `env-config.js` for the Cloudflare Pages build

## 1. Identification
- **Report number:** 281
- **Task ID:** HPD-09 (Hosted Production Deployment plan, `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md`, Phase 2 Task 9)
- **Predecessor task:** HPD-08 (report 280 — CI backend image workflow)
- **Branch:** `feat/hosted-production-deployment`

## 2. Objective
Give the Cloudflare Pages build a way to emit the runtime `window.ENV` contract the SPA
already expects (`EMBW_API_URL` / `EMBW_WS_URL`). Docker/dev produce that file at container
start via `frontend/entrypoint.sh`; Pages has no entrypoint, so the values must be baked
into `dist/env-config.js` during the build from Pages project env vars.

## 3. Modified Files
- `frontend/scripts/gen-env-config.mjs` — **new**
- `frontend/package.json` — added `build:pages` script

## 4. What Changed?
- **`frontend/scripts/gen-env-config.mjs` (new):** a tiny Node ESM script. Reads
  `process.env.EMBW_API_URL` / `process.env.EMBW_WS_URL`, falling back to the same dev
  defaults `entrypoint.sh` uses (`http://localhost:8080/api/v1`, `http://localhost:8080/v1/ws`).
  Writes `dist/env-config.js` containing
  `window.ENV = { EMBW_API_URL: "...", EMBW_WS_URL: "..." };` (values JSON-stringified so
  quoting/escaping is safe) and logs the resolved pair. Not imported by any app code.
- **`frontend/package.json`:** new `"build:pages": "tsc -b && vite build && node scripts/gen-env-config.mjs"`
  script, placed next to `build:hub`. `build` and `build:hub` are untouched.

## 5. Why It Changed?
The React SPA resolves its API/WS base URLs at runtime from `window.ENV` (populated by
`/env-config.js`, which `index.html` loads before the bundle). The Docker image writes that
file on boot from container env; the Cloudflare Pages deployment (spec §6) is a static
upload with no boot step, so the file has to be generated at build time on the Pages runner,
where `EMBW_API_URL` / `EMBW_WS_URL` are configured as project environment variables. A
dedicated `build:pages` script keeps this path separate from the plain `build` (used by
`pnpm run build` guard rails and any non-Pages consumer) and from `build:hub` (which serves
its own `env-config.js` computed client-side from `window.location`).

## 6. Verification
- `EMBW_API_URL=https://api.ember.vanter.net/v1 EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws pnpm run build:pages`
  → build OK; `frontend/dist/env-config.js` contains the two `https://` / `wss://` values.
- `pnpm run build` → OK (unchanged).
- `pnpm run test:run` → **41/41 passed** (12 files), unchanged.
- `pnpm run lint` → exit 0, 0 errors / 17 warnings (unchanged baseline, report 260).
- Backend suite not run — no backend files touched (baseline stays 900/900, report 275).
