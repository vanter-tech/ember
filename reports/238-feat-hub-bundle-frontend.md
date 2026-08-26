# Report 238 — feat-hub-bundle-frontend

## 1. Identification
- **Report number:** 238
- **Task ID:** feat-hub-bundle-frontend
- **Predecessor task:** report 237 (feat-hub-dashboard-launcher)

## 2. Objective
Bundle the compiled `frontend/` into the backend jar so the dashboard's "Abrir en navegador" button has something real to show, closing the gap left open by report 237.

## 3. Modified Files
- `backend/src/main/resources/application-hub.yml`
- `backend/src/main/java/com/vanter/ember/hub/config/HubWebConfig.java`
- `backend/src/main/java/com/vanter/ember/hub/config/HubSpaRootController.java` (new)
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`
- `backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java`
- `frontend/index.html`
- `frontend/src/App.tsx`
- `frontend/package.json`
- `ember-hub/build-frontend.ps1` (new)
- `.gitignore`

## 4. What Changed?
- **`application-hub.yml`**: `server.servlet.context-path: /`, overriding the base `application.yml`'s `/v1/` — only for the hub profile.
- **`HubWebConfig.addResourceHandlers`**: serves the bundled SPA from `classpath:/static/`, mounted at **`/app/**`** — deliberately NOT the root. `GET /kitchen/orders` (and others) is both a real protected KDS endpoint and a frontend route; mounting at root would force choosing between exposing that API unauthenticated or breaking the frontend route. `/app` never collides with any `@RequestMapping`. A custom `PathResourceResolver` falls back to `index.html` for any unresolved path under `/app/**`, so client-side routes resolve on direct navigation/refresh.
- **`HubSpaRootController`** (new): Spring's `ResourceHttpRequestHandler` rejects an empty resource path before the custom resolver's fallback ever runs, so the bare `/app`/`/app/` root 404'd even though deeper paths worked. This controller forwards (server-side, URL stays `/app/` in the browser — required for React Router's `basename` to see the root route) to `/app/index.html`.
- **`SecurityConfig`**: added `.requestMatchers("/app/**").permitAll()` — safe specifically because `/app` has zero collision with any real endpoint (see above).
- **`HubDashboard`/`HubTrayIcon`**: "Abrir en navegador"/"Abrir Ember Hub" now open `http://localhost:<port>/app/` instead of the bare root.
- **`frontend/index.html`**: `<script src="/env-config.js">` → `<script src="env-config.js">` (relative). Vite doesn't rewrite plain `<script src>` refs for `--base`, so the absolute form would keep requesting `/env-config.js` from the server root regardless of where the page itself was served from — breaking under `/app/`.
- **`frontend/src/App.tsx`**: `BrowserRouter` now takes `basename={routerBasename}`, derived from Vite's built-in `import.meta.env.BASE_URL` (trailing slash stripped, falsy → `undefined` for the default `/` build). The regular `pnpm run build` (cloud/dev) is unaffected — `BASE_URL` stays `/`.
- **`frontend/package.json`**: new `build:hub` script — `tsc -b && vite build --base=/app/`. The Hub build is the *only* thing that changes; the shipped bundle logic itself (routes, API client, etc.) is identical to the cloud build.
- **`ember-hub/build-frontend.ps1`** (new — first file in the `ember-hub/` packaging folder the spec already designated): runs `pnpm run build:hub`, copies `dist/*` into `backend/src/main/resources/static/`, writes `env-config.js` computing `window.ENV.EMBW_API_URL`/`EMBW_WS_URL` from `window.location.origin`/`.host` at page-load time — works regardless of which port `EMBER_HUB_SERVER_PORT` ends up on, no rebuild needed per port. Manual step for now (not wired into `mvnw package`); the bundled output is gitignored, same as `frontend/dist` itself.

## 5. Why It Changed?
The straightforward approach (serve the frontend from the root, matching the API's own root after the context-path override) turned out to be unsafe: `KitchenController`'s real `GET /kitchen/orders` endpoint has the exact same path as the frontend's KDS route. A root-mounted resource handler with a `permitAll` broad enough to let the SPA load would have also exposed that (and potentially other) protected API endpoints unauthenticated. Isolating the frontend under its own `/app` prefix — guaranteed collision-free since no controller uses it — was the only way to keep both working without a security trade-off.

## Verification
- `cd backend && ./mvnw test` — 807/807 PASS (three separate runs across the SecurityConfig/HubWebConfig/HubSpaRootController iterations, all green).
- `cd frontend && pnpm run test:run` — 36/36 PASS after the `App.tsx` change.
- `cd frontend && pnpm run build` (the *regular*, non-hub build) — still succeeds unchanged, confirming zero impact on the cloud/dev build path.
- **Live-verified end-to-end by the user, cross-checked from the server side at each step:**
  - `GET /app/` → 200, serves `index.html` with correctly `/app/`-prefixed asset references.
  - `GET /app/env-config.js` → 200, correct content.
  - `GET /app/admin/settings` (a client-side-only route, no matching file) → 200, SPA fallback confirmed.
  - `GET /kitchen/orders` (real protected API) → still 401, confirming no security regression from the `/app/**` permitAll.
  - Dashboard → Iniciar → Abrir en navegador → **user confirmed the real Ember UI renders correctly** (not a blank page, as it had been before the `/app` prefix fix).
  - Login attempt → 401. **Expected, not a bug**: this Hub's Postgres database is genuinely fresh with zero seeded users — confirms the request reached the real backend (not a network/CORS/404 failure).

## Known gap surfaced during this task (not fixed, needs its own design)
Attempting to log in surfaced a bigger, unscoped question: **there is currently no path from "a customer activates their Hub license" to "a Restaurant + admin User exist in the local database."** `LicenseKey`/`LicensePayload` (HUB-01-03) carry only `restaurantId` + `issuedAt` — no restaurant name, no admin email/password. Nothing in `LicenseService.validateOrActivate()` touches Postgres at all; it only writes the local `hub-state.json`. The user's platform-operator console (`/console`, EMB-PC) is presumably where a real restaurant + its cloud admin account get created today, but how that maps to (or gets re-created/synced into) a Hub's own local, offline Postgres instance is undesigned. Also flagged: the frontend's "Registrarse" button is the **customer** self-registration flow (EMB-i18N era `/auth/register`, tenant-less by design), not an admin-provisioning flow — showing it at all on a fresh Hub install is misleading and was called out as something that "no debería de existir" in this context. Needs a dedicated design session before any code changes — not started.
