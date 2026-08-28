# Report 266 — HEARTBEAT-02: LicenseService suspended-grace logic + HubProperties heartbeat fields

## Identification
- **Report number:** 266
- **Task ID:** HEARTBEAT-02 (Task 2 of `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor task:** HEARTBEAT-01 (report 265 — `HubState.suspendedSince` + state-store round-trip)

## Objective
Give `LicenseService` the ability to evaluate a cloud-driven suspension against a courtesy grace
window, and give `HubProperties` the two new config values the heartbeat feature needs
(`heartbeatUrl`, `suspendedGraceHours`) without breaking existing positional constructor callers.

## Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`
- `backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java`
- `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`

## What Changed?
- **`LicenseService`** — added a `Duration suspendedGrace` field and made it the 6th constructor
  argument (the old 5-arg constructor is removed). Added two methods:
  - `isSuspendedGraceExpired(HubState)` → `true` only when `suspendedSince != null` and the time
    since then exceeds `suspendedGrace`.
  - `recordSuspended(HubState)` → stamps `suspendedSince = now()` and persists via `stateStore.save`
    the first time; a subsequent call with an already-set `suspendedSince` returns the state
    unchanged and does **not** re-persist, so the courtesy-grace counter never resets mid-suspension.
- **`HubProperties`** — appended `String heartbeatUrl` and `int suspendedGraceHours` to the record
  header and to `fromEnvironment()` (reading `EMBER_HUB_HEARTBEAT_URL`, default `""`, and
  `EMBER_HUB_SUSPENDED_GRACE_HOURS`, default `"48"`). Added a back-compat constructor with the
  previous 11-arg signature that delegates with `heartbeatUrl = ""`, `suspendedGraceHours = 48` —
  keeps `HubProvisioningRunnerTest`'s positional `new HubProperties(...)` calls compiling untouched.
- **`HubBeansConfig`** — the `licenseService` bean now passes
  `Duration.ofHours(properties.suspendedGraceHours())` as the new 6th arg.
- **`HubBootstrapRunner`** — same 6th arg added to its hand-built `new LicenseService(...)`. This
  call site is **not** in the plan's stated file list; found by grepping every `new LicenseService(`
  call site repo-wide (same "grep live code, don't trust the plan list" precaution used across this
  branch). It has `HubProperties` in hand, so it reuses `suspendedGraceHours()` identically.
- **`LicenseServiceTest`** — existing `setUp` now passes `Duration.ofHours(48)`. Added a
  `newServiceWithGrace(Duration)` helper that builds the service against a **mocked** `HubStateStore`
  (`mockStateStore` field) — the file's default `stateStore` is a real on-disk store, so the two
  `recordSuspended` tests need a mock to `verify`/`verify(never())` persistence. 5 new tests: 3 for
  `isSuspendedGraceExpired` (null / within 48h / past 48h), 2 for `recordSuspended` (first-time
  stamp+persist, already-suspended no-op). Added `never`/`verify`/`any`/`Duration` imports.
- **`HubPropertiesTest`** — 1 new test `fromEnvironment_heartbeatDefaults` asserting the two new
  defaults (`""`, `48`).

## Why It Changed?
The License Heartbeat feature (sub-project A1) needs the Hub to keep operating for a 48-hour
courtesy window after `/console` suspends a restaurant, rather than halting instantly on a
possibly-transient or mistaken suspension. `isSuspendedGraceExpired` is the predicate
`GracePeriodInterceptor` will consult (HEARTBEAT-06); `recordSuspended` is what
`HeartbeatScheduler` calls on a `SUSPENDED` response (HEARTBEAT-05). The `HubProperties` fields
carry the endpoint URL and the configurable grace length from the environment into the wired beans.
The back-compat constructor and the 3-arg `HubState` constructor from HEARTBEAT-01 together keep
every pre-existing call site compiling with no churn.

## Verification
- `cd backend && ./mvnw test -Dtest=LicenseServiceTest,HubPropertiesTest,HubProvisioningRunnerTest,HubStateStoreTest`
  → **21/21 PASS** (LicenseServiceTest 11, HubPropertiesTest 2, HubProvisioningRunnerTest 4, HubStateStoreTest 4).
- `cd backend && ./mvnw test` (full suite) → **876/876 PASS**, 0 failures / 0 errors
  (870 prior from report 265 + 5 new `LicenseServiceTest` + 1 new `HubPropertiesTest`).
