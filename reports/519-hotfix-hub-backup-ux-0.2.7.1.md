# Report 519 — Hotfix 0.2.7.1: Hub backup UX (stuck button, license gating, progress bar, responsive grid)

## 1. Identification
- **Report number:** 519
- **Task ID:** ad-hoc, feedback from testing the 0.2.7 installer
- **Predecessor task:** report 518 (bump 0.2.7)

## 2. Objective
Fix the observations from the first manual test of Hub backup/restore: (1) "Respaldar ahora" without a license got stuck and disabled until a restart, and must not be enabled without the license; (2) add a progress bar; (3) the window is not usable on small laptops; (4) cards should use a grid when the window is wider.

## 3. Modified Files
- Backend: `hub/backup/{PostgresTools,BackupArchive,BackupStatus,HubBackupService}.java`, new `hub/backup/BackupProgress.java`; tests `PostgresToolsTest` (new), `BackupArchiveTest`, `HubBackupServiceTest`, `HubControlServerTest`
- Hub UI: `ember-hub/ui/src/{lib/types.ts,components/BackupCard.tsx,components/BackupCard.test.tsx,components/Dashboard.tsx,styles/global.css}`
- Tauri shell: `ember-hub/src-tauri/{tauri.conf.json,src/main.rs}`
- `ember-hub/VERIFY.md` (checks 20–22), `backend/pom.xml` (0.2.7 → 0.2.7.1), `PROGRESS.md`, this report

## 4. What Changed?
- **Stuck button (root cause, reproduced):** the dev machine has another, password-protected Postgres on port 5432. With no license the Hub's own Postgres never starts, so `pg_dump` connected to that other server, prompted for a password on the console and never returned; the UI stayed in "working" forever. Running the bundled `pg_dump` against 5432 without `-w` hung (killed by `timeout` at 10 s); with `-w` it fails in 0.07 s. Fix: every Postgres tool now runs with `-w` (never prompt) and a timeout (60/120/3 min) that kills the process; `backupNow` (and the restore's safety copy) refuse unless the Hub's **own** Postgres is `RUNNING`, which also prevents backing up a foreign database.
- **Gating:** `BackupCard` gets `licenseStatus`/`postgresRunning`; without a license "Respaldar ahora", "Restaurar desde archivo…" and the rows' "Restaurar" are disabled with a hint; with a license but stopped services only backing up is disabled.
- **Progress bar:** `BackupStatus.progress` (`operation`, `phase`, `percent|null`) is updated by the service (exporting DB = animated, compressing/restoring images = real % counted in files, stopping/starting services) and polled every 600 ms by the card while working.
- **Layout:** dashboard container `max-w-5xl`, cards in `grid-cols-1 md:grid-cols-2` (Respaldos spans both), header buttons wrap; window default 900×780 with minimum 420×360, and a Rust `fit_window_to_screen` shrinks it to 92%/85% of the monitor's logical size and centers it (3 Rust unit tests).

## 5. Why It Changed?
A silent infinite hang is the worst failure mode for a backup tool, and backing up the wrong database would have been worse; both come from letting `pg_dump` talk to whatever answers on the port. Gating and the progress bar were requested; the window sizing came from the 720×800 default with a 640×640 minimum not fitting small/scaled laptop screens.

Verification: `./mvnw test` **1367/1367** (1360 + 7; the 2 real-Postgres round-trip tests still pass with `-w`), `ember-hub/ui` `pnpm test` **29/29** (25 + 4), `pnpm run build` OK, `tsc --noEmit` clean for touched files, `cargo test` 5/5. Not yet verified on an installed Hub (see `VERIFY.md` 20–22).
