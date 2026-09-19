# Report 513 — HUB-BACKUP-RESTORE Task 2: backup service

## 1. Identification
- **Report number:** 513
- **Task ID:** HUB-BACKUP-RESTORE / Task 2 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`)
- **Predecessor task:** Task 1 (report 509). Reports 510–512 are hotfixes done on `main` in between (hence the number).

## 2. Objective
The backup half of Ember Hub backup/restore: create backups, list and inspect them, keep the schedule and pruning rules — all without touching a real Postgres yet (the `pg_dump` call is stubbed in tests).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/backup/PostgresTools.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/HubBackup.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/HubBackupService.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/backup/FakeHubOrchestrator.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/backup/HubBackupServiceTest.java` (new, 14 tests)
- `PROGRESS.md`
- `reports/513-task-hub-backup-2-backup-service.md`

## 4. What Changed?
- `PostgresTools`: process wrapper for `pg_dump`/`dropdb`/`createdb`/`pg_restore` (same user/host/auth assumptions as `PortableDatabaseBootstrap`); non-final so tests stub it. `dropAndCreate`/`restore` are used by Task 3.
- `HubBackup`: the interface the control server will use (`status`, `getConfig`/`setConfig`, `backupNow`, `listSnapshots`, `inspect`).
- `HubBackupService`: `backupNow` never throws (failures come back as an `ERROR` snapshot and are persisted as the last run); the zip is written as `.tmp` and atomically renamed; dumping happens in a local temp dir so a slow USB never holds a half dump; only **scheduled** runs prune (retention counts only regular `ember-backup-<ts>.zip`, never `-pre-restore` files, and manual backups never delete anything); `runScheduledIfDue` waits for Postgres to be RUNNING, runs 24 h after the last success, and backs off 1 h after a failure; `inspect` validates the file and blocks a snapshot newer than the running Hub; a single lock (`tryLock` for backups) serialises with restore.

## 5. Why It Changed?
The service is the core of the feature, so it was built and tested in isolation before any wiring. Two rules come from real usage: a manual backup to a USB must not delete that USB's older files, and restaurant PCs are often off at night, so the schedule is "24 h since the last success, checked periodically" rather than a fixed hour. All I/O against the database is behind `PostgresTools`, which keeps these tests fast and deterministic; the real `pg_dump`/`pg_restore` round trip is exercised in Task 3.

Verification: `./mvnw test` → **1344/1344** (1330 + 14), BUILD SUCCESS.
