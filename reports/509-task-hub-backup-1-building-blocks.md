# Report 509 — HUB-BACKUP-RESTORE Task 1: building blocks

## 1. Identification
- **Report number:** 509
- **Task ID:** HUB-BACKUP-RESTORE / Task 1 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`)
- **Predecessor task:** report 508 — hotfix 0.2.6.1 prep

## 2. Objective
Pure, dependency-free foundation for Ember Hub backup/restore: data records, version comparison, JSON config persistence and the zip backup format. No I/O against Postgres/MinIO yet.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupConfig.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupSnapshot.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupStatus.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupException.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/HubVersion.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupConfigStore.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/backup/BackupArchive.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/backup/HubVersionTest.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/backup/BackupConfigStoreTest.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/backup/BackupArchiveTest.java` (new)
- `docs/superpowers/plans/2026-09-19-hub-backup-restore.md` (new — the approved plan)
- `PROGRESS.md`

## 4. What Changed?
- `BackupConfig`/`BackupSnapshot`/`BackupStatus`: records shared by the service, the control server and the UI. Timestamps are ISO strings (the control server's plain `ObjectMapper` has no JavaTime module).
- `BackupException`: one exception with a machine-readable `code` (`BACKUP_INCOMPATIBLE`, `BACKUP_SAFETY_FAILED`, `BACKUP_INVALID`, `BACKUP_FAILED`, `BACKUP_RESTORE_FAILED`).
- `HubVersion.isNewer`: numeric per-component comparison (handles the 4-part `0.2.6.1`); unknown/unparseable versions never block a restore.
- `BackupConfigStore`: `hub-backup.json` holding config, last run and last-success time; defaults when missing/corrupt; kept outside Postgres.
- `BackupArchive`: single `.zip` (`manifest.json`, `postgres.dump`, `minio/**`), manifest validation, and zip-slip-safe MinIO extraction.
- Branch `feat/hub-backup-restore` was created from the current branch (not `main`), because `main` lacks the r506–508 commits that `PROGRESS.md` depends on.

## 5. Why It Changed?
Restore accepts an uploaded file, so a backup must be one portable archive, and its untrusted contents must be validated and extracted safely. Keeping the backup system's own state in a JSON file (not the database) guarantees a restore can never erase it. Isolating these pieces first lets the service (Task 2) and restore (Task 3) build on tested primitives.

Verification: `./mvnw test` → **1327/1327** (1314 baseline + 13 new), BUILD SUCCESS.
