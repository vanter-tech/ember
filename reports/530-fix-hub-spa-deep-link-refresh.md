# Report 530 — Hub web app broke on F5 (blank page, "Failed to fetch")

## 1. Identification
- **Report number:** 530
- **Task ID:** FIX-HUB-SPA-DEEP-LINK
- **Predecessor:** report 529 (Hub 0.2.9 bump)

## 2. Objective
Testing Hub 0.2.9: pressing F5 on any inner page left the web app blank, slow to come back, and every request failed ("Failed to fetch").

## 3. Modified Files
- `ember-hub/build-frontend.ps1`
- `frontend/index.html` (comment only)
- `PROGRESS.md`, `reports/530-fix-hub-spa-deep-link-refresh.md`

## 4. What Changed?
**Root cause (reproduced against the running Hub, not guessed).** `index.html` loads `env-config.js` with a *relative* `src` (required so `vite build --base=/app/` works). At `/app/` that resolves to `/app/env-config.js`, but on a reload of an inner route such as `/app/waiter/tables` the browser asks for `/app/waiter/env-config.js`; `HubWebConfig`'s SPA fallback answers any unknown path under `/app/` with `index.html`, so the "script" is HTML (`text/html`), never runs, and `window.ENV` stays undefined. The API client then falls back to the build-time `http://localhost:8080/v1` — a `/v1` prefix the Hub does not have, or another PC's `localhost` when the page is opened by IP — so every call fails. The cloud had the same defect and already fixed it in `frontend/scripts/gen-env-config.mjs`; the Hub's frontend script (`ember-hub/build-frontend.ps1`) never got the equivalent.

**Fix.** After copying `dist/` into `static/`, `build-frontend.ps1` pins the tag to `src="/app/env-config.js"` (UTF-8 without BOM), and throws if the expected tag is not found so a future `index.html` change cannot silently reintroduce the bug. The `index.html` comment now points at both scripts.

## 5. Why It Changed?
A hard refresh (or a bookmark) on any page except the root must work; a wrong API base turns the whole app into "Failed to fetch".

Verification: rebuilt `EmberHubSetup-0.2.9.exe` (18:36); the `index.html` packaged inside `ember-hub.jar` contains `<script src="/app/env-config.js">`. A first rebuild aborted on a PowerShell parse error in the new guard message (`"$indexPath: …"` reads as a drive-qualified variable), which is why it is now `${indexPath}`; the guard failed loudly and produced no installer. **Not yet verified in the running app**: install the rebuilt Hub and press F5 on an inner route. No automated test: the change is in the build script (the packaged file was inspected instead).

Same version (0.2.9) on purpose: it has not been tagged or published.
