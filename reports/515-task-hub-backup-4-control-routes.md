# Report 515 — HUB-BACKUP-RESTORE Task 4: scheduler, control-server routes, sidecar wiring

## 1. Identification
- **Report number:** 515
- **Task ID:** HUB-BACKUP-RESTORE / Task 4 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`)
- **Predecessor task:** Task 3 (report 514)

## 2. Objective
Expose the backup service to the Hub window (Tauri shell) over the loopback control server, run the automatic backups, and wire everything into the sidecar's startup.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupScheduler.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`
- `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- `backend/src/test/java/com/vanter/ember/hub/backup/BackupSchedulerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`
- `PROGRESS.md`, `reports/515-task-hub-backup-4-control-routes.md`

## 4. What Changed?
- `BackupScheduler`: plain `ScheduledExecutorService` (daemon thread), first tick after 2 min then every 15 min, calling `HubBackupService.runScheduledIfDue`; `tick()` swallows any throwable so one failure can't cancel the schedule. Not a Spring `@Scheduled` bean because the Spring context is closed during a restore.
- `HubControlServer` now takes a `HubBackup` and registers 6 routes: `GET /api/backup/status`, `GET|POST /api/backup/config`, `POST /api/backup/now` (body optional), `GET /api/backup/list`, `POST /api/backup/inspect`, `POST /api/backup/restore`. Errors carry `{error, code}`: `BACKUP_INCOMPATIBLE`/`BACKUP_SAFETY_FAILED` → 409, `BACKUP_INVALID` → 400, others → 500; a backup that fails to run still answers 200 with an `ERROR` snapshot.
- `EmberApplication.runHubSidecar`: builds `BackupConfigStore` (`hub-backup.json` and the `backups` folder next to `hub-state.json`, i.e. `%ProgramData%\EmberHub` when installed), `PostgresTools`, `HubBackupService`, starts the scheduler, passes the service to the control server, and stops the scheduler in the shutdown hook.
- 10 new tests: 1 scheduler, 9 control-server route tests (with a `FakeBackup`), including the error-code → HTTP-status mapping and the empty-body `now`.

## 5. Why It Changed?
The UI (Task 5) can only talk to the sidecar through `HubControlServer`, and the automatic backup has to run in the same process that survives a restore. The wire contract (status codes + machine-readable `code`) is fixed here so the frontend can show the specific messages (incompatible version, "continue without safety copy"). The `EmberApplication` wiring is not unit-tested (it is the sidecar's `main`); it compiles and is covered by the manual checks in `VERIFY.md` (Task 6).

Verification: `./mvnw test` → **1360/1360** (1350 + 10), BUILD SUCCESS.
