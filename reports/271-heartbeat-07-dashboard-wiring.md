# Report 271 — HEARTBEAT-07: dashboard license status line + config docs + full verification

## 1. Identification
- **Report number:** 271
- **Task ID:** HEARTBEAT-07 (Task 7 of 7, `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor Task:** HEARTBEAT-06 (report 270 — `GracePeriodInterceptor` blocks on suspended-grace)
- **Branch:** `feat/hub-license-heartbeat`

## 2. Objective
Wire the License Heartbeat feature's user-facing surface: a read-only "Licencia: ..." status line on the Swing Hub dashboard, and document the three new `EMBER_HUB_HEARTBEAT_*` env vars plus `hub.latest-version` in `.env.example`. Close out the plan with a full backend suite run.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`
- `.env.example`
- `PROGRESS.md`
- `reports/271-heartbeat-07-dashboard-wiring.md` (new)

## 4. What Changed?
### `HubDashboard.java`
- New imports: `HubState`, `HubStateStore`, `java.time.Duration`, `java.time.Instant`.
- New field `stateStore = new HubStateStore(properties.stateFile())` — the dashboard already held `HubProperties`, so it constructs its own store (no shared bean exists; the dashboard runs before any Spring context).
- New `JLabel licenseStatusLabel` added to the status panel; `GridLayout(3, 1)` → `GridLayout(4, 1)`; frame height `190` → `215`.
- New `refreshLicenseStatus()` — reads `stateStore.load()` and sets the label via `licenseStatusText(HubState)`. Called at three points that already mutate the other status labels: constructor (initial paint), the `ApplicationReadyEvent` listener (server-ready), and `stopServicesInBackground` (after stop).
- New helpers `licenseStatusText(HubState)` / `humanizeSince(Instant)` (verbatim from the plan): `null` state → `"Licencia: sin estado local"`; `suspendedSince != null` → `"Licencia: SUSPENDIDA (desde hace <n>)"`; otherwise → `"Licencia: OK · último contacto hace <n>"`. `humanizeSince` buckets the elapsed `Duration` into `min` (< 60 min), `h` (< 48 h), or `d`.
- No test — Swing desktop paths are manually verified, consistent with `HubTrayIcon` (HUB-01-10) and HEARTBEAT-05's scheduler-only test scope.

### `.env.example`
- New "Ember Hub — license heartbeat (sub-project A1)" block after `HUB_LICENSE_PRIVATE_KEY=` (no prior `EMBER_HUB_*` block existed — this establishes it):
  - `EMBER_HUB_HEARTBEAT_URL=https://<cloud-host>/hub-heartbeat` (blank disables the scheduler)
  - `EMBER_HUB_HEARTBEAT_INTERVAL_MS=300000`
  - `EMBER_HUB_SUSPENDED_GRACE_HOURS=48`
  - `hub.latest-version=` (cloud-side; the version the `/hub-heartbeat` response advertises)

## 5. Why It Changed?
The heartbeat scheduler (HEARTBEAT-05) and suspended-grace enforcement (HEARTBEAT-02/06) run silently — an operator had no way to see, from the Hub's own launcher, whether the last cloud contact succeeded or whether the restaurant is suspended and counting down the 48 h courtesy grace. The dashboard line surfaces exactly that from `hub-state.json` with no Postgres dependency (readable even when Postgres won't start). The `.env.example` entries make the new configuration discoverable, matching the project rule that every new env var is documented there.

## 6. Verification
- `cd backend && ./mvnw test` → **891/891 PASS**, 0 failures / 0 errors, BUILD SUCCESS (~1:50). Unchanged from report 270 — this task adds no test (Swing) and no production logic path exercised by the suite.
- **Manual 2-process smoke (plan Task 7 Step 4): PENDING — requires the operator to run the report-256 cloud-on-:8081 + Hub-on-:8080 two-process setup.** Steps to run: (1) start Hub with `EMBER_HUB_HEARTBEAT_URL=http://localhost:8081/hub-heartbeat`, expect `Heartbeat OK.` in the log within ~1 min, `lastHeartbeatAt` advancing, dashboard showing `Licencia: OK · último contacto hace 0 min`; (2) `UPDATE restaurants SET status='SUSPENDED'` on the cloud DB → next cycle logs `SUSPENDED`, `hub-state.json` gains `suspendedSince`, dashboard flips to `Licencia: SUSPENDIDA ...`, writes still succeed inside 48 h; (3) `EMBER_HUB_SUSPENDED_GRACE_HOURS=0` + restart → `POST /sessions/*/items` returns 403 `license_suspended`; (4) `UPDATE ... status='ACTIVE'` → next cycle clears `suspendedSince`, writes work again.
- **PR to `main`: PENDING** — push `feat/hub-license-heartbeat`, open PR, wait for `lint-backend`/`lint-frontend`/`lint-gateway` green, squash-merge.

The License Heartbeat plan (HEARTBEAT-01..07) code is now COMPLETE on the branch.
