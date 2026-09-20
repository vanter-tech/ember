# Report 527 — Agent installer shipped a stale sidecar jar ("Failed to fetch" when pairing)

## 1. Identification
- **Report number:** 527
- **Task ID:** FIX-AGENT-STALE-JAR
- **Predecessor:** report 526 (agent 0.1.3 bump)

## 2. Objective
Find why the freshly built `EmberAgentSetup-0.1.3.exe` answered "Failed to fetch" when pairing, and fix it at the root.

## 3. Modified Files
- `printing-agent/build-installer.ps1`
- `printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java`
- `printing-agent/src/test/java/com/vanter/emberagent/control/LocalControlServerTest.java`
- `PROGRESS.md`, `reports/527-fix-agent-installer-stale-jar.md`

## 4. What Changed?
**Root cause (evidence, not guess).** The installed sidecar `printing-agent.jar` contained no `HubDiscovery` class although its launcher config said `app-version=0.1.3`; the same request answered `400 {"error":…}` with the old field (`backendUrl`) and dropped the connection with the new one (`target`): `curl: (52) Empty reply from server`. `printing-agent/target/` held one jar per version ever built (0.1.0-SNAPSHOT … 0.1.3) and `Build-AppImage` took `Select-Object -First 1`, i.e. the alphabetically first = the oldest (`0.1.1-SNAPSHOT`, 13 Sep). So the installer labelled 0.1.3 carried the new UI (Tauri shell) with an old sidecar; the old sidecar deserialized the body outside its `try`, the unknown field `target` threw, and the JDK HttpServer closed the socket without a response — which the browser reports as "Failed to fetch".
- **Build script:** `mvn clean package`, and the step now requires **exactly one** `printing-agent-*.jar` in `target/` (fails with the list otherwise), like the Hub's stale-jar guard (report 507).
- **Control server (defence in depth):** the mapper ignores unknown fields, and an unparseable body is answered `400 {"error":"Solicitud inválida."}` instead of dropping the connection. Two tests reproduced the symptom first ("received no bytes") and now pass.

## 5. Why It Changed?
A build that silently packages an old jar is worse than a failing build. And a sidecar that abandons a connection on a bad body turns any UI/sidecar version mismatch into an unexplained "Failed to fetch".

**Consequence to act on:** `EmberAgentSetup-0.1.2.exe` (published, and therefore `EmberAgentSetup-latest.exe`) was built by the same script the same way, so it also contains the 13 Sep jar: it **lacks the fix of report 521** (an agent paired against a Hub keeps the cloud URL) and everything after. Publish the rebuilt `0.1.3` to replace it. The Hub is not affected (its script already runs `clean package`; the installed 0.2.8 jar contains `PrintTargetResolver`).

Verification: agent `mvnw -f printing-agent/pom.xml test` **70/70**; `printing-agent/target` holds a single jar; the jar inside `dist/app-image/Ember Agent/app/printing-agent.jar` contains `HubDiscovery`; `EmberAgentSetup-0.1.3.exe` rebuilt (16:41). Not yet verified: installing it and pairing (to do on the real machine).
