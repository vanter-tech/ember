# Report 456

## 1. Identification
- **Report Number:** 456
- **Task ID:** EMBER-HUB-V2 UI fixes (ad-hoc, post-plan) — license dialog not opening, frozen PostgreSQL card, log styling, badge alignment
- **Predecessor Task:** report 455 (real-install crash fix)

## 2. Objective
User reported 4 issues after the real-install crash was fixed: (1) "Seleccionar license.key…" doesn't open a dialog, (2) the PostgreSQL card stays expanded/"frozen", (3) the log box needs `#8c1717` background + white text + spacing between lines instead of running together, (4) the "Sin licencia" badge should sit on the right of the header, matching the service cards' `Detenido`/`Error` badges.

## 3. Modified Files
- Modify: `ember-hub/src-tauri/Cargo.toml` (+ `tauri-plugin-process`)
- Modify: `ember-hub/src-tauri/src/main.rs` (register the process plugin)
- Create: `ember-hub/src-tauri/capabilities/default.json`
- Modify: `ember-hub/ui/src/components/Card.tsx` (optional right-aligned `badge` slot)
- Modify: `ember-hub/ui/src/components/LicenseCard.tsx` (use the new badge slot)
- Modify: `ember-hub/ui/src/components/ServiceCard.tsx` (log box colors + per-line spacing)

## 4. What Changed?

**4.1 — License dialog silently did nothing.** `ember-hub/src-tauri` had **no `capabilities/` directory at all**. Tauri v2 denies every plugin command by default unless a capability file explicitly grants it — `@tauri-apps/plugin-dialog`'s `open()` call was being silently rejected (the code never `.catch()`s it, so nothing visibly happened, matching the reported symptom exactly). Added `capabilities/default.json` granting `core:default`, `dialog:default`, `shell:default`, `autostart:default`, `process:default` to the `main` window. While fixing this, found a second, same-class bug: the "Salir" button calls `@tauri-apps/plugin-process`'s `exit()`, but `tauri-plugin-process` wasn't even a Rust dependency or a registered plugin — that command didn't exist at all. Added the dependency and `.plugin(tauri_plugin_process::init())`.

**4.2 — PostgreSQL card "frozen."** `ServiceCard`'s expand/collapse is purely `phase`-driven (`expanded = starting || stopping || isError`); there's no separate animation/state bug. Before 4.1's fix, no `license.key` could ever be installed, so `postgres` stayed in `ERROR` forever — the card was never actually stuck, it just had no way to leave the state that keeps it expanded. Not expected to need its own fix; verify after 4.1.

**4.3 — Log styling.** `ServiceCard`'s log container: `bg-foreground text-background` → `bg-[#8c1717] text-white`; each `<p>` line (both the simulated boot/shutdown log lines and the real error message) gained `mb-1 last:mb-0` so consecutive lines don't run together.

**4.4 — Badge alignment.** The shared `Card` component's header row only ever rendered icon+title — `LicenseCard` was rendering its `Badge` in a separate row below, left-aligned, unlike `ServiceCard` (which hand-rolls its own header with the title as `flex-1` so the badge lands at the right edge). Added an optional `badge?: ReactNode` prop to `Card`, rendered after a now-`flex-1` title; `LicenseCard` passes its status `Badge` there instead of rendering it in the body.

## 5. Why It Changed?
4.1 is a real, previously-unexercised gap: nothing in this codebase had used `tauri-plugin-dialog` or needed a JS-triggered `exit()` before Ember Hub's Task 4 (`printing-agent` doesn't have a file-picker or an explicit exit button — it uses close-to-tray), so the missing capabilities file and the missing `tauri-plugin-process` registration were never caught by any earlier manual smoke test, including this session's own report 455 verification (which only exercised the no-license error path, never clicked the button). 4.3/4.4 are direct visual requests from the user, applied with the minimal component change each needed (no new abstractions beyond the one optional `badge` slot, reused as-is by nothing else yet).

## 6. Verification
- `cd ember-hub/ui && npm run test` → 8/8 pass (unchanged — no test asserted on the old classNames).
- `npm run build` → clean.
- `cargo build --release` (`ember-hub/src-tauri`) → compiles clean with the new `tauri-plugin-process` dependency.
- Full pipeline rebuild (`build-installer.ps1 -Stage installer`) → fresh `EmberHubSetup-0.2.4.exe`.
- **Real end-to-end test**: uninstalled the previous test install, silently installed the fresh one, launched the real `ember-hub-shell.exe`. Screenshotted the dashboard: log box now shows white text on `#8c1717`; "Sin licencia" badge now sits at the right of the "Licencia" header, matching `Detenido`/`Error` on the other cards. Used Windows UI Automation (`InvokePattern` on the button, found by name — mouse-coordinate clicking proved unreliable because the window kept drifting between screenshots) to invoke "Seleccionar license.key…": the real native "Abrir" file picker opened inside the app window — confirmed via screenshot. Closed it with Escape.
- **Not verified**: the PostgreSQL card actually collapsing after a real license is installed — no test `license.key` exists in this repo (private signing key is intentionally never committed), so this needs the user's own real license file. The code path has no separate bug beyond 4.1 as far as static reading shows; flagged to the user to confirm on retest.
- **Incident during verification**: closing the file dialog with a `SendKeys` Escape landed on the wrong (unfocused) window and typed stray characters into a chat textbox open behind the Ember Hub window on the user's desktop. Not auto-corrected (to avoid making it worse with more blind automation) — flagged directly to the user to clear manually. Coordinate-based mouse automation was abandoned mid-session in favor of UI Automation's `InvokePattern` specifically because of this class of risk.
