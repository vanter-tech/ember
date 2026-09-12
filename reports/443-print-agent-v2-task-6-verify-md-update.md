# Report 443 — PRINT-AGENT-V2 Task 6: `VERIFY.md` update for the Tauri shell

**Predecessor Task:** report 442 — Task 5 (installer pipeline: Tauri bundler replaces Inno Setup)

## Objective
Update `printing-agent/VERIFY.md`'s manual checklist to describe the Tauri shell (instead of
the retired Swing dashboard/Inno Setup installer) and add the new sidecar crash-recovery and
orphan-process checks called for by the plan's Task 6.

## Modified Files
- `printing-agent/VERIFY.md`

## What Changed?
- Intro line updated: verifies the installer built via `cargo tauri build` / the NSIS bundler,
  not Inno Setup / `iscc`.
- Checklist items 1–10 reworded to describe the Tauri window (title/tray/autostart via the
  `tauri-plugin-autostart`, not a `{commonstartup}` shortcut with `--tray`) instead of the old
  Swing `Ember Agent.exe` dashboard; item 4 now notes manual test prints correctly do **not**
  add an "Actividad" row (matches existing `LocalControlServer` behavior, not a regression).
- Added 3 new items:
  - **11. Sidecar crash recovery** — kill the `java.exe` child directly, expect the crash
    screen + **Reintentar** to respawn it without reinstalling.
  - **12. Close-to-tray, no orphaned process** — close (X) hides to tray with the sidecar
    still running; **Salir** kills both processes cleanly.
  - **13. Force-kill the shell, no orphan** — kill the Tauri parent itself and record whether
    the `java.exe` child survives (turns the spec §7 orphan-process risk into an explicit,
    checked item instead of an assumed-fixed one).
- Step 2 of the plan (actually running the checklist on a clean Windows machine/VM) is an ops
  action outside this session's reach — left as a pending manual run, per the plan's own
  framing of that step as "not something to automate."

## Why It Changed?
Task 4/5 replaced the Swing dashboard and Inno Setup installer with a Tauri+WebView2 shell and
an NSIS bundle produced by `cargo tauri build`; the old checklist's wording (dashboard, Inno
"remove credential?" prompt text, `--tray` shortcut) no longer matches what a tester will see.
The plan also calls out the Tauri-parent/Java-sidecar process split as a new failure surface
(crash, close-to-tray, force-kill) that the old single-process Swing app never had, so those
need their own explicit pass/fail checks before the installer is considered verified.
