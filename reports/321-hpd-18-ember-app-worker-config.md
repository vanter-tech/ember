# Report 321 — HPD-18 (repo side): `ember-app` Worker config for `app.ember.vanter.net`

## 1. Identification
- **Report number:** 321
- **Current Task ID:** HPD-18 (Cloudflare track — repo prerequisites)
- **Predecessor Task:** report 320 (task-D — leave table / session resume)

## 2. Objective
Prepare the repo so the React SaaS frontend can be deployed as its own
Cloudflare Worker (static assets) served at `app.ember.vanter.net`, separate from
the existing `ember` Worker that serves the Astro landing. The Cloudflare
dashboard steps (create the Worker, move the custom domain) and the Tunnel
(HPD-17) / secret wiring (HPD-20) remain to be executed by the operator.

## 3. Modified Files
- `frontend/wrangler.jsonc` — new
- `frontend/scripts/gen-env-config.mjs` — modified

## 4. What Changed?
- **`frontend/wrangler.jsonc` (new):** mirrors `landing/wrangler.jsonc`. Worker
  `name: "ember-app"`, `compatibility_date: "2026-08-31"`, static assets from
  `./dist`. `not_found_handling: "single-page-application"` — unlike the landing
  (`"404-page"`), the SPA needs every unknown path to fall back to `index.html`
  so React Router client routes (`/admin/...`, `/kds`, deep links) resolve.
- **`frontend/scripts/gen-env-config.mjs`:** the no-env fallback for `EMBW_API_URL`
  was `http://localhost:8080/api/v1`; every consumer (`src/lib/api.ts`,
  `platformApi.ts`, `store/websocket.ts`) uses the `/v1` prefix with no `/api`
  segment. Corrected the fallback to `http://localhost:8080/v1` so a build with no
  env vars produces a coherent `dist/env-config.js`. Production builds pass real
  values and are unaffected.

## 5. Why It Changed?
`app.ember.vanter.net` and `ember.vanter.net` currently both resolve to the single
`ember` Worker, which can serve only one `assets.directory` (the landing). A
Cloudflare static-assets Worker cannot host two sites; the SPA needs its own
Worker (`ember-app`) with its own `dist/` and SPA fallback. `build:pages`
(`tsc -b && vite build && node scripts/gen-env-config.mjs`) already existed from
HPD-09; this task only adds the Worker manifest it deploys with and fixes the
fallback URL.

## 6. Verification
```
cd frontend && EMBW_API_URL=https://api.ember.vanter.net/v1 \
  EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws pnpm run build:pages
```
`✓ built in 5.94s`; `dist/env-config.js` contains:
```
window.ENV = {
  EMBW_API_URL: "https://api.ember.vanter.net/v1",
  EMBW_WS_URL: "wss://api.ember.vanter.net/v1/ws"
};
```
`dist/index.html` loads `<script src="env-config.js">`. No frontend source
touched; test suite unaffected.

## 7. Operator follow-up (not in this commit)
- **Cloudflare Worker `ember-app`:** Workers → Create → Connect to Git → this
  repo. Root directory `frontend`, build command `pnpm run build:pages`, deploy
  command `npx wrangler deploy`. Build env vars:
  `EMBW_API_URL=https://api.ember.vanter.net/v1`,
  `EMBW_WS_URL=wss://api.ember.vanter.net/v1/ws`, `NODE_VERSION=20`.
- **Custom domain:** remove `app.ember.vanter.net` from the `ember` Worker, add it
  to `ember-app`. Leave `ember.vanter.net` on `ember`.
- **HPD-17 / HPD-20:** Tunnel `ember-prod` → `api.ember.vanter.net` → `app:8080`;
  put `TUNNEL_TOKEN` into `ember-prod-env`; `./deploy/deploy.sh v0.1.0`; smoke.
