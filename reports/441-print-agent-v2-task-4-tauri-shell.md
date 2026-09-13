# Report 441

## 1. Identification
- **Report Number:** 441
- **Task ID:** PRINT-AGENT-V2 Task 4
- **Predecessor Task:** PRINT-AGENT-V2 Task 3 (report 440)

## 2. Objective
Implement the Tauri native shell for the printer agent: sidecar process spawn, port discovery, crash watcher, tray icon, close-to-tray, and autostart — per `docs/superpowers/plans/2026-09-12-printer-agent-v2-tauri-shell.md` Task 4.

## 3. Modified Files
- Create: `printing-agent/src-tauri/Cargo.toml`
- Create: `printing-agent/src-tauri/build.rs`
- Create: `printing-agent/src-tauri/tauri.conf.json`
- Create: `printing-agent/src-tauri/src/main.rs`
- Create: `printing-agent/src-tauri/icons/` (generated via `cargo tauri icon`)
- Create: `printing-agent/ui/src/lib/agent-events.ts`
- Modify: `printing-agent/ui/src/components/Dashboard.tsx`
- Modify: `printing-agent/ui/package-lock.json` (lockfile sync from `npm install`)
- Modify: `printing-agent/.gitignore` (+ `src-tauri/gen/`)

## 4. What Changed?
Added the Rust/Tauri 2.x shell crate `ember-agent-shell`: on startup it spawns the jpackage app-image (`Ember Agent.exe`, bundled as a Tauri resource) as a headless child process, reads the `PORT=<n>` line from its stdout, and exposes that port to the frontend via a `get_port` Tauri command. A background thread watches the child for an unexpected exit and emits an `agent-crashed` event. A tray icon (Mostrar/Salir) and a `WindowEvent::CloseRequested` handler implement close-to-tray (hide instead of quit); the app's `RunEvent::Exit` handler kills the sidecar so a real quit never orphans the Java process. `tauri-plugin-autostart` registers the shell to launch on login.

On the frontend, `agent-events.ts` exposes `watchAgentShell`, and `Dashboard.tsx` now gates its status-polling `useEffect` on the shell reaching `'ready'`, showing a startup/crash screen with a **Reintentar** button (`invoke('restart_agent')`) otherwise.

Three real bugs surfaced during verification and were fixed (all beyond the plan's literal code, which didn't compile/run as written against the installed toolchain):
1. **`tauri.conf.json`'s `beforeBuildCommand`** — the plan's literal `"cd ../ui && npm install && npm run build"` assumes the command runs from `src-tauri/`, but Tauri actually runs `beforeBuildCommand` from the app root (`printing-agent/`, the parent of `src-tauri/`). Changed to `"npm --prefix ui install && npm --prefix ui run build"`.
2. **`main.rs` missing import** — `app.autolaunch()` requires `tauri_plugin_autostart::ManagerExt` in scope for the installed `tauri-plugin-autostart` 2.5.1; the plan's snippet omitted it. Added `use tauri_plugin_autostart::{MacosLauncher, ManagerExt};`.
3. **Race condition in `agent-events.ts`** — the plan's `watchAgentShell` relied solely on listening for the Rust-emitted `agent-ready` event. Since `spawn_agent` runs during Tauri's `.setup()` (before the webview has loaded and React has mounted `useEffect`), the event can fire before any listener is registered and is silently dropped (Tauri doesn't buffer events for late subscribers) — verified live: the dashboard was stuck on "El agente no pudo iniciar" even though the Java sidecar was healthy and its `LocalControlServer` was reachable via `curl`. Fixed by polling the already-existing `get_port` command (every 200ms) as the primary readiness signal, keeping the `agent-crashed` event listener (crashes happen after readiness, not at the same race window). Also fixed `restart_agent` in `main.rs`, which spawned a new sidecar without killing the previous one — every retry would have leaked an orphaned Java process; it now kills the existing child first.

## 5. Why It Changed?
The plan's own Task 4 preamble anticipated needing to fix compile-time API mismatches against the installed `tauri`/`tauri-plugin-autostart` versions (items 1–2), which is exactly what came up. Item 3 is a genuine functional defect independent of this machine's environment — without it, the dashboard would practically never show up for a real user, since a warm JVM easily starts fast enough to win the race against React mounting. Fixing it was necessary to meet Task 4's actual goal (a working native window, full parity with the old Swing dashboard), not just a passing `cargo build`.

Toolchain-wise, this machine had no Rust/Cargo, no MSVC Build Tools (a hard Tauri-on-Windows prerequisite for the linker), and Windows Defender initially locked freshly-built/copied executables during `cargo tauri build`'s resource-copy step — all installed/resolved with the user's explicit approval before verification could proceed. Verification also surfaced that the local `printing-agent/dist/app-image/` and its embedded jlink runtime were stale (pre-dating Task 2's headless `Main`/`jdk.httpserver` addition) and that `build-installer.ps1`'s jar selection (`Get-ChildItem ... | Select-Object -First 1`) picked a leftover `0.1.0-SNAPSHOT` jar over the current `0.1.1-SNAPSHOT` one when both existed in `target/` — both were local-environment/build-artifact issues (rebuilt the runtime and app-image, removed the stale jars), not code defects, and are noted here for context but are not part of this task's file changes; the `build-installer.ps1` jar-selection bug is Task 5's territory (installer pipeline rewrite) and should be fixed there.

## 6. Verification
- `cd printing-agent/ui && npm run test` → 4/4 pass (unchanged).
- `npm run build` → clean static build.
- `cargo tauri build --debug` → compiles and bundles (438 crates, NSIS debug bundle produced).
- Manual smoke test (launched the debug exe directly, confirmed via `Get-Process`/`Get-NetTCPConnection`/`curl`/screenshots):
  - Sidecar (`Ember Agent.exe`) spawns as a child process.
  - `LocalControlServer` binds `127.0.0.1` and responds correctly to `/api/status` (verified via `curl`).
  - Dashboard renders past the loading screen once the race-condition fix was applied — confirmed via screenshot showing the full UI (Impresora, Actividad, footer actions) and live-polled status data (TCP connections established between the webview and the control server).
  - Close-to-tray verified: sending `WM_CLOSE` to the window hides it (`IsWindowVisible` → false) while both sidecar processes remain alive.
  - Deeper checks (sidecar crash recovery, force-kill orphan behavior, tray menu clicks, a real installer run) are explicitly Task 6's `VERIFY.md` checklist, run on a clean machine — out of this task's scope.
- Backend/Java: unchanged, no `mvn test` run needed (no Java source touched by this task).
