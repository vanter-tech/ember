# Report 233 — bugfix-hub-portable-db-missing-database

## 1. Identification
- **Report number:** 233
- **Task ID:** bugfix-hub-portable-db-missing-database (found during HUB-01-11's manual verification, spun out as its own atomic task)
- **Predecessor task:** report 232 (HUB-01-10, hub tray icon)

## 2. Objective
Fix a real boot-hang bug found while manually running HUB-01-11 (Task 11 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`) against a real portable Postgres 16.6 distribution on Windows: `PortableDatabaseBootstrap` starts the portable Postgres server successfully but never creates the `ember` database the app's own datasource URL (`application-hub.yml`) connects to, so Spring's connection pool retries forever and the app never finishes booting.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`

## 4. What Changed?
- `ensureRunning()` now calls a new private `ensureApplicationDatabaseExists()` right after `startServer()` returns.
- `ensureApplicationDatabaseExists()` shells out to the portable distribution's own `createdb` binary (`createdb -U ember -h localhost -p <port> ember`), same `ProcessBuilder`/merged-stream/exit-code pattern as `initializeDataDirectory`/`startServer`. Exit code `0` is success; a non-zero exit whose captured output contains `"already exists"`/`"ya existe"` is also treated as success (idempotent — every later boot after the first hits this "already exists" path harmlessly). Any other failure throws `PortableDatabaseException` with the captured output, matching the existing actionable-message convention.
- No new auth handling needed: `initdb`'s default `trust` method already covers loopback (`127.0.0.1`/`localhost`) connections on Windows (no unix sockets), confirmed manually against the real portable instance before writing the fix.

## 5. Why It Changed?
`initdb -U ember` only ever creates the `postgres`/`template0`/`template1` databases — never one named after the `-U` value. `application-hub.yml`'s datasource URL is `jdbc:postgresql://localhost:<port>/ember`, so on a genuinely fresh portable install the app was guaranteed to hang: Postgres itself came up healthy, but every Hikari connection attempt hit `FATAL: no existe la base de datos «ember»` and retried every 5 seconds indefinitely, with no boot-error message and no exit — the one boot path the plan's spec explicitly says "no hay CI que cubra esto, es verificación manual" (HUB-01-11) exists specifically to catch. Confirmed live: `postgres.log` from the real portable run showed the FATAL loop; after manually running `createdb ember` against that same live instance the error stopped recurring, confirming the fix's mechanism before encoding it in `PortableDatabaseBootstrap`.

## Verification
`cd backend && ./mvnw test` — 807/807 PASS, `BUILD SUCCESS`. No test changes: the existing `PortableDatabaseBootstrapTest` only covers `isPortInUse` (unchanged); `ensureApplicationDatabaseExists` needs real Postgres binaries, so it's verified live as part of resuming HUB-01-11's manual verification (not a new unit test), same deferral rationale the plan already used for `initializeDataDirectory`/`startServer`.
