# Report 232 — HUB-01-10: HubTrayIcon

## 1. Identification
- **Report:** 232
- **Task ID:** HUB-01-10
- **Predecessor Task:** HUB-01-09 (report 231)

## 2. Objective
Add a minimal Windows system-tray presence for the Ember Hub profile — an icon with "Abrir Ember Hub" (opens `http://localhost:8080` in the default browser) and "Salir" (exits the process), per plan Task 10 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java` (new)

## 4. What Changed?
New `@Component @Profile("hub")` class `HubTrayIcon`. An `@EventListener(ApplicationReadyEvent.class)` method `show()` checks `SystemTray.isSupported()` — logs a warning and returns (no-op) if unsupported (e.g. headless environments), otherwise builds a `PopupMenu` with "Abrir Ember Hub"/"Salir" items, draws a 16x16 solid-red-circle `Image` at runtime (`createIcon()`), and adds a `TrayIcon` to `SystemTray.getSystemTray()`. Clicking the icon or "Abrir Ember Hub" calls `openBrowser()` (`Desktop.getDesktop().browse(...)`); "Salir" calls `System.exit(0)`. Taken verbatim from the plan, no drift against current code (fresh `tray/` package, no naming conflicts with the existing `hub/config`, `hub/license`, `hub/bootstrap` packages).

No new automated test: `java.awt.SystemTray` requires a real desktop session — headless CI has none, so `SystemTray.isSupported()` returns `false` there, which is exactly the fallback branch this task codes for rather than something worth asserting on in a unit test. Manual verification is deferred to HUB-01-11 (real Windows + portable Postgres).

## 5. Why It Changed?
Per spec §2.3 (`docs/superpowers/specs/ember_hub.md`), Ember Hub needs a minimal visible presence on the host PC once it's running standalone (no browser tab pinned to it by default) — a tray icon lets a restaurant's staff confirm the Hub is alive and gives them a one-click way to reopen the app or shut it down, without requiring a full desktop app shell. Auto-updater notifications (spec §2.10) are deliberately out of scope here — that needs a version to compare against from HUB-02's sync response, not yet built.

## Verification
`cd backend && ./mvnw test` — PASS, 807/807 (same count as report 231; `@Profile("hub")` keeps this class out of the default profile's context).
