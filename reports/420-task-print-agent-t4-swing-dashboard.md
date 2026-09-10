# Report 420 — EMB-PRINT-AGENT T4: Swing dashboard, tray icon, pair dialog, `Main` rewire

## 1. Identification
- **Report number:** 420
- **Current Task ID:** EMB-PRINT-AGENT T4 (plan `docs/superpowers/plans/2026-09-08-print-agent-installer.md`)
- **Predecessor Task:** EMB-PRINT-AGENT T3 — report 419 (`PairingClient`, `WindowsPrinterEnumerator`, `DiscoveredPrintersClient`)

## 2. Objective
Turn the headless print agent into a desktop app: extract the connection loop into a
testable `AgentRunner`, feed a `StatusHub` the Swing UI observes, and add the window,
tray icon and pairing dialog. The runtime wiring for the T3 clients (enumerate +
report on connect) lands here.

## 3. Modified Files
Created:
- `printing-agent/src/main/java/com/vanter/emberagent/AgentRunner.java`
- `printing-agent/src/main/java/com/vanter/emberagent/DiagnosticsReport.java`
- `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java`
- `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java`
- `printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java`
- `printing-agent/src/test/java/com/vanter/emberagent/AgentRunnerTest.java`
- `printing-agent/src/test/java/com/vanter/emberagent/DiagnosticsReportTest.java`

Modified:
- `printing-agent/src/main/java/com/vanter/emberagent/Main.java`
- `printing-agent/src/main/java/com/vanter/emberagent/PrintJobHandler.java`
- `printing-agent/src/main/java/com/vanter/emberagent/AgentConfig.java`
- `printing-agent/src/test/java/com/vanter/emberagent/PrintJobHandlerTest.java`

## 4. What Changed?
- **`AgentRunner`** — the old `Main.main` while-loop, behaviour-identical (5s alive-poll, 10s error backoff, per-job printer refetch), with the T4 hooks at marked points: config from `AgentConfig.resolve(store, agent.properties)`; an empty resolve parks the loop in `StatusHub.Phase.UNPAIRED` and re-checks every 3s so a pair from the dashboard resumes it with no restart; on each connect it runs `WindowsPrinterEnumerator.enumerate()` → `DiscoveredPrintersClient.report(...)` (best-effort, wrapped, never blocks); phase / `setConnected` / job records are pushed into the `StatusHub`. `stop()` ends the loop and drops the live STOMP session; `requestReconnect()` drops the session so the loop reconnects. `decodeAgentIdFromJwt` / `toWsUrl` moved verbatim. Package-private 3-arg ctor takes the properties `Path` for hermetic tests.
- **`PrintJobHandler`** — added a nullable `StatusHub` (new 5-arg ctor; the 4-arg ctor delegates with `null`, so existing callers/tests are untouched). `handle(...)` wraps the ack callback so every ack — including the printer-fetch-failure early return — is mirrored as a `StatusHub.JobRecord(now, role, printerConfigId, result, error)` before delegating. No change to sender selection or error propagation.
- **`Main`** — parses `--headless` / `--tray`; builds one `StatusHub` + `AgentRunner`, starts the runner on a thread (daemon unless headless), and either `join`s it (headless) or calls `AgentDashboard.launch(status, store, runner, startInTray)`.
- **`AgentConfig`** — deleted the `load(Path)` shim (and its `throws IOException`) kept for `Main` in T2; nothing calls it now.
- **`AgentDashboard`** (Swing, `JFrame` "Ember Agent") — header (title + version + phase-coloured dot), **Conexión** (backend host, phase detail, humanized "última vez visto"), **Impresora** (a `JComboBox` of `WindowsPrinterEnumerator.enumerate()` queue names + "Actualizar" + "Imprimir página de prueba" that sends a canned ticket straight through `new WindowsPrintQueueSender().print(dto, text)` with a synthetic `PrinterConfigDto` — `RAW`, or `DRIVER` when the picked queue's `inkjetGuess` is true — **no "Guardar"**, registration stays in the admin per spec §5), **Actividad** (non-editable `JTable` bound to the snapshot's last-20 jobs), **Pie** ("Abrir carpeta de logs" → `AgentPaths.logsDir()`, "Copiar diagnóstico" → clipboard). Repaints on every `StatusHub` snapshot via `SwingUtilities.invokeLater`. `DO_NOTHING_ON_CLOSE` + window listener → hide to tray. On launch, opens `PairDialog` if the store has no credential.
- **`AgentTrayIcon`** — cloned from the Hub's `HubTrayIcon`: runtime-drawn brand-red 16×16 icon, "Mostrar Ember Agent" / "Salir" popup, double-click shows the window, tooltip tracks the phase via a `StatusHub` listener, `SystemTray.isSupported()` guard.
- **`PairDialog`** — modal "Emparejar este agente": 10-char uppercased code field, backend URL under an "Opciones avanzadas" toggle (pre-filled `https://api.ember.vanter.net/v1`), "Emparejar" runs `new PairingClient(store).redeem(url, code)` on a background thread and shows the `PairingException` message inline; a "Tengo una API key" link swaps to a URL + key form that writes the credential directly. Returns `Optional<AgentCredential>`.
- **`DiagnosticsReport.build(Snapshot, CredentialStore)`** — support-ticket text: version, OS, `java.version`, backend **host only** (never the full URL / key), `agentId`, `credencial cifrada=sí/no`, phase, printer count, one line per recent job.
- **Tests** — `AgentRunnerTest` (empty store + missing properties → loop reaches `UNPAIRED` within 5s and `stop()` exits promptly, fully offline); `DiagnosticsReportTest` (contains `agentId=`, `cifrada=sí`, the bare host, never the key or full URL, one line per job; unpaired store still renders); `PrintJobHandlerTest` +1 (a real `StatusHub` receives one `JobRecord` with the right role/result). Swing classes are covered by T7 manual verification, not unit tests (same call the repo made for `HubDashboard`).

## 5. Why It Changed?
Spec §2.1 wants a real window on the printer's PC showing connection health and recent
print activity, and §2.2 wants pairing to happen from that window with a code — no
hand-edited config file. Extracting `AgentRunner` from `Main` keeps the loop a single
testable unit (same rationale as `AgentConnection`/`PrintJobDispatcher`) and lets the
UI thread stay responsive while the daemon thread reconnects. The `StatusHub` seam
(T2) means the Swing layer only observes — it never drives the connection, so a UI
bug can't wedge printing. Feeding `DiscoveredPrintersClient` from the connect path
(rather than a timer) matches spec §2.3's "reported wholesale on every connect/refetch".

## 6. Verification
- `mvn -f printing-agent/pom.xml test` → **BUILD SUCCESS**, `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` (37 prior + 4 new).
- `mvn -f printing-agent/pom.xml -DskipTests package` → shaded jar builds (16.5 MB).
- `java -jar target/printing-agent-0.1.0-SNAPSHOT.jar --headless` → resolved config from the local dev `agent.properties`, connected, serviced a real queued job and idled — the extracted loop is behaviour-equivalent to the old `Main`.
