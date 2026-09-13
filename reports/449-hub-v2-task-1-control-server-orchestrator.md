# Report 449

## 1. Identification
- **Report:** 449
- **Task ID:** EMBER-HUB-V2 Task 1
- **Predecessor Task:** report 448 (PRINT-AGENT-V2 dashboard polish round 3) — first task of a new milestone, EMBER-HUB-V2

## 2. Objective
Build the Java↔Tauri bridge for Ember Hub v2: a loopback-only HTTP control server (`HubControlServer`) backed by a business-logic-free orchestrator (`HubOrchestrator`/`DefaultHubOrchestrator`) that wraps the existing `HubBootstrapRunner`/`LicenseFileInstaller`/`HubStateStore` unchanged, tracking a real `ServicePhase` per service (Postgres/MinIO/Servidor) instead of a Swing label mutated in place.

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootProgressListener.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/ServicePhase.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/DefaultHubOrchestrator.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/control/DefaultHubOrchestratorTest.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`

## 4. What Changed?
- `HubBootProgressListener`: a 4-method callback interface (`onPostgresStarting/Ready`, `onMinioStarting/Ready`) with a `NO_OP` constant.
- `HubBootstrapRunner.startServices()` now delegates to a new overload `startServices(HubBootProgressListener)` that fires the listener at each real step of the existing sequence (license validation → Postgres → MinIO). The no-arg method's behavior is byte-for-byte unchanged (delegates through `NO_OP`).
- `ServicePhase` enum: `STOPPED|STARTING|RUNNING|STOPPING|ERROR`.
- `HubOrchestrator` interface + `HubStatusSnapshot`/`LicenseSnapshot` records — the seam `HubControlServer`'s tests run against a fake instead of booting real Postgres/MinIO.
- `DefaultHubOrchestrator`: wraps `HubBootstrapRunner` + a `SpringApplication` run exactly like `HubDashboard.onStart`/`onStop` did, mapping the progress-listener callbacks and any thrown exception (`InvalidLicenseException`/`PortableDatabaseException`/`PortableMinioException`/generic) into per-service `ServicePhase`/error-message state; `snapshot()` also maps `HubStateStore`'s persisted `HubState` into a 3-state license snapshot (`NONE`/`OK`/`SUSPENDED`).
- `HubControlServer`: JDK `HttpServer` bound to `127.0.0.1` on an OS-assigned port, with the same `CORS_FILTER` pattern printer-agent's `LocalControlServer` needed (report 444) — `GET /api/status`, `POST /api/start`, `POST /api/stop`, `POST /api/license` (body `{"path": "..."}`, installs then auto-starts).
- Tests: `DefaultHubOrchestratorTest` (4 — license-snapshot mapping for none/ok/suspended states + initial all-stopped snapshot) and `HubControlServerTest` (7 — status passthrough, start/stop 200 + method-not-allowed, license missing-path/failure/success against a `FakeOrchestrator`).

## 5. Why It Changed?
This is Task 1 of `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md`, which replaces Hub's Swing `HubDashboard` with a Tauri+WebView2 window per the approved spec (`docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md`). The Tauri window needs an HTTP channel to the Java sidecar the same way printer-agent's already-shipped shell does; splitting `HubOrchestrator` (interface) from `DefaultHubOrchestrator` (real implementation) keeps `HubControlServer`'s tests hermetic without ever touching real Postgres/MinIO processes, consistent with this codebase's existing testing conventions (`HubBootstrapRunner` itself has no test today for the same reason). The progress-listener overload is additive specifically so per-service UI cards (Task 3) can reflect true startup timing instead of a client-side guess.

## 6. Verification
- `cd backend && ./mvnw.cmd -q -Dtest=DefaultHubOrchestratorTest test` — **4/4** pass.
- `cd backend && ./mvnw.cmd -q -Dtest=HubControlServerTest test` — **7/7** pass (the plan document said "6/6" — a miscount in the plan text; the test file it specifies verbatim has 7 `@Test` methods, all passing, not a real discrepancy).
- `cd backend && ./mvnw.cmd test` — full suite **1244/1244** pass (1233 previous + 11 new).
- `./mvnw.cmd -q -DskipTests compile` confirmed the `HubBootstrapRunner` change compiles cleanly before writing any new class (plan Step 3).
