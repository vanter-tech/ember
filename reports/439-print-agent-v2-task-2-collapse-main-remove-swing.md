# Report 439 — PRINT-AGENT-V2 Task 2: collapse `Main` to a single sidecar mode; remove the Swing UI

## 1. Identification
- **Report:** 439
- **Task ID:** PRINT-AGENT-V2 Task 2
- **Predecessor Task:** report 438 (PRINT-AGENT-V2 Task 1 — `LocalControlServer`)

## 2. Objective
Collapse `Main` to the single headless "sidecar" mode the Tauri shell (Task 4) will spawn, and remove the Swing UI it replaces — no more `--tray`/`--headless` flags, this process never owns a window.

## 3. Modified Files
- Modify: `printing-agent/src/main/java/com/vanter/emberagent/Main.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java`
- Modify: `printing-agent/jlink-modules.txt`

## 4. What Changed?
`Main.main` no longer branches on `--headless`/`--tray`: it always starts `AgentRunner` on a background thread, always starts the `LocalControlServer` added in Task 1, prints `PORT=<n>` to stdout once that server is listening (the exact line the Tauri shell's `main.rs` will parse in Task 4), registers a shutdown hook that stops both, and blocks on `worker.join()`. The three Swing UI classes (`AgentDashboard`, `AgentTrayIcon`, `PairDialog`) are deleted outright — there were no tests referencing them (confirmed via `grep -rl "AgentDashboard\|AgentTrayIcon\|PairDialog" printing-agent/src/test` before deleting), so nothing needed updating on the test side.

`jlink-modules.txt` gains `jdk.httpserver`: `com.sun.net.httpserver.HttpServer` (used by `LocalControlServer`) lives in that JDK-specific module, which the existing `java.se` aggregator does **not** include (`java.se` only aggregates standard `java.*` modules). Without this addition, a jlink'd runtime image would throw `NoClassDefFoundError` on the first HTTP request in the packaged installer, even though local `mvn test` (which runs on a full JDK, not the trimmed runtime image) would never catch it. Also updated the module-list comment: `java.se` is still required post-Swing-removal because `WindowsPrintQueueSender` still uses `java.awt.print`/`javax.print` for the actual printing, unrelated to the UI.

## 5. Why It Changed?
Implements Task 2 of `docs/superpowers/plans/2026-09-12-printer-agent-v2-tauri-shell.md`: the spec's architecture (Tauri as the parent process, Java as a windowless sidecar) requires the Java process to never draw its own UI and to always run in what used to be called `--headless` mode — plus emit the port-discovery line the future Rust shell depends on.

**Verification:** `mvn -f printing-agent/pom.xml test` → **50/50** (unchanged from report 438 — deleting the untested Swing classes removed no tests, `Main` has no direct unit test). `AgentRunner`/`StatusHub`/`LocalControlServer` logic untouched.
