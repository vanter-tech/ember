# Report 366 — Hotfix: Cloudflare Pages `env-config.js` fails on deep links

## 1. Identification
- **Report number:** 366
- **Current Task ID:** HOTFIX — prod login broken on `app.ember.vanter.net` deep links (`net::ERR_CONNECTION_REFUSED` to `http://localhost:8080/v1/auth/login`)
- **Predecessor Task:** report 365 (PROGRESS.md compaction + security-debt list)

## 2. Objective
Stop the production SPA from falling back to `http://localhost:8080` for API calls when a
user opens or hard-refreshes any route other than `/`.

## 3. Modified Files
- `frontend/scripts/gen-env-config.mjs`
- `frontend/index.html` (comment only)

## 4. What Changed?
`gen-env-config.mjs` (run by `build:pages`) already wrote `dist/env-config.js` from the
`EMBW_API_URL` / `EMBW_WS_URL` build vars. It now also reads `dist/index.html` and rewrites
the config script tag from the relative `src="env-config.js"` to the absolute
`src="/env-config.js"`. The step throws if the relative marker is absent, so a future
`index.html` change cannot silently disable the fix.

`frontend/index.html`: extended the existing comment to note that the Pages build rewrites
the tag to an absolute path via `gen-env-config.mjs`. The source tag stays relative because
the Hub build (`vite build --base=/app/`) needs it that way.

Verification: `EMBW_API_URL=… EMBW_WS_URL=… pnpm run build:pages` — build succeeds,
`dist/index.html` ends with `<script src="/env-config.js"></script>`, `dist/env-config.js`
carries the prod URLs.

## 5. Why It Changed?
The API client resolves its base URL as
`window.ENV?.EMBW_API_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080/v1'`
(`frontend/src/lib/api.ts:129`, same in `platformApi.ts:83`).

`index.html` loads `window.ENV` with a **relative** `<script src="env-config.js">`. The
`ember-app` Cloudflare Worker serves the SPA from the domain root with
`not_found_handling: "single-page-application"` (`frontend/wrangler.jsonc`). On any deep
link or hard refresh — `/login`, `/kitchen`, `/admin`, `/console/...` — the browser
requests `env-config.js` **relative to that route** (`GET /login/env-config.js`), which
does not exist, so the Worker returns `index.html` (HTML, `200`). The `<script>` body is
then `<!doctype html>` → JS SyntaxError → `window.ENV` is never defined →
`import.meta.env.VITE_API_URL` is not baked into this build (the prod URL only lives in
`env-config.js`) → the client falls back to `http://localhost:8080/v1`. Nothing listens on
the visitor's own `:8080`, so `POST http://localhost:8080/v1/auth/login` fails with
`net::ERR_CONNECTION_REFUSED`. Loading the bare root `/` worked because `env-config.js`
then resolved to the real file.

Pinning the tag to `/env-config.js` makes every route load the real asset from the root,
so `window.ENV` is always defined before the bundle initialises.
