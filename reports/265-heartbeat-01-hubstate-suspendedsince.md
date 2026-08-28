# Report 265 — HEARTBEAT-01: `HubState.suspendedSince` + state-store round-trip

## 1. Identification
- **Report number:** 265
- **Task ID:** HEARTBEAT-01 (Task 1 of the Ember Hub Fase 2 / Sub-project A1 "License Heartbeat" plan, `docs/superpowers/plans/2026-08-28-hub-license-heartbeat.md`)
- **Predecessor Task:** report 264 (fix-stuck-bills-cleanup)
- **Branch:** `feat/hub-license-heartbeat` (off `main`)

## 2. Objective
Give `HubState` a fourth component, `suspendedSince`, so a later task can record a
cloud-driven suspension into `hub-state.json`, without breaking any existing
`new HubState(...)` call site or any already-written (legacy) state file on disk.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/HubState.java`
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- `backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java`
- `PROGRESS.md`
- `reports/265-heartbeat-01-hubstate-suspendedsince.md` (this file)

## 4. What Changed?
### `HubState.java`
- Record header gains a 4th component: `Instant suspendedSince`. This is now the
  canonical constructor.
- Added a 3-arg back-compat constructor `HubState(String, UUID, Instant)` that
  delegates to the canonical one with `suspendedSince = null`. Every pre-existing
  call site (`LicenseService.validateOrActivate`, and the `HubProvisioningRunnerTest`
  / `LicenseServiceTest` / `GracePeriodInterceptorTest` / `HubStateStoreTest`
  fixtures) keeps compiling unchanged.
- Added `withHeartbeatNow()` → returns a copy with `lastHeartbeatAt = Instant.now()`
  and `suspendedSince = null` (a successful heartbeat clears any prior suspension).
- Added `withSuspendedSince(Instant when)` → returns a copy with `suspendedSince = when`
  and the same fingerprint / restaurantId / lastHeartbeatAt.

### `LicenseService.java`
- `recordHeartbeatSuccess(HubState)` now delegates to `state.withHeartbeatNow()`
  instead of hand-rebuilding the record. Behaviour is identical for the existing
  3-component shape; it additionally clears `suspendedSince` on a heartbeat.

### `HubStateStoreTest.java`
- Added `save_thenLoad_roundTripsSuspendedSince` — a `HubState` built with the
  4-arg constructor and a non-null `suspendedSince` survives a `save` → `load`
  cycle through the Jackson `ObjectMapper` unchanged.
- Added `load_legacyStateFileWithoutSuspendedSince_readsNull` — a hand-written JSON
  file with only the original 3 fields still deserializes, with `suspendedSince`
  read as `null` (forward-compatible on-disk format; no migration of existing
  `hub-state.json` files needed).
- Added the `java.nio.file.Files` import for the legacy-file test. Both new tests
  construct their own `HubStateStore` locally, matching this file's existing
  per-test setup style (no shared `store` / `stateFile` fields).

## 5. Why It Changed?
The heartbeat feature needs two independent grace clocks: the existing 4-day
offline-grace clock keyed off `lastHeartbeatAt`, and a new 48-hour courtesy-grace
clock that starts when the cloud reports the restaurant as `SUSPENDED`. Storing the
suspension start on `HubState` (which is deliberately a plain local JSON file,
readable even when Postgres won't boot) keeps the suspension decision durable
across Hub restarts. The back-compat constructor and the legacy-file test exist
because Hubs already in the field have a 3-field `hub-state.json` that must keep
loading after an update. `withHeartbeatNow()` / `withSuspendedSince()` centralize
the "which fields change together" rule so Task 2's `recordSuspended` and the
existing `recordHeartbeatSuccess` can't drift.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=HubStateStoreTest,LicenseServiceTest,GracePeriodInterceptorTest,HubProvisioningRunnerTest` → **16/16 PASS** (`HubStateStoreTest` now 4 tests, +2 new).
- `cd backend && ./mvnw test` (full suite) → **870/870 PASS**, 0 failures / 0 errors (868 prior + 2 new).
