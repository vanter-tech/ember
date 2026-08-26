# Report 226 — Task HUB-01-04: `HubState`/`HubStateStore`

## 1. Identification
- **Report:** 226
- **Task ID:** HUB-01-04
- **Predecessor Task:** HUB-01-03 (report 225)

## 2. Objective
Add a local, Postgres-independent JSON store for the Hub's activation state — has to be
readable even before/without a working Postgres connection, since `LicenseService` (HUB-01-05)
and `GracePeriodInterceptor` (HUB-01-09) both need it during the pre-Spring bootstrap path.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/HubState.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/license/HubStateStore.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java` (new)

## 4. What Changed?
- `HubState`: a plain record `(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt)`.
- `HubStateStore`: constructed with a `Path stateFile`. `load()` returns `Optional.empty()` when
  the file doesn't exist, otherwise deserializes it via a private Jackson `ObjectMapper`
  (`JavaTimeModule` + `INDENT_OUTPUT`); `save(HubState)` creates parent directories as needed and
  writes the state as pretty-printed JSON. Both wrap `IOException` in `IllegalStateException`.
- Test covers: missing-file → empty `Optional`, and save-then-load round-trip (including nested
  parent-directory creation) preserving equality.

## 5. Why It Changed?
Taken verbatim from Task 4 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`.
Plain-file JSON (not Postgres) is deliberate: the Hub's activation state must survive/be readable
even if the portable Postgres instance fails to boot, since `GracePeriodInterceptor` and the
pre-Spring bootstrap in `EmberApplication.main` both need to read it before/independent of the
database being up.

## Verification
- `./mvnw test -Dtest=HubStateStoreTest` — PASS (2/2, both new).
- `./mvnw test` — PASS, 795/795 (up from 793; no regressions).
