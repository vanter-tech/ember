# Report 438 — PRINT-AGENT-V2 Task 1: `LocalControlServer`

## 1. Identification
- **Report:** 438
- **Task ID:** PRINT-AGENT-V2 Task 1
- **Predecessor Task:** report 437 (Ember Hub installer rebuild + `build-installer.ps1` fix) — unrelated area, first task of a new plan.

## 2. Objective
Add the loopback-only HTTP control server (`LocalControlServer`) that the future Tauri shell (Task 4) will poll/call instead of the old Swing dashboard reading `StatusHub` in-process. This is the bridge only — no UI changes, no changes to `Main`, no changes to existing agent logic.

## 3. Modified Files
- Create: `printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java`
- Create: `printing-agent/src/test/java/com/vanter/emberagent/control/LocalControlServerTest.java`

## 4. What Changed?
New class `LocalControlServer`, built on the JDK's built-in `com.sun.net.httpserver.HttpServer` (no new Maven dependency). Binds to `127.0.0.1` on an OS-assigned port (`start()` returns the bound port) and exposes six endpoints:

- `GET /api/status` — JSON mirror of `StatusHub.Snapshot` (`phase`, `detail`, `lastSeen`, `agentId`, `printerCount`, `recentJobs[]`), with `Instant` fields converted to ISO-8601 strings via small private `StatusDto`/`JobDto` records (Jackson has no `Instant` module on the classpath, so this avoids adding one).
- `POST /api/pair` — accepts `{code, backendUrl}` (delegates to the existing `PairingClient.redeem`) or `{apiKey, backendUrl}` (saves the credential directly, same as `PairDialog`'s "Tengo una API key" path); calls `AgentRunner.requestReconnect()` on success.
- `GET /api/printers` — returns a cache of `WindowsPrinterEnumerator.enumerate()`, refreshed every 30s by a daemon `ScheduledExecutorService` (not re-scanned per request).
- `POST /api/test-print` — same behavior as `AgentDashboard.onTestPrint()`: builds a synthetic `PrinterConfigDto`, picks `RAW`/`DRIVER` from the cached printer's `inkjetGuess`, calls `WindowsPrintQueueSender.print`. Does not touch `StatusHub` (manual test prints aren't recorded jobs today either).
- `GET /api/diagnostics` — plain-text `DiagnosticsReport.build(...)`, unchanged.
- `GET /api/paths` — `{logsDir}` from `AgentPaths.logsDir()`.

All handlers reject the wrong HTTP method with `405`. `LocalControlServerTest` (9 tests) spins up a real server on an ephemeral port per test and hits it with `java.net.http.HttpClient`.

## 5. Why It Changed?
Implements Task 1 of `docs/superpowers/plans/2026-09-12-printer-agent-v2-tauri-shell.md` (spec `docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md`): the Tauri shell and the Java agent will run as two separate processes, so they need a channel to talk over. A loopback HTTP server was chosen over any persistent-connection scheme to match the project's existing decision (`ember_hub.md` §2.7) to avoid WebSocket/SSE complexity for a purely local, low-frequency channel.

**Verification:** `mvn -f printing-agent/pom.xml test` → **50/50** (41 previous + 9 new), `BUILD SUCCESS`. No other files touched — `AgentRunner`/`StatusHub`/`PairingClient`/`WindowsPrinterEnumerator`/`WindowsPrintQueueSender`/`DiagnosticsReport`/`AgentPaths` are all called only through their existing public API, unmodified.
