# Report 326 — fix: coerce ws(s):// WebSocket URL to http(s):// for SockJS

## 1. Identification
- **Report number:** 326
- **Task ID:** fix-ws-url-scheme-sockjs (out-of-band fix surfaced during HPD-20 verification)
- **Predecessor task:** report 325 (HPD-16 — GCS media bucket)
- **Branch:** `main`

## 2. Objective
The live SPA at `https://app.ember.vanter.net` threw
`SyntaxError: The URL's scheme must be either 'http:' or 'https:'. 'wss:' is not
allowed.` on every WebSocket connect attempt, so realtime (cart sync, KDS push,
cash-shift sentinel) never connected. Make the client tolerant of a
`ws://`/`wss://` value.

## 3. Modified Files
- `frontend/src/store/websocket.ts`

## 4. What Changed?
`connect()` builds `wsUrl` from `window.ENV?.EMBW_WS_URL` (written into
`dist/env-config.js` by `scripts/gen-env-config.mjs` from the Cloudflare Worker
build var), falling back to `import.meta.env.VITE_WS_URL`, then
`http://localhost:8080/v1/ws`. That value is handed straight to `new SockJS(...)`.

SockJS negotiates the WebSocket upgrade itself and **requires an `http(s)://`
URL** — a `wss://` value makes its constructor throw. The `ember-app` Worker's
`EMBW_WS_URL` build var was set to `wss://api.ember.vanter.net/v1/ws` (HPD-18),
which is the natural-looking but wrong scheme for this client.

Fix: `.replace(/^ws(s?):\/\//i, 'http$1://')` on the resolved URL —
`wss://` → `https://`, `ws://` → `http://`, `http(s)://` untouched. A
mis-set env var now self-heals instead of breaking all realtime.

The Worker build var should still be corrected to
`https://api.ember.vanter.net/v1/ws` for correctness; this change means a future
recurrence degrades to a no-op rather than an outage.

## 5. Why It Changed?
Discovered while verifying HPD-20 (first real login against the hosted stack).
The `wss://` scheme error is unrelated to HPD-20's secret wiring — it is an
HPD-18 frontend-deploy config issue. Coercing in the client is the robust fix
because SockJS's scheme requirement is invariant; no caller should ever need to
know it.

Not touched: the `GET /v1/cash-shifts/current 404` console noise (that endpoint
returns 404 when no cash shift is open — expected on a fresh restaurant, the
`CashShiftSentinel` handles it) and the `Missing Description for DialogContent`
a11y warning (pre-existing, harmless).

## 6. Verification
- `cd frontend && pnpm run build` → `✓ built in 8.77s`, 0 TypeScript errors.
- `pnpm run lint` → 0 errors, 17 warnings (unchanged baseline, report 260).
- `pnpm run test:run` → **41/41 passed**.
- Live confirmation (the SPA no longer throwing the scheme error, realtime
  connecting) lands when the `ember-app` Worker rebuilds from `main`.
