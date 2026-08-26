# Report 227 — Task HUB-01-05: `LicenseService`

## 1. Identification
- **Report:** 227
- **Task ID:** HUB-01-05
- **Predecessor Task:** HUB-01-04 (report 226)

## 2. Objective
Add the service that ties licensing together: verify `license.key` against the embedded RSA
public key, hardware-lock activation to the machine's fingerprint on first run, and expose the
4-day grace-period check that `GracePeriodInterceptor` (HUB-01-09) will enforce.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java` (new)

## 4. What Changed?
`LicenseService`, constructed with `(Path licenseFile, PublicKey publicKey, LicenseKeyParser,
HardwareFingerprintService, HubStateStore)`:
- `validateOrActivate(): HubState throws InvalidLicenseException` — missing `license.key` throws;
  otherwise parses+verifies it (`LicenseKeyParser`), reads the current hardware fingerprint. If
  `HubStateStore` already has state, a fingerprint mismatch throws ("otra PC"), else the existing
  state is returned unchanged (idempotent on the activated machine). If no state exists yet, it
  stamps `(fingerprint, licenseKey.restaurantId(), now())`, persists it, and returns it (first-run
  activation).
- `isWithinGracePeriod(HubState): boolean` — `now() - lastHeartbeatAt <= 4 days`.
- `recordHeartbeatSuccess(HubState): HubState` — bumps `lastHeartbeatAt` to `now()`, persists, and
  returns the updated state; has no caller yet (HUB-02's sync client will call it after each
  successful heartbeat).

Test covers: no-file throw, first-run activation+persistence, idempotent second run on the same
hardware, throw on a different hardware fingerprint, grace-period true/false boundary, and
heartbeat-success timestamp bump + persistence.

## 5. Why It Changed?
Taken verbatim from Task 5 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`.
Hardware-locking on first activation (rather than on every boot against the license file alone) is
what stops a `license.key` from being copied onto a second PC — the fingerprint is captured once
into local state and checked against on every subsequent run.

## Verification
- `./mvnw test -Dtest=LicenseServiceTest` — PASS (6/6, all new).
- `./mvnw test` — PASS, 801/801 (up from 795; no regressions).
