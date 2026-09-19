# Report 514 — HUB-BACKUP-RESTORE Task 3: restore

## 1. Identification
- **Report number:** 514
- **Task ID:** HUB-BACKUP-RESTORE / Task 3 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`)
- **Predecessor task:** Task 2 (report 513)

## 2. Objective
Restore an Ember Hub to the state of a backup file, safely, including when the Postgres data directory is corrupt.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java` (`stopAndWait()`)
- `backend/src/main/java/com/vanter/ember/hub/control/DefaultHubOrchestrator.java`
- `backend/src/main/java/com/vanter/ember/hub/backup/HubBackup.java` (`restore`)
- `backend/src/main/java/com/vanter/ember/hub/backup/HubBackupService.java`
- `backend/src/test/java/com/vanter/ember/hub/control/DefaultHubOrchestratorTest.java`
- `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java` (fake gets `stopAndWait`)
- `backend/src/test/java/com/vanter/ember/hub/backup/FakeHubOrchestrator.java`
- `backend/src/test/java/com/vanter/ember/hub/backup/HubBackupServiceTest.java` (+3 guard tests)
- `backend/src/test/java/com/vanter/ember/hub/backup/HubBackupRestoreIntegrationTest.java` (new)
- `PROGRESS.md`, `reports/514-task-hub-backup-3-restore.md`

## 4. What Changed?
- `HubOrchestrator.stopAndWait()` / `DefaultHubOrchestrator`: like `stop()` but synchronous (server, MinIO and Postgres all stopped before it returns), so `start()` works again.
- `HubBackupService.restore(path, skipSafetySnapshot)`, under the shared lock: validate + version gate (nothing touched if it fails) → pre-restore safety snapshot to the configured folder (failure ⇒ `BACKUP_SAFETY_FAILED`, nothing stopped, so the UI can offer "continue without it") → `stopAndWait` → make sure Postgres runs → `dropdb --force` + `createdb` + `pg_restore` → stop Postgres → swap MinIO's directory (extract to `<dir>.restoring`, move the old one to `<dir>.replaced`, then delete it) → `start`. Any failure after the stop is reported as `BACKUP_RESTORE_FAILED` with the location of the safety snapshot, and services are not auto-started against a possibly empty database.
- If Postgres won't start (corrupt data dir) the data dir is moved to `<dir>.corrupt-<ts>` (never deleted) and a fresh cluster is initialised; a port conflict is checked first and is not treated as corruption.
- Tests: orchestrator `stopAndWait`; 3 guard tests (invalid file, newer version, failing safety snapshot — none of them stop anything); 2 integration tests against the vendored portable Postgres (real `pg_dump`/`pg_restore`): a full round trip of DB rows (used as a stand-in for settings) and MinIO files, and recovery from a corrupt data dir.

## 5. Why It Changed?
Restore is the reason for the feature ("por si se corrompe algo"). The ordering makes the dangerous part last: everything that can be validated is validated before anything is stopped, and the current state is snapshotted first. Corrupt-data-dir handling is required because the typical trigger is a Hub whose Postgres no longer starts. Settings live in Postgres, so they are restored with the dump.

Verification: `./mvnw test` → **1350/1350** (1344 + 6: 1 orchestrator, 3 guards, 2 integration), BUILD SUCCESS; both integration tests ran (not skipped) on this machine.
