# Ember Hub — Local Backup & Restore — Design

- **Date:** 2026-09-14
- **Branch:** to be created off `main` when implementation starts (`writing-plans`)
- **Status:** design approved in chat 2026-09-14, pending spec review → `writing-plans`
- **Related:** the gap this closes was surfaced during PILOT-READINESS Task 1 (report 485,
  `PROGRESS.md`'s Open/deferred list); reuses the portable Postgres binaries from
  `hub/bootstrap/PortableDatabaseBootstrap.java`; reuses the graceful stop/start orchestration
  `hub/control/DefaultHubOrchestrator.java` already has from the license-removal flow (report 458).

## 1. Objective

Ember Hub (the on-premise Tauri app: Rust shell + headless Spring Boot sidecar + portable
Postgres/MinIO) has **no backup mechanism at all today**. A restaurant running Hub on their own PC
loses every order, bill, cash-shift and uploaded image if that PC's disk fails or the install is
lost — there is nothing to restore from. This is separate from and unrelated to prod's own backup
cadence (PILOT-READINESS #1, report 485, which is the cloud SaaS VM's `pg_dump`→GCS and is already
done); Hub installs are individually owned by each restaurant and have no relationship to that
cloud bucket.

Scope for this pass: automatic scheduled + manual local backup, and a restore path, both driven
from the Hub dashboard. Destination is a local/removable folder the restaurant owner picks
themselves (USB, another drive, a network share) — **not** cloud upload. No cost, no per-install
credentials to provision, no change to Ember's own infra. Cloud upload as a second destination is
an explicitly deferred future addition (see §7).

## 2. Current state (verified)

- **No backup code exists anywhere under `hub/`.** Confirmed by directory listing —
  `hub/{bootstrap,config,control,dashboard,license,provisioning,sync}`, no `backup` package.
- **The portable Postgres the Hub ships already bundles `pg_dump.exe` and `pg_restore.exe`**
  (`ember-hub/.vendor-cache/postgres/bin/`), alongside `pg_ctl`/`initdb`/`createdb`, which
  `PortableDatabaseBootstrap` already invokes via `HubProperties.postgresBinDir`. No new binary,
  no new dependency — this feature only adds two more invocations of already-bundled tools.
- **Data lives in two plain directories**, both configurable via env var, defaulting under the
  install's working directory (packaged installs run this under `%ProgramData%\EmberHub\`, same
  convention as `printing-agent`'s `%ProgramData%\EmberAgent`):
  - Postgres: `EMBER_HUB_DATA_DIR` (default `./data/postgres`)
  - MinIO (uploaded media — branding, product images): `EMBER_HUB_MINIO_DATA_DIR` (default
    `./data/minio`)
- **`HubControlServer`** (`hub/control/HubControlServer.java`) exposes 4 loopback-only HTTP routes
  today: `/api/status`, `/api/start`, `/api/stop`, `/api/license`. This feature adds 4 more,
  matching the same registration pattern (`createContext(...).getFilters().add(CORS_FILTER)`).
- **`DefaultHubOrchestrator`** already has a graceful stop-then-start sequence, built for the
  license removal/swap flow (report 458) — `onRestart` calls the app's own `/api/stop` and polls
  until it clears before any hard action. This feature reuses that primitive for restore instead of
  writing a new one; it does **not** stop Postgres or MinIO, since `pg_restore` needs a live
  Postgres connection and MinIO's files are read/copied directly off disk without needing MinIO
  itself paused.
- **Hub self-versions from `backend/pom.xml`** (`Get-AgentVersion`-equivalent logic already exists
  for the installer scripts) — this feature reuses that same version string as the "current Hub
  version" half of the compatibility check in §4.3, no new versioning scheme.
- **Existing dashboard card pattern** (`ember-hub/ui/src/components/{ServiceCard,LicenseCard}.tsx`)
  already establishes: status + error banner + action button, toggleable expanded detail. The new
  `BackupCard` follows this convention rather than inventing a new visual language.
- **Existing Tauri dialog plugin** is already a dependency (used by the license-file picker) — the
  new "Elegir carpeta" folder picker is one more command using the same plugin, not a new Cargo
  dependency.
- **Explicitly out of scope / not touched by this design:** F-21 (hardcoded Postgres/MinIO
  credentials in the bootstrap classes) is a related but separate concern in the same package
  neighborhood — not folded in here, stays its own open item.

## 3. Backend — new `hub/backup/` package

### 3.1 Components

- **`BackupConfig`** (record) — `destDir` (`Path`, nullable until first configured), `retention`
  (`int`, default 7). Persisted as a small JSON file next to the existing `state.json`
  (`HubStateStore`'s mechanism) — **not** a Postgres table, deliberately: a restore that replaces
  the whole database must never be able to erase the backup system's own configuration or in-flight
  state.
- **`BackupSnapshot`** (record) — `id` (the folder name), `createdAt`, `sizeBytes`, `status`
  (`OK`/`ERROR`), `errorMessage` (nullable), `appVersion` (from `manifest.json`, §4.3),
  `preRestoreSafety` (`boolean` — marks the auto-snapshot taken just before a restore, so the UI can
  label it distinctly and the pruner in §3.2 step 6 can skip it).
- **`BackupStatus`** (record) — `lastRun` (a `BackupSnapshot` or null), `nextScheduledRun`
  (computed from the daily cron), `destDir`, `retention`. What `GET /api/backup/status` returns.
- **`HubBackupService`** — the only place with real logic; `runBackup()`, `listSnapshots()`,
  `restore(String snapshotId)`, `getConfig()`/`setConfig(BackupConfig)`. Stateless beyond what it
  reads from `BackupConfig`/disk on each call.
- **`BackupScheduler`** — one `@Scheduled(cron = ...)` bean, daily, calling
  `HubBackupService.runBackup()`. Fixed time of day (no time-of-day picker in this pass — matches
  the already-decided "7 diarios, poda automática" scope; a configurable hour is a cheap future
  addition if requested, not built speculatively now).

### 3.2 `runBackup()` — used by both the scheduler and the manual "Respaldar ahora" button

1. Read `BackupConfig`. If `destDir` is unset or not writable right now (USB unplugged, folder
   deleted), record a `BackupSnapshot` with `status=ERROR` and a specific message, and return —
   **never throws out of the scheduled job** (an unhandled exception here must not be able to take
   down the Hub's own scheduler thread or the JVM).
2. Create `<destDir>/ember-backup-<yyyy-MM-dd'T'HH-mm>.tmp/` as a working folder.
3. `pg_dump -Fc <db> -f postgres.dump` into it (same `-Fc` custom format prod's own backup uses —
   restorable with `pg_restore`).
4. Recursive file copy of `data/minio/` → `<folder>/minio/`. Plain filesystem copy; MinIO does not
   need to be paused for this (no live writes are being raced against in the failure modes that
   matter here — restaurant traffic is synchronous request/response through the Spring app, which
   isn't stopped during a routine backup, only during restore).
5. Write `manifest.json` (`{ "appVersion": "<current Hub version>", "createdAt": "<iso>" }`).
6. Atomic rename `.tmp` → final name (`ember-backup-<timestamp>/`, or
   `ember-backup-<timestamp>-pre-restore/` when called internally from `restore()`). This is the
   crash-safety boundary: a power loss mid-copy leaves an orphaned `.tmp` folder, never a snapshot
   that *looks* complete but isn't. A future `runBackup()` (or a cheap startup sweep) can garbage
   collect stale `.tmp` folders older than a day; not building a separate sweep job for this pass —
   the next successful backup rename simply doesn't touch old `.tmp` litter, so it's inert, not
   dangerous.
7. Update `BackupStatus.lastRun`.
8. Prune: list non-`-pre-restore` snapshot folders under `destDir`, delete the oldest beyond
   `retention` (default 7). `-pre-restore` safety snapshots are **never** auto-pruned by this step
   (see §4.2) — left for the owner to clean up manually if disk space matters to them.

### 3.3 `restore(snapshotId)`

1. Read `manifest.json` from the target snapshot. Compare `manifest.appVersion` against the running
   Hub's own version. If the snapshot is newer, throw `BackupIncompatibleException` immediately —
   nothing below this line runs. **No version-comparison utility exists in the codebase today**
   (checked — nothing under `backend/src/main/java` does semver comparison); versions here are
   plain `MAJOR.MINOR.PATCH` from `pom.xml`, so a small dependency-free comparator (split on `.`,
   compare numerically component-by-component) is enough — exact placement (a static helper vs. a
   tiny `HubVersion` value type) is a judgment call for the implementation plan, not locked here.
2. Call `runBackup()` internally first, to produce a `-pre-restore` safety snapshot of the
   *current* state before anything is overwritten.
3. `orchestrator.stopApp()` (existing graceful stop, reused as-is) — Postgres and MinIO keep
   running.
4. `dropdb` + `createdb` (same binaries/pattern `PortableDatabaseBootstrap` already uses for first
   boot) + `pg_restore -d <db> postgres.dump` from the chosen snapshot.
5. Delete the current contents of `data/minio/`, copy in the snapshot's `minio/`.
6. `orchestrator.startApp()`. Flyway runs its normal migration check on this boot, same as any
   startup — since step 1 already ruled out a too-new snapshot, this is always a forward or no-op
   migration, never a mismatch.
7. If steps 3-6 throw partway through, the failure is surfaced as an `ERROR` status; the database
   is left in whatever state `dropdb`+`createdb` reached (i.e., empty at worst, never a
   half-old/half-new mix) and the `-pre-restore` snapshot from step 2 is the recovery path — the
   owner (or support) restores from it the same way, no special-cased "undo" logic needed.

### 3.4 `HubControlServer` — 4 new routes

Same registration style as the 4 existing ones:

```
GET  /api/backup/status    -> BackupStatus
GET  /api/backup/config    -> BackupConfig
POST /api/backup/config    <- { destDir, retention } -> BackupConfig
POST /api/backup/now       -> BackupSnapshot (the run just performed)
POST /api/backup/restore   <- { snapshotId } -> 200, or 409 + { code: "BACKUP_INCOMPATIBLE" }
```

## 4. Rust (`src-tauri/src/`)

One new Tauri command, `pick_backup_folder`, using the dialog plugin already present for the
license-file picker — opens the native OS folder-picker dialog, returns the chosen path (or `null`
if cancelled) to the frontend via `invoke('pick_backup_folder')`. It does not talk to Postgres, MinIO,
or the backup HTTP API itself — the frontend takes the returned path and `POST`s it to
`/api/backup/config`, same as every other Hub↔backend interaction already goes through
`HubControlServer` rather than Rust.

## 5. Frontend (`ember-hub/ui/src/`)

**New `components/BackupCard.tsx`**, same structural convention as `ServiceCard`/`LicenseCard`:

- No `destDir` configured yet → single CTA "Elegir carpeta de respaldo", calls
  `invoke('pick_backup_folder')` then `POST /api/backup/config`.
- Configured → shows: destination path, last run (timestamp + OK/error), next scheduled run,
  "Respaldar ahora" button (`POST /api/backup/now`, disabled while in flight), "Cambiar carpeta"
  (re-runs the picker).
- Error banner (same red `bg-primary` convention `LicenseCard`/`Dashboard.tsx` already use for
  Postgres/license errors) when `lastRun.status === 'ERROR'`, showing `errorMessage` verbatim (it's
  already a specific, non-technical message from the backend, e.g. "no se pudo escribir en la
  carpeta elegida").
- Snapshot list (`GET /api/backup/list`) — each row: timestamp, size, OK/error,
  "pre-restore" badge when applicable, "Restaurar" button.
- "Restaurar" opens a confirmation `Dialog` (Tauri's own, not a browser `confirm()`) with explicit
  destructive-action copy before calling `POST /api/backup/restore`. On `409
  BACKUP_INCOMPATIBLE`, shows that specific message instead of a generic error ("este respaldo es
  de una versión más reciente de Ember — actualiza el Hub antes de restaurar").

## 6. Files touched (anticipated)

**Backend**
- `hub/backup/{BackupConfig,BackupSnapshot,BackupStatus,HubBackupService,BackupScheduler,BackupIncompatibleException}.java` — new.
- `hub/control/HubControlServer.java` — 4 new route registrations.
- `hub/control/DefaultHubOrchestrator.java` — expose the existing stop/start primitives for reuse
  by `HubBackupService` (likely no new logic, just visibility/wiring).
- Flyway: **none** (backup config lives in a JSON file, not the database it's backing up).

**Rust**
- `src-tauri/src/` — new `pick_backup_folder` command + registration in the Tauri command handler
  list.

**Frontend**
- `ember-hub/ui/src/components/BackupCard.tsx` — new.
- `ember-hub/ui/src/pages/Dashboard.tsx` (or wherever `ServiceCard`/`LicenseCard` are mounted) —
  add `BackupCard`.
- `ember-hub/ui/src/lib/api.ts` (or equivalent) — the 4 new endpoint calls.

## 7. Testing

**Backend**
- `HubBackupServiceTest` — successful backup (dump + minio copy + manifest written), prune at 7
  (oldest deleted, `-pre-restore` snapshots never pruned), destination unwritable → `ERROR` status
  without throwing, successful restore (data actually round-trips), `BACKUP_INCOMPATIBLE` blocks
  before touching any data, a restore failure partway through still leaves a usable `-pre-restore`
  snapshot behind. Real portable Postgres in the test (same pattern as
  `PortableDatabaseBootstrapTest`, if that convention already exists — verify when the plan is
  written), not mocked, since the whole point is exercising real `pg_dump`/`pg_restore`.
- `HubControlServerTest` — the 4 new routes, slice-tested like the existing 4.
- Full `./mvnw test` green.

**Frontend**
- `BackupCard.test.tsx` — unconfigured/configured/error/snapshot-list states; restore confirmation
  gate; `BACKUP_INCOMPATIBLE` message path.
- `ember-hub/ui` `npm run build` + `npm test` clean.

**Not covered by automated tests, deferred to manual verification when implementation lands:** an
actual folder-picker dialog interaction (Tauri native dialog, not mockable in the JS test
environment) and a real end-to-end backup→restore cycle on a live installed Hub — same category of
manual check as `VERIFY.md` already covers for the rest of the Hub/Tauri surface.

## 8. Decisions locked (were the open questions during design)

1. **Destination** — local/removable folder only (USB, another drive, network share), owner-picked.
   Cloud upload is explicitly deferred, not built now.
2. **Trigger** — daily automatic (fixed time, no picker) + manual "Respaldar ahora" button, same
   code path either way.
3. **Folder selection** — native OS folder dialog via Tauri, not a fixed hardcoded path.
4. **Restore** — included in this pass (not deferred), with a mandatory automatic pre-restore
   safety snapshot that's exempt from normal pruning.
5. **What's backed up** — Postgres (`pg_dump -Fc`) **and** MinIO's file directory (plain recursive
   copy). Hub license/`state.json` is explicitly **not** backed up (tied to this PC's hardware
   fingerprint, meaningless to restore elsewhere).
6. **Retention** — 7 daily snapshots, auto-pruned; pre-restore safety snapshots don't count against
   this and aren't auto-pruned.
7. **Version compatibility** — each snapshot records the Hub version that created it
   (`manifest.json`); restoring a snapshot newer than the currently installed Hub is blocked with a
   specific `BACKUP_INCOMPATIBLE` error rather than being attempted and failing inside Flyway.
   Restoring an equal-or-older snapshot is always allowed; Flyway's normal forward migration on
   startup handles the schema difference exactly as it does for any Hub upgrade.
8. **Crash safety** — snapshots are written to a `.tmp` suffix and atomically renamed only once
   complete, so a mid-write crash (power loss, disk full) can never produce a snapshot that looks
   valid but isn't.
9. **No new dependencies** — `pg_dump`/`pg_restore` are already bundled with the Hub's portable
   Postgres; the Tauri dialog plugin is already a dependency from the license-file flow.
