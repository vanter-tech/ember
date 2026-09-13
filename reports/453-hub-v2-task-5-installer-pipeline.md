# Report 453

## 1. Identification
- **Report Number:** 453
- **Task ID:** EMBER-HUB-V2 Task 5
- **Predecessor Task:** EMBER-HUB-V2 Task 4 (report 452)

## 2. Objective
Replace Ember Hub's Inno Setup installer pipeline with the Tauri bundler (NSIS), per `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md` Task 5.

## 3. Modified Files
- Modify: `ember-hub/build-installer.ps1`
- Create: `ember-hub/src-tauri/installer-hooks.nsh`
- Modify: `ember-hub/src-tauri/tauri.conf.json` (wire `bundle.windows.nsis.installerHooks`)
- Delete: `ember-hub/installer/EmberHub.iss`
- Delete: `ember-hub/installer/Iniciar Ember Hub.cmd`

## 4. What Changed?
`build-installer.ps1`'s `Build-Installer` no longer shells out to `iscc`; it runs `cargo tauri build` from `ember-hub/src-tauri` (mirroring printing-agent's own already-fixed Task 5 pattern verbatim: a `Clear-ReadOnlyRecurse` pass over any previous `target/{release,debug}/app-image` copy before building — jpackage's launcher exe is read-only and Tauri's resource-copy step can't overwrite a read-only destination on a second build — and a locally relaxed `$ErrorActionPreference` around the `cargo tauri build` call, since PowerShell 5.1 treats `cargo`/`tauri-cli`'s non-fatal stderr progress lines as a terminating error under `Stop`). It copies the produced `target\release\bundle\nsis\*-setup.exe` to `dist/EmberHubSetup-<version>.exe`, the same output path `deploy/publish-installer.sh` already expects. `Build-AppImage` no longer copies `Iniciar Ember Hub.cmd` into the app-image (Task 4's Rust shell reads `hub.env` itself; the batch shim has no reader left). `Read-BuildEnv`/`build.env` are left in place but now unused by `Build-Installer` — the activation/heartbeat URLs and secrets it used to inject via `/D` Inno defines are now `ensure_hub_env`'s baked-in defaults in Task 4's Rust code.

New `ember-hub/src-tauri/installer-hooks.nsh` replaces `EmberHub.iss`'s `[Dirs]`/`[Run]`/`[UninstallRun]`/`CurUninstallStepChanged`: `NSIS_HOOK_POSTINSTALL` creates the `%COMMONAPPDATA%\EmberHub\{data\postgres,data\minio,logs,backups}` tree and adds the inbound firewall rule (delete-then-add, so a reinstall doesn't stack duplicates); `NSIS_HOOK_POSTUNINSTALL` removes the firewall rule and prompts (Sí/No, default No) to wipe `%COMMONAPPDATA%\EmberHub` entirely. The old `.iss`'s Pascal `[Code]` section (LCG-seeded `hub.env` secret generation) has no NSIS equivalent needed — that logic already moved to Rust's `ensure_hub_env`/`random_hex` in Task 4, so this hook only touches directories/firewall/uninstall, never `hub.env` itself. `tauri.conf.json` gained `bundle.windows.nsis.installerHooks: "installer-hooks.nsh"` (Task 4 left this unset since the file didn't exist yet at that point). `EmberHub.iss` and `Iniciar Ember Hub.cmd` were `git rm`'d; `ember-hub.ico`/`make-icon.ps1`/`hub.env.example` stay (the icon is still `cargo tauri icon`'s source, per Task 4).

## 5. Why It Changed?
Same rationale as printing-agent's own Task 5: the Tauri bundler is now the single source of truth for the packaged app (it already owns the app-image resource embedding from Task 4), so a separate Inno Setup script duplicating `[Files]`/`[Dirs]`/firewall logic would drift. The `.nsh` hook mechanism is Tauri's supported way to inject NSIS steps the bundler itself doesn't model (directory pre-creation, firewall rules, an uninstall data-wipe prompt) without hand-writing a full custom NSIS script. No CI job changes this task: `.github/workflows/lint.yml`'s `build-hub` job only compiles the frontend's Hub build variant and has never invoked `build-installer.ps1` — standing up Windows CI for the full pipeline needs `build.env` secrets and the portable Postgres/MinIO vendor download, out of this plan's scope (flagged in the plan itself as a follow-up, not silently assumed done here).

## 6. Verification
- `pwsh` isn't on this machine's default `PATH` inside the Bash tool (the first attempt silently did nothing — `pwsh: command not found` was swallowed by a piped `tail`, a real gotcha worth flagging: piping a failed command into `tail` masks the failure's exit code). Re-ran via the PowerShell tool instead: `powershell -File ember-hub\build-installer.ps1 -Stage installer` (with `%USERPROFILE%\.cargo\bin` prepended to `PATH` for `cargo`).
- Full run succeeded end to end (existing app-image from Task 4's testing was reused, so it skipped straight to the Tauri build): `cargo tauri build` compiled the `ember-hub-shell` release binary (`Finished release profile [optimized] target(s) in 3m 23s`), patched it with NSIS bundle info, and `makensis` produced `target\release\bundle\nsis\Ember Hub_0.1.0_x64-setup.exe`, copied to `ember-hub/dist/EmberHubSetup-0.2.4.exe` (177 MB).
- Confirmed the custom hooks actually took effect, not just that the build didn't error: `grep`'d the generated `ember-hub/src-tauri/target/release/nsis/x64/installer.nsi` for the firewall rule's literal name ("Ember Hub 8080") — present, meaning `installerHooks` correctly pulled `installer-hooks.nsh`'s macros into the compiled installer script (a bad hook reference or NSIS syntax error would have failed `makensis`, not silently been ignored).
- Did not run a full clean install/uninstall of `EmberHubSetup-0.2.4.exe` (dirs/firewall rule/uninstall-prompt behavior) — that is explicitly Task 6's clean-machine `VERIFY.md` checklist, not this task's scope.
- Backend/Java: unchanged, no `mvn test` run needed (no Java source touched by this task).
