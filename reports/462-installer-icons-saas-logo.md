# Report 462

## 1. Identification
- **Report Number:** 462
- **Task ID:** Rebrand installer icons to the SaaS favicon (ad-hoc, both Ember Hub v2 and Print Agent v2)
- **Predecessor Task:** report 461 (QR join / export date bugs)

## 2. Objective
User request, ahead of publishing the two newly-built installers: use the same logo as the SaaS frontend's favicon (`frontend/public/ember_logo.ico` — a white flame outline on a rounded dark-red square) as the icon for both the Ember Hub and Print Agent installers, replacing the old solid-flame-silhouette icon both previously shared.

## 3. Modified Files
- Modify: `ember-hub/installer/ember-hub.ico`, `printing-agent/installer/ember-agent.ico` (source icon swap)
- Modify: `ember-hub/src-tauri/icons/*`, `printing-agent/src-tauri/icons/*` (regenerated via `cargo tauri icon`)
- Modify: `ember-hub/src-tauri/tauri.conf.json`, `printing-agent/src-tauri/tauri.conf.json` (`bundle.windows.nsis.installerIcon`/`uninstallerIcon`)

## 4. What Changed?
Confirmed first that `ember-hub.ico` and `ember-agent.ico` were byte-identical (9748 bytes each) and, per `ember-hub/installer/make-icon.ps1`, both originally generated the same way (`frontend/src/assets/ember.png` wrapped into a single-frame 256×256 PNG-in-ICO) — so the SaaS favicon (also a single-frame 256×256 PNG-in-ICO, confirmed by parsing its `ICONDIR`) is a drop-in replacement with no format/quality regression. Copied `frontend/public/ember_logo.ico` over both installer `.ico` files, then ran `cargo tauri icon <path>.ico` in both `src-tauri/` dirs to regenerate the full icon sets (`icons/icon.ico`, `.icns`, all Windows/Android/iOS PNG sizes) Tauri needs.

Rebuilt both app-images (`-Stage appimage`, which bakes the icon into the jpackage-generated `Ember Hub.exe`/`Ember Agent.exe` via `--icon`) and both NSIS installers (`-Stage installer`). Verified via the real shell icon API (`SHGetFileInfo`, the same call Explorer itself uses — plain `Icon.ExtractAssociatedIcon()` was tried first and returned a generic default icon for both, which turned out to be accurate, not a .NET extraction quirk): the jpackage app-image exe picked up the new logo correctly, but **the NSIS installer wrapper itself (`EmberHubSetup-0.2.4.exe`, `EmberAgentSetup-0.1.1.exe`) still showed a generic default icon.**

Root-caused by reading `tauri-bundler` 2.9.4's own source (`bundle/windows/nsis/mod.rs`): the installer/uninstaller icon is controlled by a **separate** config field, `nsis.installer_icon`/`nsis.uninstaller_icon`, which is `None` unless explicitly set in `tauri.conf.json` — it does **not** automatically fall back to the general `bundle.icon` used for the app itself. Confirmed directly in the generated NSIS script: `!define INSTALLERICON ""` (empty), which is why `makensis` fell back to its own default. Added `"installerIcon": "icons/icon.ico"` and `"uninstallerIcon": "icons/icon.ico"` under `bundle.windows.nsis` in both `tauri.conf.json` files, rebuilt both installers again, and confirmed the generated NSIS script now has the real path (`!define INSTALLERICON "...\icons\icon.ico"`).

## 5. Why It Changed?
Direct user request for the logo swap. The `installerIcon` gap is a real, previously-latent config omission in this codebase's Tauri setup (not something introduced by this change) — every installer built by either app's pipeline before this fix would have shown a generic default icon on the setup `.exe` itself, even though the *installed* app always had the correct branded icon. It just happened to surface now because this was the first time anyone looked closely at the installer file's own icon rather than the running app's.

## 6. Verification
- Confirmed the source `.ico` format compatibility by parsing the `ICONDIR` header directly (both single-frame 256×256) before swapping, avoiding a blind replace.
- `cargo tauri icon` ran clean for both apps (no errors, full icon sets regenerated).
- Rebuilt app-image + installer for both apps twice (once before finding the `installerIcon` gap, once after fixing it).
- **Real verification, not assumed**: used `shell32.dll`'s `SHGetFileInfo` (the same API Windows Explorer itself uses to render file icons) to extract and visually confirm the actual icon on all four artifacts — `ember-hub/dist/app-image/Ember Hub/Ember Hub.exe` (correct from the first rebuild, jpackage `--icon` was never the problem), and both `EmberHubSetup-0.2.4.exe`/`EmberAgentSetup-0.1.1.exe` (wrong — generic default — until the `installerIcon` config fix, then confirmed correct after rebuilding again).
- No `npm test`/`mvn test` run — this task touched only icon assets and Tauri bundler config, no application code.
