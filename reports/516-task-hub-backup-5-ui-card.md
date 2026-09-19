# Report 516 — HUB-BACKUP-RESTORE Task 5: Hub UI backup card

## 1. Identification
- **Report number:** 516
- **Task ID:** HUB-BACKUP-RESTORE / Task 5 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`)
- **Predecessor task:** Task 4 (report 515)

## 2. Objective
The Hub window's UI for backups: back up (modal recommending a USB drive vs. this machine), list backups, and restore from an uploaded file with confirmation.

## 3. Modified Files
- `ember-hub/ui/src/lib/types.ts`, `ember-hub/ui/src/lib/api.ts`
- `ember-hub/ui/src/components/Modal.tsx` (new)
- `ember-hub/ui/src/components/BackupCard.tsx` (new)
- `ember-hub/ui/src/components/BackupCard.test.tsx` (new, 12 tests)
- `PROGRESS.md`, `reports/516-task-hub-backup-5-ui-card.md`

## 4. What Changed?
- `types.ts`: `BackupSnapshot`, `BackupConfig`, `BackupStatus` mirroring the Task 4 wire contract.
- `api.ts`: `ApiError` (carries the backend's `code`) now thrown by every call, plus `getBackupStatus`, `listBackups`, `setBackupConfig`, `backupNow`, `inspectBackup`, `restoreBackup`.
- `Modal`: small in-window dialog (no browser `confirm()`).
- `BackupCard`: shows the automatic folder, next automatic run and last-run error banner; **Respaldar ahora** opens the destination modal ("USB o disco externo — Recomendado" / "Esta máquina", with a reminder to copy a machine backup to a USB); **Restaurar desde archivo…** picks a file, validates it with `inspect`, then asks for explicit confirmation ("Sí, restaurar") — an incompatible-version error is shown without opening the confirmation; if the safety copy can't be made it offers "Restaurar sin copia previa"; the list of backups has a per-row **Restaurar**; **Cambiar carpeta automática…** re-points the scheduled backups. The native pickers are injected (`pickFolder`, `pickBackupFile`), `onBusyChange`/`onRestored` let the dashboard lock its buttons during a restore.
- While writing it, the confirmation button was changed to use the path the user picked rather than the path echoed by `inspect` (found by the tests; more robust).

## 5. Why It Changed?
This is the user-facing half of the feature the owner asked for: upload a file to go back to an earlier state, and a modal that steers backups to a USB. Injecting the pickers keeps the component testable without Tauri. The card is mounted in the dashboard in Task 6.

Verification: `pnpm test` in `ember-hub/ui` → **25/25** (13 existing + 12 new), `pnpm run build` OK, `tsc --noEmit` reports nothing for the new/changed files.
