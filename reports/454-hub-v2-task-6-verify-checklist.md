# Report 454

## 1. Identification
- **Report Number:** 454
- **Task ID:** EMBER-HUB-V2 Task 6 (final task of the plan)
- **Predecessor Task:** EMBER-HUB-V2 Task 5 (report 453)

## 2. Objective
Write `ember-hub/VERIFY.md`, the manual clean-machine verification checklist for the Tauri-shell Hub installer, per `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md` Task 6 — the last task in the plan.

## 3. Modified Files
- Create: `ember-hub/VERIFY.md`

## 4. What Changed?
Added a 13-item pass/fail checklist, same format/convention as `printing-agent/VERIFY.md` (metadata table, `[ ]`→`[x]`/`[F]` per item, closing Go/No-Go + blocking-failures section). Covers: clean install with no prior Java and first-launch `hub.env` generation (distinct 64-hex secrets); the no-license → install-license → auto-start-all-3-services flow; Abrir en navegador; Detener; the heartbeat badge advancing on its own; autostart on log-on; `hub.env` surviving an in-place update; uninstall keep-data vs. remove-data (the NSIS prompt from Task 5's `installer-hooks.nsh`); the firewall rule (`netsh advfirewall firewall show rule name="Ember Hub 8080"`) with no inbound-access prompt; sidecar crash recovery (kill `java.exe`, confirm the Reintentar screen, confirm respawn); and the window-close behavior that is deliberately different from printer-agent (closing the window stops everything here, rather than hiding to tray), plus the tray's Mostrar/Salir.

This is a documentation-only task — no source files were touched, matching the plan's own note that Task 6 "produces nothing consumed by later tasks." **This closes out `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md` — all 6 tasks are now code/doc-complete** (Tasks 1–5: reports 449–453; Task 6: this report). The only work remaining on EMBER-HUB-V2 is the ops action this checklist describes: actually running it on a clean Windows machine/VM, which is explicitly out of scope for an agent to perform (no such machine available here, same constraint printer-agent's own Task 6b is still waiting on).

## 5. Why It Changed?
Every native-shell plan in this codebase (printer-agent's Task 6a, now Hub's Task 6) ends in a manual checklist rather than an automated test, because the things being verified — a real NSIS install/uninstall, UAC prompts, Windows Firewall rule registration, autostart-on-logon, and killing a real child process to test crash recovery — cannot be exercised inside this dev environment (this session already hit a live example of that constraint during Task 4's smoke test, where the dev machine's own Docker-held ports 5432/9000 blocked a full green boot). Writing the checklist now, rather than deferring it, means the next person with a clean machine has an exact, already-cross-checked-against-the-real-code script to run instead of having to reconstruct expected behavior from the plan or the source.

## 6. Verification
- No build/test run — this task added only a markdown file, no source changes.
- Content was cross-checked against what Tasks 1–5 actually built in this session (not copied blind from the plan): the firewall rule name/scope was confirmed present in the actual generated NSIS script during Task 5's verification (report 453); the `hub.env` bootstrap behavior (fresh 64-hex secrets, directories created) and the "Ember Hub no pudo iniciar." + Reintentar crash screen were both directly observed live during Task 4's manual smoke test (report 452); the close-stops-services (not close-to-tray) behavior matches the explicit design comment in `ember-hub/src-tauri/src/main.rs`'s `on_window_event` handler.
