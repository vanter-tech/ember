# Report 452

## 1. Identification
- **Report Number:** 452
- **Task ID:** EMBER-HUB-V2 Task 4
- **Predecessor Task:** EMBER-HUB-V2 Task 3 (report 451)

## 2. Objective
Implement the Tauri native shell for Ember Hub: sidecar process spawn, `hub.env` first-run bootstrap with CSPRNG-generated secrets, port discovery, crash watcher, tray icon, close-means-stop-services behavior, and autostart — per `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md` Task 4.

## 3. Modified Files
- Create: `ember-hub/src-tauri/Cargo.toml`
- Create: `ember-hub/src-tauri/build.rs`
- Create: `ember-hub/src-tauri/tauri.conf.json`
- Create: `ember-hub/src-tauri/src/main.rs`
- Create: `ember-hub/src-tauri/icons/` (generated via `cargo tauri icon ../installer/ember-hub.ico`)
- Create: `ember-hub/ui/src/lib/agent-events.ts`
- Modify: `ember-hub/ui/src/components/Dashboard.tsx`
- Create: `ember-hub/.gitignore` (`ember-hub/` had none before this task)

## 4. What Changed?
Added the Rust/Tauri 2.x shell crate `ember-hub-shell`. On startup it writes `%ProgramData%\EmberHub\hub.env` if absent (first run after install) — `EMBER_HUB_*` paths/ports plus a `JWT_SECRET`/`PLATFORM_JWT_SECRET` pair generated with `rand`'s CSPRNG (`random_hex`), replacing the old Inno Setup `[Code]` section's hand-rolled, explicitly non-cryptographic LCG. It then spawns the bundled jpackage app-image (`Ember Hub.exe`, `resources` entry pointing at `../dist/app-image/Ember Hub`) as a child process with `SPRING_PROFILES_ACTIVE=hub` plus every `hub.env` key injected as an env var, reads the `PORT=<n>` line the sidecar prints once `HubControlServer` is listening, and exposes it to the frontend via a `get_port` command. A background thread watches the child for an unexpected exit and emits `agent-crashed`; a tray icon (Mostrar/Salir) is wired, and `RunEvent::Exit` kills the sidecar so quitting never orphans the Java process. Unlike printer-agent's shell (hide-to-tray on close), the window's `CloseRequested` handler is a deliberate no-op — closing the window here still means "stop everything," matching the old `HubDashboard`'s `windowClosing` behavior, since a headless Hub with a hidden window would otherwise leave Postgres/MinIO/the server running with no visible indicator.

On the frontend, `agent-events.ts` exposes `watchAgentShell` (polls `get_port` every 200ms as the primary readiness signal, `agent-crashed` event as a secondary one — same race-condition-proofing printer-agent's Task 4 needed), and `Dashboard.tsx` now gates its status-polling `useEffect` on `shellState === 'ready'`, rendering a "Iniciando Ember Hub…" screen while starting and a "Ember Hub no pudo iniciar." + **Reintentar** (`invoke('restart_agent')`) screen on crash/timeout.

**Context on how this task was found:** this session started with these exact files already present on disk but uncommitted (`git status` showed `ember-hub/src-tauri/` untracked, `agent-events.ts` untracked, `Dashboard.tsx` modified) — evidently written in a prior session that stopped before Steps 6–8 (report/PROGRESS/commit). Read-only comparison against the plan's Task 4 code blocks found the existing `main.rs`/`tauri.conf.json`/`Cargo.toml` matching essentially verbatim, already incorporating the three fixes printer-agent's own Task 4 had needed (correct `use tauri_plugin_autostart::ManagerExt`, `beforeBuildCommand` written as `npm --prefix ui ...` rather than a `cd`-relative form, and `get_port`-polling instead of relying solely on the `agent-ready` event) — no code changes were needed. The only real gap found was `ember-hub/.gitignore` not existing at all (unlike `printing-agent/.gitignore`, which already has `target/` + `src-tauri/gen/`), which would have swept the ~450MB `src-tauri/target/` build directory and `src-tauri/gen/` (Tauri's generated capability schemas) into the commit — created with `src-tauri/target/` + `src-tauri/gen/`.

## 5. Why It Changed?
The plan's own Task 4 header note (mirroring printer-agent's Task 4) anticipated needing runtime/compile fixes against the actually-installed `tauri`/`tauri-plugin-autostart` crate versions; whoever wrote this code before the commit had already applied printer-agent's lessons directly rather than hitting the same bugs again, so nothing further was required. The `.gitignore` gap is a straightforward oversight from `ember-hub/` never having had a Rust subproject before this task — `printing-agent/` had the same gap until its own Task 4, fixed there the same way.

## 6. Verification
- `cd ember-hub/ui && npm run test` → 8/8 pass (unchanged, no new tests added — matches printer-agent's Task 4, which also added no tests for the pure Tauri-wiring layer; `ServiceCard`/`LicenseCard` coverage from Task 3 stands).
- `npm run build` → clean static build.
- `cargo tauri build --debug` (from `ember-hub/src-tauri`) → compiles cleanly (fresh `cargo build` of the crate itself; dependency tree already cached from a prior session) and produces both `target/debug/ember-hub-shell.exe` and an NSIS debug bundle.
- Manual smoke test (launched the debug exe directly via `Start-Process`, inspected with `Get-Process`/`Get-NetTCPConnection`, screenshotted the live window):
  - `ember-hub-shell.exe` starts and creates `%ProgramData%\EmberHub\{hub.env, data/postgres, data/minio, logs, backups}` on first launch — confirms `ensure_hub_env`'s bootstrap path.
  - The bundled `Ember Hub.exe` sidecar was spawned but exited quickly because this dev machine's Docker Desktop/WSL stack already owns TCP 5432 and 9000 (the main backend's local dev Postgres/MinIO) — a real port conflict on this machine, not a defect in the shell or sidecar code. This is exactly the failure path Task 4 is meant to handle: the crash watcher fired `agent-crashed`, and a screenshot confirms `Dashboard.tsx` correctly rendered "Ember Hub no pudo iniciar." with a working **Reintentar** button — i.e. the crash-recovery UI path was exercised for real, even though a full boot-to-`RUNNING` pass wasn't possible in this environment. A full green run (all 3 services reaching `RUNNING`) needs a machine without something already bound to 5432/9000, which is squarely Task 6's clean-machine `VERIFY.md` territory.
  - Test process stopped via `Stop-Process` after the screenshot; `%ProgramData%\EmberHub\hub.env` (with freshly generated secrets) is left on this dev machine as a side effect of the smoke test, same class of local-state footprint report 441 noted for printer-agent's equivalent verification.
- Backend/Java: unchanged, no `mvn test` run needed (no Java source touched by this task).
