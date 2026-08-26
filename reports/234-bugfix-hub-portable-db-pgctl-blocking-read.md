# Report 234 — bugfix-hub-portable-db-pgctl-blocking-read

## 1. Identification
- **Report number:** 234
- **Task ID:** bugfix-hub-portable-db-pgctl-blocking-read (found during HUB-01-11's manual verification, spun out as its own atomic task)
- **Predecessor task:** report 233 (bugfix-hub-portable-db-missing-database)

## 2. Objective
Fix a boot hang found while resuming HUB-01-11's manual verification after report 233's fix: `PortableDatabaseBootstrap.startServer()` could deadlock forever, with Postgres itself fully up and healthy but the JVM's `main` thread never returning — so Spring never started and nothing (including HUB-01-10's tray icon) ever came up.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`

## 4. What Changed?
- `startServer()` no longer pipes `pg_ctl`'s stdout/stderr into Java via `process.getInputStream().readAllBytes()`. It now uses `ProcessBuilder.redirectOutput(File)` to a new sibling file (`pg_ctl-start.log`), and just calls `process.waitFor()` for the exit code.
- On a non-zero exit, the error message now reads `pg_ctl-start.log` via the existing `readLogTail` helper (same shape already used for `postgres.log`) instead of the in-memory captured string.
- `initializeDataDirectory()` (`initdb`) and `ensureApplicationDatabaseExists()` (`createdb`, added in report 233) are unchanged — both spawn short-lived processes that exit on their own, so they aren't exposed to this failure mode.

## 5. Why It Changed?
Live `jstack` on the hung JVM showed the `main` thread blocked inside `FileInputStream.read`, called from `PortableDatabaseBootstrap.startServer():99` — i.e. still inside `readAllBytes()`, which blocks until the pipe's write end closes (EOF). `pg_ctl start -w` launches `postgres.exe` as a detached, long-running server; on Windows, `postgres.exe`'s own background children (checkpointer, walwriter, etc. — confirmed via `Get-Process`, 6 separate `postgres.exe` processes) can inherit `pg_ctl`'s own stdout handle, so the pipe never reaches EOF as long as Postgres keeps running, even though `pg_ctl` itself has long since exited. This is timing-dependent (an earlier boot attempt with the same code did NOT hang), which is why report 228/229's original manual smoke test didn't catch it. Redirecting straight to a file sidesteps the pipe entirely — nothing blocks on it regardless of which processes inherit the file handle.

**Verification:** `cd backend && ./mvnw test` — 807/807 PASS. Re-ran HUB-01-11's manual boot (port 5433): `pg_ctl-start.log` was written and `startServer()` returned within ~15s (previously hung 10+ minutes); confirmed via `netstat`/`Get-CimInstance` that the real `EmberApplication` PID was the one listening on 8080, not a stray process.
