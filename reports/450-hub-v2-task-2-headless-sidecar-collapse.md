# Report 450

## 1. Identification
- **Report:** 450
- **Task ID:** EMBER-HUB-V2 Task 2
- **Predecessor Task:** report 449 (EMBER-HUB-V2 Task 1 — `HubOrchestrator` + `HubControlServer`)

## 2. Objective
Collapse the `hub` Spring-profile entry point to a headless sidecar mode (drop the Swing `HubDashboard`/`HubTrayIcon` window, run only `HubControlServer` + `DefaultHubOrchestrator` from Task 1), printing `PORT=<n>` to stdout so a future Tauri shell (Task 4) can discover it — same contract as printer-agent's `Main`.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- Delete: `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`
- Delete: `backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java`
- Modify: `ember-hub/jlink-modules.txt`

## 4. What Changed?
- `EmberApplication.main`'s hub branch no longer calls `HubDashboard.launch(args)`. It now calls a new private `runHubSidecar(args)`: builds `HubProperties.fromEnvironment()`, constructs `DefaultHubOrchestrator`, starts a `HubControlServer` on it, prints `PORT=<n>` to stdout (flushed immediately), calls `orchestrator.start(args)` to auto-start services (replacing `HubDashboard.launch`'s `--autostart` handling — the Tauri shell will always pass this), and registers a shutdown hook that stops the orchestrator then the control server.
- Removed the `System.setProperty("java.awt.headless", "false")` line — it existed only so Swing could open a real window/tray icon; the sidecar never touches AWT/Swing, so Spring Boot's default `headless=true` is correct.
- Deleted `HubDashboard.java` (249 lines, the Swing launcher/dashboard) and `HubTrayIcon.java` (confirmed unused in main source beyond a stray comment mention in `HubProvisioningRunner.java`, and confirmed via `grep -rl "HubDashboard\|HubTrayIcon" backend/src/test` that no test references either class).
- `ember-hub/jlink-modules.txt` += `jdk.httpserver` after `jdk.charsets` — `com.sun.net.httpserver.HttpServer` (used by `HubControlServer`) lives in that JDK-specific module, which the `java.se` aggregator does not include; without it the jlink runtime image would throw `NoClassDefFoundError` on the first control-server request (the exact gotcha printer-agent's Task 2 hit for the same reason).

## 5. Why It Changed?
This is Task 2 of `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md`: the Tauri shell (Task 4) needs the Java process to run as a headless child it spawns and talks to over HTTP, not as a window-owning Swing app — identical architectural shift to printer-agent v2's Task 2. `isHubProfile()`'s environment-read-before-Spring logic is unchanged (Spring's DataSource autoconfiguration would otherwise race Postgres bootstrap).

## 6. Verification
- `cd backend && ./mvnw.cmd test` — **1244/1244** pass, unchanged from the end of Task 1 (deleting the untested Swing classes removed no tests, matching the plan's expectation).
