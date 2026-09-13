# Report 458

## 1. Identification
- **Report Number:** 458
- **Task ID:** EMBER-HUB-V2 feature (ad-hoc, post-plan) — manual "Reiniciar" button + full license removal/swap flow
- **Predecessor Task:** report 457 (service card manual toggle)

## 2. Objective
Two related, user-requested capabilities discovered while testing license swaps live: (1) a manual "restart everything" button, because a stuck service (e.g. a port conflict) had no in-UI recovery path once neither "Iniciar servicios" nor a working "Detener" was available; (2) a way to actually delete the current license and have a new one take effect — installing a new `license.key` over a running instance changes nothing live, since license validity is only checked once, at Spring Boot startup.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/DefaultHubOrchestrator.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`
- Modify: `backend/src/test/java/com/vanter/ember/hub/control/DefaultHubOrchestratorTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`
- Modify: `ember-hub/src-tauri/src/main.rs`
- Modify: `ember-hub/ui/src/lib/api.ts`
- Modify: `ember-hub/ui/src/components/Dashboard.tsx`
- Modify: `ember-hub/ui/src/components/LicenseCard.tsx`
- Modify: `ember-hub/ui/src/components/LicenseCard.test.tsx`

## 4. What Changed?

**4.1 — Backend: `DELETE /api/license`.** `HubOrchestrator.removeLicense()` (new interface method) deletes both the license file *and* the persisted activation state file (`Files.deleteIfExists` on both — `HubOrchestrator.snapshot()` derives `license.status` from the state file, not from the license file's mere presence, so leaving the state file behind would keep showing a stale "OK" even after the license.key was gone). `HubControlServer.handleLicense` now dispatches `DELETE` to it (same `/api/license` context as the existing `POST`, avoiding a new CORS-filtered context); the CORS preflight's `Access-Control-Allow-Methods` gained `DELETE`. TDD'd: 2 new `DefaultHubOrchestratorTest` cases (deletes both files, resets to `NONE`; no-op when nothing exists) + 2 new `HubControlServerTest` cases (200 + orchestrator called; 400 on failure) — full backend suite still 1244+/1244+ green (`./mvnw test`, run in full, not just the touched packages).

**4.2 — Rust: real process-tree kill.** `main.rs`'s `Child::kill()` calls (on `RunEvent::Exit` and in `restart_agent`) only ever terminated the sidecar JVM itself — not its descendants. MinIO is a direct, persistent child so this incidentally worked for it, but Postgres is started via `pg_ctl start`, which launches the real `postgres.exe` and then *exits itself* — so by the time we'd kill the tree, Postgres was already not a live descendant of the JVM at all, and got orphaned on every restart (confirmed live: repeated restarts left `postgres.exe` bound to 5432 across app relaunches, causing the exact "puerto ya está en uso" error the user kept hitting). New `kill_process_tree(&Child)` shells out to `taskkill /F /T /PID <pid>` instead — still not sufficient for Postgres alone (see 4.3), but does now correctly clean up MinIO and the JVM itself unconditionally.

**4.3 — Frontend: `onRestart` does a graceful stop first.** Because of 4.2's `pg_ctl` gap, the *only* reliable way to actually stop Postgres is its own `pg_ctl stop` — already wired via the existing `POST /api/stop` → `HubOrchestrator.stop()` → `bootstrapRunner.stopServices()` path. `Dashboard.tsx`'s new `onRestart` calls `stopServices()` first, then polls `getStatus()` (max ~5s, 300ms steps) until `postgres`/`server` leave `STOPPING`, before falling through to the hard `restart_agent` kill+respawn — layering the graceful path under the hard one instead of racing them. Verified live end-to-end on a clean instance: after this fix, a "Reiniciar" click leaves **zero** orphaned `postgres.exe`/`minio.exe` processes and a fresh instance starts clean on the next launch (previously it always left the old Postgres alive).

**4.4 — Frontend: the control-server port is ephemeral, so restarting needs a cache reset.** `HubControlServer.start()` binds `InetSocketAddress("127.0.0.1", 0)` — an OS-assigned port that changes on every JVM respawn. `ui/src/lib/api.ts` caches the port in a module-level variable *forever* once resolved; without invalidating it, any restart (this new button, or the pre-existing crash-screen "Reintentar") would leave the UI silently fetching against a dead port with no way to recover short of a full app relaunch. New `resetPortCache()` export, called by `onRestart` right before `invoke('restart_agent')`; `Dashboard.tsx`'s `watchAgentShell` effect now depends on a `restartNonce` counter bumped after every restart, so the port-polling watcher actually re-arms instead of staying settled from the first successful boot.

**4.5 — "Reiniciar" button + license lifecycle.** New always-visible "Reiniciar" button in the header (wired to `onRestart`). `LicenseCard` gained a second button, "Eliminar license.key" (only shown once a license exists, i.e. `status !== 'NONE'`), wired to a new `onRemoveLicense` that calls the new `removeLicense()` API function and then **also runs the same full restart `onRestart` does** — deleting the file alone doesn't help if services are already `RUNNING` (`start()` only fires when postgres is `STOPPED`/`ERROR`) or stuck in `ERROR` with no other recovery path, so this guarantees every "delete license" ends on a clean, freshly-booted "Sin licencia" state ready for a new key.

## 5. Why It Changed?
All four sub-fixes trace back to the same underlying gap: this plan's original design assumed license/service state was simple enough that installing a new key or hitting an error would always have an obvious next action available in the UI. Real testing (the user swapping license keys and hitting a genuine, reproducible port-conflict loop) showed that assumption breaking down in exactly the way `systematic-debugging` predicts — each layer (JVM-only kill, cached ephemeral port, license-file-only removal) looked correct in isolation but composed into a dead end with no button that actually worked. Fixing all four together, rather than patching only the one thing reported, was necessary because any one of them left alone would have reintroduced the same "stuck, nothing works" state on the next restart.

## 6. Verification
- Backend: `./mvnw test` (full suite, not scoped) → green, unchanged count + 4 new tests.
- `cd ember-hub/ui && npm run test` → 12/12 (unchanged 10 + 2 new: "Eliminar license.key" hidden when `NONE`, shown + wired otherwise).
- `npm run build` (ui) → clean. `cargo build --release` (`ember-hub/src-tauri`) → clean.
- Full pipeline rebuild (`build-installer.ps1 -Stage all`, required since backend code changed — reusing `-Stage installer` alone would have repackaged the previous jar, the exact mistake report 455 already found and fixed once).
- **Real end-to-end, repeated across multiple full uninstall→install→launch cycles**: confirmed clean startup (single Postgres/MinIO instance, correct process count — the ~15 `postgres.exe` processes seen are normal per-connection backends, not a double-start, verified by checking which single PID actually owns port 5432), confirmed "Reiniciar" respawns the JVM with **no leftover Postgres/MinIO** afterward (checked via `Get-NetTCPConnection`/`Get-Process` immediately after each click), and confirmed "Eliminar license.key" triggers the same clean restart (new JVM PID timestamped 1 second after the click).
