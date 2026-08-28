# Report 270 — HEARTBEAT-06: GracePeriodInterceptor blocks on suspended-grace too

## 1. Identification
- **Report number:** 270
- **Task ID:** HEARTBEAT-06 (sixth of the 7-task License Heartbeat plan, `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor Task:** HEARTBEAT-05 (report 269)
- **Branch:** `feat/hub-license-heartbeat`

## 2. Objective
Make the Hub-side `GracePeriodInterceptor` also block order/payment-creating writes once the
suspended-license grace window (`LicenseService.isSuspendedGraceExpired`, HEARTBEAT-02) has
lapsed, returning a distinct `license_suspended` JSON body — while keeping the existing
`license_grace_period_expired` body for the no-cloud-contact case.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java`
- `backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java`

## 4. What Changed?
- **`GracePeriodInterceptor.preHandle`:** after loading `HubState`, a new first check runs
  `licenseService.isSuspendedGraceExpired(state)`; when true it writes
  `{"error":"license_suspended","message":"La licencia de Ember Hub está suspendida. Contacta a
  Vanter para reactivarla."}` with HTTP 403 and returns `false`. The pre-existing
  `isWithinGracePeriod` branch (403 `license_grace_period_expired`) is unchanged and now runs
  only when the license is not suspended.
- **`GracePeriodInterceptorTest`:** +2 tests in the file's existing local-`mock()` style
  (no `@ExtendWith(MockitoExtension.class)`, so unstubbed `isSuspendedGraceExpired` returns
  `false` for the two prior tests — no edit needed there):
  - `preHandle_suspendedGraceExpired_blocksWithSuspendedMessage` — 4-arg `HubState` with
    `suspendedSince` 3 days ago, `isWithinGracePeriod`→true, `isSuspendedGraceExpired`→true;
    asserts `false` / 403 / body contains `license_suspended`.
  - `preHandle_notSuspendedAndWithinGrace_proceeds` — healthy 4-arg `HubState`
    (`suspendedSince=null`), both booleans driven; asserts `true`.

## 5. Why It Changed?
Spec §4.4: a heartbeat that returns `SUSPENDED` stamps `hub-state.json`'s `suspendedSince`, and
after `EMBER_HUB_SUSPENDED_GRACE_HOURS` (default 48h) of continuous suspension the Hub must stop
accepting new order/payment writes — the same enforcement point already used for the 4-day
no-contact grace. A separate error code lets the frontend show suspension-specific copy
("contacta a Vanter") rather than the connectivity message, since the remedy differs.

## 6. Verification
- `./mvnw test -Dtest=GracePeriodInterceptorTest` — RED confirmed before implementation
  (`preHandle_suspendedGraceExpired...` expected false, was true), GREEN after: 4/4 PASS.
- Full backend suite: `./mvnw test` — **891/891 PASS**, 0 failures / 0 errors (889 prior + 2 new).
