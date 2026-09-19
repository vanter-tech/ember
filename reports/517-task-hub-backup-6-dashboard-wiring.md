# Report 517 — HUB-BACKUP-RESTORE Task 6: dashboard wiring + manual checklist

## 1. Identification
- **Report number:** 517
- **Task ID:** HUB-BACKUP-RESTORE / Task 6 (plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`) — last task of the feature
- **Predecessor task:** Task 5 (report 516)

## 2. Objective
Make the backup/restore feature reachable from the Ember Hub window and document how to verify it on a real install.

## 3. Modified Files
- `ember-hub/ui/src/components/Dashboard.tsx`
- `ember-hub/VERIFY.md`
- `PROGRESS.md`, `reports/517-task-hub-backup-6-dashboard-wiring.md`

## 4. What Changed?
- `Dashboard` mounts `BackupCard` after the license card, with two native pickers from the already-installed `@tauri-apps/plugin-dialog`: a folder picker (`open({ directory: true })`) and a `.zip` file picker. `onBusyChange={setBusy}` disables Iniciar/Detener/Reiniciar during a restore; `onRestored={refresh}` refreshes the status after it. The card sits inside `{status && …}` and its routes don't need Postgres, so it stays usable when Postgres won't start (the corruption case). No Rust change was needed.
- `VERIFY.md`: new manual checks 14–19 (modal + "Esta máquina", USB and missing USB, settings + images round trip via an uploaded file, corrupted-database recovery, newer-version guard, automatic backup).

## 5. Why It Changed?
Tasks 1–5 built the pieces; this is the step that lets the owner actually use them. The native dialogs, a real backup→restore on an installed Hub and the automatic backup can't be covered by automated tests, hence the checklist.

Verification: `./mvnw test` → **1360/1360**; `ember-hub/ui`: `pnpm test` **25/25**, `pnpm run build` OK, `tsc --noEmit` clean for the touched files. The main `frontend/` is untouched.

Still pending (user-gated): PR/merge of `feat/hub-backup-restore`, rebuild of the Hub installer (`ember-hub/build-installer.ps1` — the backend jar changed; the Tauri shell did not) and running `VERIFY.md` items 14–19 on a real install.
