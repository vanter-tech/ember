# Ember Hub v2 (Tauri shell) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **This project's convention (see `PROGRESS.md`/`CLAUDE.md`): one task per context window, `/clear` between tasks, and a sequential numbered report in `/reports/` per completed task** — follow that cadence regardless of which sub-skill executes the tasks.

**Goal:** Replace Ember Hub's Swing launcher (`HubDashboard`/`HubTrayIcon`) with a Tauri + WebView2 native window (Astro + React + Tailwind), with the Hub's Spring Boot process running headless as a spawned sidecar talking to the window over a new loopback-only local HTTP control server — full functional parity with today's Swing dashboard (start/stop services, select `license.key`, open the SPA in the browser, license/heartbeat status), plus a new presentation-only feature: PostgreSQL/MinIO/Servidor each render as an expandable card that shows a simulated, per-service-flavored boot/shutdown log while that service is actually starting/stopping, driven by real phase transitions (never fake success/failure).

**Architecture:** Two processes, same pattern as printer-agent v2. The installed app becomes a Tauri (Rust) binary that owns the window, tray icon, and autostart; on launch it (a) ensures `%ProgramData%\EmberHub\hub.env` exists (generating it with fresh random secrets on first run, replacing the old Inno Setup Pascal-script step), (b) reads that file and (c) spawns the existing jpackage app-image's `Ember Hub.exe` as a child process with those variables injected into its environment and no window of its own. The Java process (Spring Boot, `SPRING_PROFILES_ACTIVE=hub`) gains one new class, `HubControlServer` (JDK `HttpServer`, bound to `127.0.0.1` on an OS-assigned port), backed by a new `HubOrchestrator` that wraps the existing `HubBootstrapRunner`/`LicenseFileInstaller`/`HubStateStore` (unchanged business logic) and tracks a `ServicePhase` (`STOPPED|STARTING|RUNNING|STOPPING|ERROR`) per service (Postgres/MinIO/Servidor) plus the license snapshot; it prints `PORT=<n>` to stdout once listening, exactly like printer-agent's `Main`. The React UI polls `/api/status` every ~1.5s; when a service's phase flips to `STARTING`/`STOPPING` its card auto-expands and plays a canned, service-flavored log script client-side (not fetched from the server) until the next poll reports `RUNNING`/`STOPPED`, at which point it auto-collapses; on `ERROR` the card stays expanded and shows the **real** `*Error` message from `/api/status` instead of a canned line.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 (unchanged core: `HubBootstrapRunner`, `PortableDatabaseBootstrap`, `PortableMinioBootstrap`, `LicenseService`, `LicenseFileInstaller`, `HubStateStore`, `HubProperties`), JDK `com.sun.net.httpserver.HttpServer` (no new Java dependency), Astro 7 + `@astrojs/react` + React 19 + Tailwind 4 for the window's UI (own `ember-hub/ui/` package, not shared with `printing-agent/ui` or `frontend/`), Tauri 2.x (Rust) for the native shell/tray/sidecar/installer, `tauri-plugin-shell` (open the SPA in the system browser) and `tauri-plugin-dialog` (native `license.key` file picker) in addition to printer-agent's `tauri-plugin-autostart`.

**Spec:** `docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md` (covers both Ember Hub v2 and printer-agent v2; printer-agent v2 already shipped — reports 438-448 — and validated the architectural pattern this plan reuses). This plan implements the Hub-specific functional scope and API contract the spec explicitly deferred to "su propio plan de implementación" (§2.5/§8).

## Global Constraints

- No new Java dependency for the control server — JDK `HttpServer` only (spec §2.3), same as printer-agent's `LocalControlServer`.
- `HubControlServer` binds `127.0.0.1` only, never `0.0.0.0` — no auth needed, never add any (spec §2.3). It needs the same CORS filter printer-agent's `LocalControlServer` added in report 444 (`Access-Control-Allow-Origin: *` + `OPTIONS` preflight short-circuit) — the Tauri webview's `fetch()` is always cross-origin to `http://127.0.0.1:<port>`.
- Live updates are polling (~1–2s), never WebSocket/SSE for this local channel (spec §2.3/§2.7).
- **No changes to real business logic**: `PortableDatabaseBootstrap`, `PortableMinioBootstrap`, `LicenseService`, `LicenseFileInstaller`, `HubStateStore`, `HubProperties` are **not modified**. `HubBootstrapRunner` gets exactly one additive, backward-compatible change (an overload taking a progress-listener callback so the control server can report true per-service phase transitions) — the existing no-arg `startServices()` keeps its exact current behavior by delegating to a no-op listener.
- Boot/shutdown log lines shown while a card is expanded are **simulated and hardcoded client-side** (never fetched from the server) — but the phase transitions that trigger them, and any error message shown, are always real (spec's own error-handling philosophy in §4, reused here).
- The Astro/React project is its own package (`ember-hub/ui/`), not a shared module with `printing-agent/ui/` or `frontend/` — copy only the Tailwind 4 design tokens and, since the visual language must match printer-agent's already-shipped shell, its own local copies of `Button`/`Card`/`IconBadge`/`Badge` (spec §2.4).
- Brand red token: `--primary: oklch(0.395 0.175 28.5)` (hex `#8c1717`); `--radius: 0.625rem`; every card `rounded-3xl` or larger, buttons `rounded-full`, primary buttons `bg-primary text-primary-foreground`.
- Commit policy per `CLAUDE.md`: scoped `git add` (never `-A`/`.`), Conventional Commits, lowercase, **no** `Co-authored-by`/`Signed-off-by`/AI signature lines, on branch `spec/tauri-native-shells`.
- `HubDashboard.java` and `HubTrayIcon.java` have no existing tests (confirmed: no test file references either class) — safe to delete outright. `HubBootstrapRunner` also has no existing test today (it drives real portable Postgres/MinIO processes, not hermetically testable) — this plan does not invent one for it; the additive overload is verified by compilation plus `HubOrchestrator`'s own hermetic tests around the parts that **are** pure logic (license snapshot mapping, phase bookkeeping via a fake).

---

## Task 1: `HubOrchestrator` + `HubControlServer` — the Java↔Tauri bridge

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootProgressListener.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/ServicePhase.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/DefaultHubOrchestrator.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/control/DefaultHubOrchestratorTest.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`

**Interfaces:**
- Consumes: `com.vanter.ember.hub.bootstrap.HubBootstrapRunner` (existing, `new HubBootstrapRunner(HubProperties)`, gains the new `startServices(HubBootProgressListener)` overload this task adds; `stopServices()` unchanged), `com.vanter.ember.hub.config.HubProperties` (existing record, unchanged), `com.vanter.ember.hub.dashboard.LicenseFileInstaller.install(Path, Path)` (existing, unchanged), `com.vanter.ember.hub.license.HubStateStore` (existing, `load(): Optional<HubState>`, unchanged), `com.vanter.ember.hub.license.HubState` (existing record: `hardwareFingerprint`, `restaurantId`, `lastHeartbeatAt`, `suspendedSince`), `com.vanter.ember.EmberApplication` (existing `@SpringBootApplication` class, used as `new SpringApplication(EmberApplication.class)`'s argument exactly like `HubDashboard.startServicesInBackground()` already does).
- Produces: `public interface HubOrchestrator { void start(String[] launchArgs); void stop(); void installLicense(java.nio.file.Path source) throws java.io.IOException; HubStatusSnapshot snapshot(); }` plus its `HubStatusSnapshot`/`LicenseSnapshot` records (defined in `HubOrchestrator.java`) and the `ServicePhase` enum — Task 4 (Tauri `main.rs`) does not consume these directly, but Task 2 (`EmberApplication`'s new sidecar entry point) consumes `new DefaultHubOrchestrator(HubProperties)`, `HubControlServer(HubOrchestrator)`, `start()`/`stop(): int`. Task 3 (`ember-hub/ui`) consumes the JSON shape documented in Step 5 below.

- [ ] **Step 1: Write `HubBootProgressListener`**

```java
package com.vanter.ember.hub.bootstrap;

/**
 * Lets a caller of {@link HubBootstrapRunner#startServices(HubBootProgressListener)} observe the
 * real, already-sequential Postgres-then-MinIO startup order without changing it — added for the
 * Tauri shell's control server (spec docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md
 * Task 1) so its per-service cards can auto-expand/collapse in sync with what is actually
 * happening instead of guessing timing client-side.
 */
public interface HubBootProgressListener {

    void onPostgresStarting();

    void onPostgresReady();

    void onMinioStarting();

    void onMinioReady();

    HubBootProgressListener NO_OP = new HubBootProgressListener() {
        @Override
        public void onPostgresStarting() {}

        @Override
        public void onPostgresReady() {}

        @Override
        public void onMinioStarting() {}

        @Override
        public void onMinioReady() {}
    };
}
```

- [ ] **Step 2: Add the progress-listener overload to `HubBootstrapRunner`, keep the old method as a delegating overload**

In `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`, replace the `startServices()` method (keep everything else in the file — fields, `stopServices()`, `stopServicesQuietly()` — unchanged):

```java
    public void startServices()
            throws InvalidLicenseException, PortableDatabaseException, PortableMinioException {
        startServices(HubBootProgressListener.NO_OP);
    }

    public void startServices(HubBootProgressListener listener)
            throws InvalidLicenseException, PortableDatabaseException, PortableMinioException {
        PublicKey publicKey = LicenseKeyParser.loadPublicKey(properties.publicKeyFile());
        LicenseService licenseService = new LicenseService(
                properties.licenseFile(),
                publicKey,
                new LicenseKeyParser(),
                new HardwareFingerprintService(),
                new HubStateStore(properties.stateFile()),
                java.time.Duration.ofHours(properties.suspendedGraceHours()));
        licenseService.validateOrActivate();

        listener.onPostgresStarting();
        dbBootstrap = new PortableDatabaseBootstrap(
                properties.dataDir(), properties.postgresBinDir(), properties.postgresPort());
        dbBootstrap.ensureRunning();
        listener.onPostgresReady();

        listener.onMinioStarting();
        minioBootstrap = new PortableMinioBootstrap(
                properties.minioDataDir(), properties.minioBinDir(), properties.minioPort());
        minioBootstrap.ensureRunning();
        listener.onMinioReady();

        shutdownHook = new Thread(this::stopServicesQuietly, "hub-db-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
```

This is purely additive: the no-arg method's behavior (order of operations, exceptions thrown, side effects) is byte-for-byte identical to before — it just now delegates through a no-op listener. No existing caller (only `HubDashboard`, deleted in Task 2) needs to change for this step alone to compile.

- [ ] **Step 3: Compile to confirm the additive change is safe**

Run: `cd backend && .\mvnw.cmd -q -DskipTests compile`
Expected: compiles clean — this class has no existing test suite to run (it drives real portable Postgres/MinIO processes; see Global Constraints).

- [ ] **Step 4: Write `ServicePhase`**

```java
package com.vanter.ember.hub.control;

/** Mirrors the real lifecycle of one Hub-managed service (Postgres, MinIO, or the Spring server). */
public enum ServicePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
```

- [ ] **Step 5: Write the `HubOrchestrator` interface + its DTOs**

```java
package com.vanter.ember.hub.control;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Business-logic-free façade the Tauri shell's {@link HubControlServer} talks to instead of the
 * old Swing {@code HubDashboard} reading/mutating its own fields in-process. The real
 * implementation ({@link DefaultHubOrchestrator}) wraps {@code HubBootstrapRunner} and a
 * {@code SpringApplication} run unchanged; this interface only exists so
 * {@link HubControlServer}'s tests can run against a fake instead of booting real Postgres/MinIO.
 */
public interface HubOrchestrator {

    /** Starts Postgres+MinIO+the Spring server in the background; returns immediately. */
    void start(String[] launchArgs);

    /** Stops the Spring server then Postgres+MinIO in the background; returns immediately. */
    void stop();

    /** Copies {@code source} into the configured license file location. */
    void installLicense(Path source) throws IOException;

    HubStatusSnapshot snapshot();

    record LicenseSnapshot(String status, Instant lastHeartbeatAt, Instant suspendedSince) {
        public static final String OK = "OK";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String NONE = "NONE";
    }

    record HubStatusSnapshot(
            ServicePhase postgres,
            String postgresError,
            ServicePhase minio,
            String minioError,
            ServicePhase server,
            String serverError,
            LicenseSnapshot license,
            int serverPort) {}
}
```

- [ ] **Step 6: Write the failing `DefaultHubOrchestrator` test (license-snapshot mapping — the one pure, hermetically-testable slice of this class)**

```java
package com.vanter.ember.hub.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultHubOrchestratorTest {

    @TempDir Path tempDir;

    private HubProperties propertiesWithStateFile(Path stateFile) {
        return new HubProperties(
                tempDir.resolve("pg-data"), tempDir.resolve("pg-bin"),
                tempDir.resolve("license.key"), tempDir.resolve("public.der"), stateFile,
                5432, 8080, "", tempDir.resolve("minio-data"), tempDir.resolve("minio-bin"), 9000);
    }

    @Test
    void snapshot_noStateFile_reportsNoneLicense() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        HubOrchestrator.LicenseSnapshot license = orchestrator.snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.NONE, license.status());
        assertNull(license.lastHeartbeatAt());
    }

    @Test
    void snapshot_stateWithoutSuspension_reportsOk() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant heartbeat = Instant.parse("2026-09-13T10:00:00Z");
        new HubStateStore(stateFile).save(new HubState("fp-1", UUID.randomUUID(), heartbeat));

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.OK, license.status());
        assertEquals(heartbeat, license.lastHeartbeatAt());
    }

    @Test
    void snapshot_suspendedState_reportsSuspended() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant suspendedSince = Instant.parse("2026-09-10T08:00:00Z");
        HubState suspended = new HubState("fp-1", UUID.randomUUID(), Instant.parse("2026-09-09T08:00:00Z"))
                .withSuspendedSince(suspendedSince);
        new HubStateStore(stateFile).save(suspended);

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.SUSPENDED, license.status());
        assertEquals(suspendedSince, license.suspendedSince());
    }

    @Test
    void snapshot_beforeStart_allServicesStopped() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        HubOrchestrator.HubStatusSnapshot snapshot = orchestrator.snapshot();

        assertEquals(ServicePhase.STOPPED, snapshot.postgres());
        assertEquals(ServicePhase.STOPPED, snapshot.minio());
        assertEquals(ServicePhase.STOPPED, snapshot.server());
        assertEquals(8080, snapshot.serverPort());
    }
}
```

- [ ] **Step 7: Run the test to verify it fails on missing class**

Run: `cd backend && .\mvnw.cmd -q -Dtest=DefaultHubOrchestratorTest test`
Expected: FAIL — compile error, `DefaultHubOrchestrator` does not exist yet.

- [ ] **Step 8: Write `DefaultHubOrchestrator`**

```java
package com.vanter.ember.hub.control;

import com.vanter.ember.EmberApplication;
import com.vanter.ember.hub.bootstrap.HubBootProgressListener;
import com.vanter.ember.hub.bootstrap.HubBootstrapRunner;
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import com.vanter.ember.hub.bootstrap.PortableMinioException;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.dashboard.LicenseFileInstaller;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Wraps {@link HubBootstrapRunner} + a {@code SpringApplication} run exactly like
 * {@code HubDashboard.onStart}/{@code onStop} used to, tracking a {@link ServicePhase} per
 * service so {@link HubControlServer} can report real progress instead of a Swing label mutated
 * in place. No business logic changes — same calls, same order, same exceptions.
 */
public final class DefaultHubOrchestrator implements HubOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DefaultHubOrchestrator.class);

    private final HubProperties properties;
    private final HubBootstrapRunner bootstrapRunner;
    private final HubStateStore stateStore;
    private final String[] fallbackArgs = new String[0];

    private volatile ServicePhase postgresPhase = ServicePhase.STOPPED;
    private volatile String postgresError;
    private volatile ServicePhase minioPhase = ServicePhase.STOPPED;
    private volatile String minioError;
    private volatile ServicePhase serverPhase = ServicePhase.STOPPED;
    private volatile String serverError;
    private volatile ConfigurableApplicationContext context;

    public DefaultHubOrchestrator(HubProperties properties) {
        this.properties = properties;
        this.bootstrapRunner = new HubBootstrapRunner(properties);
        this.stateStore = new HubStateStore(properties.stateFile());
    }

    @Override
    public synchronized void start(String[] launchArgs) {
        if (postgresPhase != ServicePhase.STOPPED && postgresPhase != ServicePhase.ERROR) {
            return; // already starting/running — HubControlServer turns this into a 409.
        }
        postgresPhase = ServicePhase.STARTING;
        postgresError = null;
        minioPhase = ServicePhase.STOPPED;
        minioError = null;
        serverPhase = ServicePhase.STOPPED;
        serverError = null;
        String[] args = launchArgs != null ? launchArgs : fallbackArgs;
        new Thread(() -> runStart(args), "hub-orchestrator-start").start();
    }

    private void runStart(String[] launchArgs) {
        try {
            bootstrapRunner.startServices(new HubBootProgressListener() {
                @Override
                public void onPostgresStarting() {
                    postgresPhase = ServicePhase.STARTING;
                }

                @Override
                public void onPostgresReady() {
                    postgresPhase = ServicePhase.RUNNING;
                }

                @Override
                public void onMinioStarting() {
                    minioPhase = ServicePhase.STARTING;
                }

                @Override
                public void onMinioReady() {
                    minioPhase = ServicePhase.RUNNING;
                }
            });

            serverPhase = ServicePhase.STARTING;
            SpringApplication app = new SpringApplication(EmberApplication.class);
            app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> serverPhase = ServicePhase.RUNNING);
            context = app.run(launchArgs);
        } catch (InvalidLicenseException e) {
            // Licencia inválida bloquea todo antes de que Postgres arranque siquiera — se reporta
            // en la card de Postgres porque es el primer paso de la secuencia real, no porque el
            // problema sea de Postgres (no hay una 4ª card de licencia-bloqueante en el diseño
            // aprobado; la card de licencia por sí sola solo refleja el estado ya persistido).
            log.error("Ember Hub no pudo iniciar: licencia inválida", e);
            postgresPhase = ServicePhase.ERROR;
            postgresError = e.getMessage();
        } catch (PortableDatabaseException e) {
            log.error("Ember Hub no pudo iniciar Postgres", e);
            postgresPhase = ServicePhase.ERROR;
            postgresError = e.getMessage();
        } catch (PortableMinioException e) {
            log.error("Ember Hub no pudo iniciar MinIO", e);
            minioPhase = ServicePhase.ERROR;
            minioError = e.getMessage();
        } catch (Exception e) {
            log.error("Ember Hub no pudo iniciar el servidor", e);
            serverPhase = ServicePhase.ERROR;
            serverError = e.getMessage();
        }
    }

    @Override
    public synchronized void stop() {
        if (serverPhase != ServicePhase.RUNNING && serverPhase != ServicePhase.ERROR
                && postgresPhase != ServicePhase.RUNNING && postgresPhase != ServicePhase.ERROR) {
            return; // nothing to stop — HubControlServer turns this into a 409.
        }
        serverPhase = ServicePhase.STOPPING;
        postgresPhase = ServicePhase.STOPPING;
        minioPhase = ServicePhase.STOPPING;
        new Thread(this::runStop, "hub-orchestrator-stop").start();
    }

    private void runStop() {
        if (context != null) {
            context.close();
            context = null;
        }
        serverPhase = ServicePhase.STOPPED;
        serverError = null;
        bootstrapRunner.stopServices();
        minioPhase = ServicePhase.STOPPED;
        minioError = null;
        postgresPhase = ServicePhase.STOPPED;
        postgresError = null;
    }

    @Override
    public void installLicense(Path source) throws IOException {
        LicenseFileInstaller.install(source, properties.licenseFile());
    }

    @Override
    public HubStatusSnapshot snapshot() {
        LicenseSnapshot license = stateStore.load()
                .map(this::toLicenseSnapshot)
                .orElse(new LicenseSnapshot(LicenseSnapshot.NONE, null, null));
        return new HubStatusSnapshot(
                postgresPhase, postgresError,
                minioPhase, minioError,
                serverPhase, serverError,
                license, properties.serverPort());
    }

    private LicenseSnapshot toLicenseSnapshot(HubState state) {
        String status = state.suspendedSince() != null ? LicenseSnapshot.SUSPENDED : LicenseSnapshot.OK;
        return new LicenseSnapshot(status, state.lastHeartbeatAt(), state.suspendedSince());
    }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `cd backend && .\mvnw.cmd -q -Dtest=DefaultHubOrchestratorTest test`
Expected: PASS, 4/4.

- [ ] **Step 10: Write the failing `HubControlServer` test against a fake orchestrator**

```java
package com.vanter.ember.hub.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubControlServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private FakeOrchestrator orchestrator;
    private HubControlServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        orchestrator = new FakeOrchestrator();
        server = new HubControlServer(orchestrator);
        int port = server.start();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void status_reflectsOrchestratorSnapshot() throws Exception {
        orchestrator.snapshot = new HubOrchestrator.HubStatusSnapshot(
                ServicePhase.RUNNING, null, ServicePhase.RUNNING, null, ServicePhase.STARTING, null,
                new HubOrchestrator.LicenseSnapshot("OK", null, null), 8080);

        JsonNode body = mapper.readTree(get("/api/status").body());

        assertEquals("RUNNING", body.get("postgres").asText());
        assertEquals("STARTING", body.get("server").asText());
        assertEquals("OK", body.get("license").get("status").asText());
        assertEquals(8080, body.get("serverPort").asInt());
    }

    @Test
    void start_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = post("/api/start", "");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.startCalled);
    }

    @Test
    void start_rejectsNonPost() throws Exception {
        HttpResponse<String> res = get("/api/start");

        assertEquals(405, res.statusCode());
    }

    @Test
    void stop_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = post("/api/stop", "");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.stopCalled);
    }

    @Test
    void license_missingPath_returns400() throws Exception {
        HttpResponse<String> res = post("/api/license", "{}");

        assertEquals(400, res.statusCode());
    }

    @Test
    void license_installFailure_returns400WithMessage() throws Exception {
        orchestrator.installLicenseFailure = new IOException("no existe");

        HttpResponse<String> res = post("/api/license", "{\"path\":\"C:/x/license.key\"}");

        assertEquals(400, res.statusCode());
        assertEquals("no existe", mapper.readTree(res.body()).get("error").asText());
    }

    @Test
    void license_installSuccess_returns200() throws Exception {
        HttpResponse<String> res = post("/api/license", "{\"path\":\"C:/x/license.key\"}");

        assertEquals(200, res.statusCode());
        assertEquals(Path.of("C:/x/license.key"), orchestrator.installedFrom);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static final class FakeOrchestrator implements HubOrchestrator {
        boolean startCalled;
        boolean stopCalled;
        Path installedFrom;
        IOException installLicenseFailure;
        HubOrchestrator.HubStatusSnapshot snapshot = new HubOrchestrator.HubStatusSnapshot(
                ServicePhase.STOPPED, null, ServicePhase.STOPPED, null, ServicePhase.STOPPED, null,
                new HubOrchestrator.LicenseSnapshot("NONE", null, null), 8080);

        @Override
        public void start(String[] launchArgs) {
            startCalled = true;
        }

        @Override
        public void stop() {
            stopCalled = true;
        }

        @Override
        public void installLicense(Path source) throws IOException {
            if (installLicenseFailure != null) {
                throw installLicenseFailure;
            }
            installedFrom = source;
        }

        @Override
        public HubStatusSnapshot snapshot() {
            return snapshot;
        }
    }
}
```

- [ ] **Step 11: Run the test to verify it fails on missing class**

Run: `cd backend && .\mvnw.cmd -q -Dtest=HubControlServerTest test`
Expected: FAIL — compile error, `HubControlServer` does not exist.

- [ ] **Step 12: Write `HubControlServer`**

```java
package com.vanter.ember.hub.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Loopback-only HTTP bridge the Tauri shell polls/calls instead of the old Swing
 * {@code HubDashboard} reading/mutating its own fields in-process (spec
 * docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md §2.3/§3, adapted for Hub in
 * docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md §Task 1). Binds only to
 * 127.0.0.1 on an OS-assigned port; no auth, because the channel never leaves this machine. Every
 * handler delegates to {@link HubOrchestrator} — this class is wire plumbing only.
 */
public final class HubControlServer {

    // The Tauri window's origin is never http://127.0.0.1:<port>, so every fetch() the React UI
    // makes is cross-origin — same fix printer-agent's LocalControlServer needed (report 444).
    private static final Filter CORS_FILTER = new Filter() {
        @Override
        public String description() {
            return "CORS (loopback-only, any origin)";
        }

        @Override
        public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            chain.doFilter(exchange);
        }
    };

    private final HubOrchestrator orchestrator;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer httpServer;

    public HubControlServer(HubOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /** Starts listening on 127.0.0.1 at an OS-assigned port and returns that port. */
    public int start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/status", this::handleStatus).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/start", this::handleStart).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/stop", this::handleStop).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/license", this::handleLicense).getFilters().add(CORS_FILTER);
        httpServer.setExecutor(Executors.newCachedThreadPool(daemonThreadFactory()));
        httpServer.start();
        return httpServer.getAddress().getPort();
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    // --- handlers ----------------------------------------------------------------

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
    }

    private void handleStart(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        orchestrator.start(new String[0]);
        sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
    }

    private void handleStop(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        orchestrator.stop();
        sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
    }

    private void handleLicense(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        LicenseRequest req = mapper.readValue(exchange.getRequestBody(), LicenseRequest.class);
        if (req.path() == null || req.path().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "path es obligatorio."));
            return;
        }
        try {
            orchestrator.installLicense(Path.of(req.path()));
            orchestrator.start(new String[0]);
            sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
        } catch (IOException | RuntimeException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    // --- wire helpers --------------------------------------------------------------

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "ember-hub-control");
            t.setDaemon(true);
            return t;
        };
    }

    // --- wire DTOs -------------------------------------------------------------------

    private record LicenseRequest(String path) {}

    private record LicenseDto(String status, String lastHeartbeatAt, String suspendedSince) {
        static LicenseDto from(HubOrchestrator.LicenseSnapshot s) {
            return new LicenseDto(
                    s.status(),
                    s.lastHeartbeatAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(s.lastHeartbeatAt()),
                    s.suspendedSince() == null ? null : DateTimeFormatter.ISO_INSTANT.format(s.suspendedSince()));
        }
    }

    private record StatusDto(
            String postgres, String postgresError,
            String minio, String minioError,
            String server, String serverError,
            LicenseDto license, int serverPort) {
        static StatusDto from(HubOrchestrator.HubStatusSnapshot s) {
            return new StatusDto(
                    s.postgres().name(), s.postgresError(),
                    s.minio().name(), s.minioError(),
                    s.server().name(), s.serverError(),
                    LicenseDto.from(s.license()), s.serverPort());
        }
    }
}
```

Note: `/api/start` and `/api/stop` do not return `409` for an already-in-progress request in this minimal version — `DefaultHubOrchestrator.start()`/`stop()` already no-op silently when called redundantly (Step 8), so the handler always returns the current snapshot either way; the UI (Task 3) disables the triggering button while a transition is in flight, matching how `HubDashboard`'s Swing buttons were disabled during `onStart`/`onStop`.

- [ ] **Step 13: Run the test to verify it passes**

Run: `cd backend && .\mvnw.cmd -q -Dtest=HubControlServerTest test`
Expected: PASS, 6/6.

- [ ] **Step 14: Run the full backend suite to confirm no regressions**

Run: `cd backend && .\mvnw.cmd test`
Expected: PASS, previous total + 10 (4 `DefaultHubOrchestratorTest` + 6 `HubControlServerTest`) — confirm it matches "previous total + 10" rather than a hardcoded number.

- [ ] **Step 15: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootProgressListener.java backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java backend/src/main/java/com/vanter/ember/hub/control backend/src/test/java/com/vanter/ember/hub/control
git commit -m "feat(hub): add loopback control server and orchestrator for the Tauri shell"
```

---

## Task 2: Collapse the `hub` profile entry point to a headless sidecar mode; remove the Swing UI

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- Delete: `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`
- Delete: `backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java`
- Modify: `ember-hub/jlink-modules.txt`

**Interfaces:**
- Consumes: `HubOrchestrator`/`DefaultHubOrchestrator`/`HubControlServer` from Task 1 (`new DefaultHubOrchestrator(HubProperties)`, `new HubControlServer(HubOrchestrator)`, `start(): int`, `stop()`, and `HubOrchestrator.start(String[])` — the sidecar auto-starts services on launch, matching `HubDashboard.launch`'s `--autostart` handling), `com.vanter.ember.hub.config.HubProperties.fromEnvironment()` (existing, unchanged).
- Produces: the new sidecar entry point prints exactly one line `PORT=<n>` to stdout once the control server is listening — Task 4 (Tauri `main.rs`) parses this exact line/prefix, identical contract to printer-agent's `Main`.

`HubDashboard`/`HubTrayIcon` have no existing tests (confirmed: `grep -rl "HubDashboard\|HubTrayIcon" backend/src/test` returns nothing) — safe to delete outright.

- [ ] **Step 1: Replace `EmberApplication.java`'s hub branch**

```java
package com.vanter.ember;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.DefaultHubOrchestrator;
import com.vanter.ember.hub.control.HubControlServer;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmberApplication {

    public static void main(String[] args) throws IOException {
        if (isHubProfile()) {
            runHubSidecar(args);
            return;
        }
        SpringApplication.run(EmberApplication.class, args);
    }

    /**
     * Runs headless: a {@link HubControlServer} that a separate Tauri shell process polls/calls
     * instead of the old Swing {@code HubDashboard}/{@code HubTrayIcon} reading/mutating state
     * in-process (spec docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md, plan
     * docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md). Prints "PORT=&lt;n&gt;" to
     * stdout once the control server is listening — the Tauri shell reads that single line from
     * this process's stdout to know where to send requests. Auto-starts services immediately,
     * same as {@code HubDashboard.launch}'s old {@code --autostart} argument did (the Tauri shell
     * always passes it, mirroring "Iniciar Ember Hub.cmd"'s prior behavior).
     */
    private static void runHubSidecar(String[] args) throws IOException {
        HubProperties properties = HubProperties.fromEnvironment();
        DefaultHubOrchestrator orchestrator = new DefaultHubOrchestrator(properties);

        HubControlServer controlServer = new HubControlServer(orchestrator);
        int port = controlServer.start();
        System.out.println("PORT=" + port);
        System.out.flush();

        orchestrator.start(args);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            orchestrator.stop();
            controlServer.stop();
        }, "ember-hub-shutdown"));
    }

    /**
     * Reads the profile straight from the environment, not from Spring — Spring hasn't started
     * yet. This is deliberately the ONLY place that matters: Spring's own DataSource
     * autoconfiguration connects to Postgres during context refresh, which runs before any
     * {@code ApplicationRunner} — too late to start portable Postgres from inside the Spring
     * lifecycle.
     */
    private static boolean isHubProfile() {
        String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profiles == null) {
            profiles = System.getProperty("spring.profiles.active", "");
        }
        return Arrays.asList(profiles.split(",")).contains("hub");
    }
}
```

Note the deleted `System.setProperty("java.awt.headless", "false")` line — it existed only so Swing (`HubDashboard`/`HubTrayIcon`) could open a real desktop window/tray icon; the sidecar never touches AWT/Swing, so Spring Boot's default `java.awt.headless=true` is correct and no longer needs overriding.

- [ ] **Step 2: Delete the Swing UI classes**

```bash
git rm backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java
git rm backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java
```

`backend/src/main/java/com/vanter/ember/hub/dashboard/LicenseFileInstaller.java` (and its test) stay untouched — `DefaultHubOrchestrator` already calls it directly (Task 1 Step 8).

- [ ] **Step 3: Add `jdk.httpserver` to the Hub's embedded runtime module list**

`com.sun.net.httpserver.HttpServer` (used by `HubControlServer`) lives in the JDK-specific `jdk.httpserver` module, which the `java.se` aggregator already in `ember-hub/jlink-modules.txt` does **not** include — without this, `jlink` produces a runtime image that throws `NoClassDefFoundError` for `HttpServer` at first request (same gotcha printer-agent's Task 2 hit).

Append one line to `ember-hub/jlink-modules.txt` (after `jdk.charsets`):

```
jdk.httpserver
```

- [ ] **Step 4: Compile and run the full backend suite**

Run: `cd backend && .\mvnw.cmd test`
Expected: PASS — same count as the end of Task 1 (deleting the untested Swing classes removes no tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/EmberApplication.java ember-hub/jlink-modules.txt
git add backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java
git commit -m "refactor(hub): drop Swing UI, run headless with the local control server as the only surface"
```

---

## Task 3: `ember-hub/ui/` — Astro + React + Tailwind window content

**Files:**
- Create: `ember-hub/ui/package.json`
- Create: `ember-hub/ui/astro.config.mjs`
- Create: `ember-hub/ui/tsconfig.json`
- Create: `ember-hub/ui/vitest.config.ts`
- Create: `ember-hub/ui/.gitignore`
- Create: `ember-hub/ui/src/styles/global.css`
- Create: `ember-hub/ui/src/layouts/Layout.astro`
- Create: `ember-hub/ui/src/pages/index.astro`
- Create: `ember-hub/ui/src/lib/types.ts`
- Create: `ember-hub/ui/src/lib/api.ts`
- Create: `ember-hub/ui/src/lib/logScripts.ts`
- Create: `ember-hub/ui/src/components/Button.tsx`
- Create: `ember-hub/ui/src/components/Card.tsx`
- Create: `ember-hub/ui/src/components/Badge.tsx`
- Create: `ember-hub/ui/src/components/ServiceCard.tsx`
- Create: `ember-hub/ui/src/components/LicenseCard.tsx`
- Create: `ember-hub/ui/src/components/Dashboard.tsx`
- Test: `ember-hub/ui/src/components/ServiceCard.test.tsx`
- Test: `ember-hub/ui/src/components/LicenseCard.test.tsx`

**Interfaces:**
- Consumes: `HubControlServer`'s JSON contract from Task 1 (`/api/status`, `/api/start`, `/api/stop`, `/api/license`) and a Tauri-provided `invoke('get_port')` command plus `agent-ready`/`agent-crashed` events (Task 4 produces them — same names/shape as printer-agent's shell, reused verbatim so `ember-hub/ui` can copy printer-agent's already-proven `agent-events.ts` unchanged) and `invoke('pick_license_file')`/`invoke('open_in_browser', { url })` commands (Task 4 produces them).
- Produces: a static build at `ember-hub/ui/dist/` — Task 4's `tauri.conf.json` `frontendDist` points here.

This task's components read from a not-yet-existing `@tauri-apps/api` global — during **this task**, stub it (`vi.mock('@tauri-apps/api/core', ...)` in tests) so the UI is independently buildable/testable before Task 4 exists, exactly like printer-agent's Task 3 did.

- [ ] **Step 1: Scaffold `package.json`**

```json
{
  "name": "ember-hub-ui",
  "type": "module",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "astro dev",
    "build": "astro build",
    "test": "vitest run"
  },
  "dependencies": {
    "@astrojs/react": "^6.0.2",
    "@tailwindcss/vite": "^4.3.3",
    "@tauri-apps/api": "^2.1.1",
    "astro": "^7.2.2",
    "lucide-react": "^0.469.0",
    "react": "^19.2.8",
    "react-dom": "^19.2.8",
    "tailwindcss": "^4.3.3"
  },
  "devDependencies": {
    "@testing-library/react": "^16.1.0",
    "@types/react": "^19.2.18",
    "@types/react-dom": "^19.2.4",
    "jsdom": "^25.0.1",
    "vitest": "^2.1.8"
  }
}
```

Version pins copied verbatim from `printing-agent/ui/package.json` for consistency (same Astro/React/Tailwind/Tauri/testing generation) — `lucide-react` added for icons (`Database`, `HardDrive`, `Server`, `KeyRound`, matching printer-agent's `IconBadge` usage of the same library).

- [ ] **Step 2: `astro.config.mjs`**

```js
// @ts-check
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

// Static single-page build loaded directly by Tauri's WebView2 (no i18n/sitemap needed —
// single-tenant local app, Spanish-only, matches printing-agent/ui).
export default defineConfig({
  integrations: [react()],
  server: { port: 5176 },
  vite: {
    plugins: [tailwindcss()]
  }
});
```

- [ ] **Step 3: `tsconfig.json`**

```json
{
  "extends": "astro/tsconfigs/strict",
  "compilerOptions": {
    "jsx": "react-jsx",
    "jsxImportSource": "react"
  }
}
```

- [ ] **Step 4: `.gitignore`**

```
node_modules/
dist/
```

- [ ] **Step 5: Design tokens — `src/styles/global.css`**

Identical values to `printing-agent/ui/src/styles/global.css` (both copy the same `frontend/src/index.css` tokens) so the two shell windows look like the same product.

```css
@import "tailwindcss";

@theme inline {
  --font-sans: 'Inter', sans-serif;
  --color-primary: var(--primary);
  --color-primary-foreground: var(--primary-foreground);
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-border: var(--border);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --radius-md: calc(var(--radius) * 0.8);
  --radius-lg: var(--radius);
  --radius-xl: calc(var(--radius) * 1.4);
}

:root {
  --radius: 0.625rem;
  --primary: oklch(0.395 0.175 28.5);
  --primary-foreground: oklch(0.98 0 0);
  --background: oklch(0.985 0 0);
  --foreground: oklch(0.15 0 0);
  --border: oklch(0.9 0 0);
  --muted: oklch(0.96 0 0);
  --muted-foreground: oklch(0.5 0 0);
}

body {
  @apply font-sans bg-background text-foreground;
  margin: 0;
}
```

- [ ] **Step 6: `src/layouts/Layout.astro`**

```astro
---
import '../styles/global.css';
---
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Ember Hub</title>
  </head>
  <body>
    <slot />
  </body>
</html>
```

- [ ] **Step 7: `src/pages/index.astro`**

```astro
---
import Layout from '../layouts/Layout.astro';
import Dashboard from '../components/Dashboard';
---
<Layout>
  <Dashboard client:load />
</Layout>
```

- [ ] **Step 8: `src/lib/types.ts`**

```ts
export type ServicePhase = 'STOPPED' | 'STARTING' | 'RUNNING' | 'STOPPING' | 'ERROR';
export type LicenseStatus = 'OK' | 'SUSPENDED' | 'NONE';

export interface LicenseSnapshot {
  status: LicenseStatus;
  lastHeartbeatAt: string | null;
  suspendedSince: string | null;
}

export interface HubStatus {
  postgres: ServicePhase;
  postgresError: string | null;
  minio: ServicePhase;
  minioError: string | null;
  server: ServicePhase;
  serverError: string | null;
  license: LicenseSnapshot;
  serverPort: number;
}
```

- [ ] **Step 9: `src/lib/api.ts`**

```ts
import { invoke } from '@tauri-apps/api/core';
import type { HubStatus } from './types';

let cachedPort: number | null = null;

async function port(): Promise<number> {
  if (cachedPort === null) {
    cachedPort = await invoke<number>('get_port');
  }
  return cachedPort;
}

async function base(): Promise<string> {
  return `http://127.0.0.1:${await port()}`;
}

async function asJson<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error ?? `request failed (${res.status})`);
  }
  return data as T;
}

export async function getStatus(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/status`));
}

export async function startServices(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/start`, { method: 'POST' }));
}

export async function stopServices(): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/stop`, { method: 'POST' }));
}

export async function installLicense(path: string): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/license`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path })
  }));
}
```

- [ ] **Step 10: `src/lib/logScripts.ts` — the simulated, per-service-flavored log lines**

```ts
export const START_SCRIPTS = {
  postgres: [
    'LOG:  starting PostgreSQL 16.6 on x86_64-pc-windows',
    'LOG:  listening on IPv4 address "127.0.0.1", port 5432',
    'LOG:  database system is ready to accept connections'
  ],
  minio: [
    'API: http://127.0.0.1:9000',
    'Console: http://127.0.0.1:9001',
    'Status: 1 Online, 0 Offline.'
  ],
  server: [
    'Starting EmberApplication using Java 17 (hub profile)',
    'Tomcat started on port(s): 8080 (http)',
    'Started EmberApplication in 4.2 seconds'
  ]
} as const;

export const STOP_SCRIPTS = {
  postgres: [
    'LOG:  received fast shutdown request',
    'LOG:  database system is shut down'
  ],
  minio: [
    'Shutting down MinIO…',
    'MinIO instance stopped.'
  ],
  server: [
    'Cerrando el contexto de Spring…',
    'Servidor detenido.'
  ]
} as const;

export type ServiceId = keyof typeof START_SCRIPTS;
```

- [ ] **Step 11: `src/components/Button.tsx` (copied from `printing-agent/ui`, not imported — spec §2.4)**

```tsx
import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'outline';

const VARIANT_CLASS: Record<Variant, string> = {
  primary: 'bg-primary text-primary-foreground hover:opacity-90',
  outline: 'border border-border hover:bg-muted'
};

export default function Button({
  variant = 'outline',
  className = '',
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-full px-4 py-1.5 text-sm font-medium cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 transition-colors ${VARIANT_CLASS[variant]} ${className}`}
      {...props}
    />
  );
}
```

- [ ] **Step 12: `src/components/Card.tsx` (copied from `printing-agent/ui`)**

```tsx
import type { ComponentType, ReactNode } from 'react';

export const cardShellClass = 'rounded-3xl border border-border shadow-md bg-background';

export function IconBadge({
  icon: Icon,
  size = 'md',
  className = ''
}: {
  icon: ComponentType<{ className?: string }>;
  size?: 'md' | 'lg';
  className?: string;
}) {
  const wrapper = size === 'lg' ? 'h-12 w-12' : 'h-9 w-9';
  const iconSize = size === 'lg' ? 'h-6 w-6' : 'h-5 w-5';
  return (
    <span className={`inline-flex items-center justify-center shrink-0 rounded-full bg-primary/10 text-primary ${wrapper} ${className}`}>
      <Icon className={iconSize} />
    </span>
  );
}

export default function Card({
  icon: Icon,
  title,
  children,
  className = ''
}: {
  icon: ComponentType<{ className?: string }>;
  title: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`${cardShellClass} p-4 flex flex-col min-w-0 ${className}`}>
      <div className="flex items-center gap-3 mb-3 shrink-0">
        <IconBadge icon={Icon} />
        <h2 className="font-semibold text-lg">{title}</h2>
      </div>
      {children}
    </section>
  );
}
```

- [ ] **Step 13: `src/components/Badge.tsx` (copied from `printing-agent/ui`)**

```tsx
import type { ReactNode } from 'react';

const VARIANTS = {
  success: 'bg-emerald-100 text-emerald-800',
  warning: 'bg-amber-100 text-amber-800',
  danger: 'bg-red-100 text-red-800',
  neutral: 'bg-muted text-muted-foreground'
} as const;

export default function Badge({
  variant = 'neutral',
  children
}: {
  variant?: keyof typeof VARIANTS;
  children: ReactNode;
}) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium whitespace-nowrap ${VARIANTS[variant]}`}>
      {children}
    </span>
  );
}
```

- [ ] **Step 14: Write the failing `ServiceCard` test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { Database } from 'lucide-react';
import ServiceCard from './ServiceCard';

describe('ServiceCard', () => {
  it('shows a compact "Detenido" badge when stopped', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="STOPPED" error={null} />);

    expect(screen.getByText('Detenido')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });

  it('auto-expands with a simulated log while STARTING', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="STARTING" error={null} />);

    expect(screen.getByText('Iniciando…')).toBeTruthy();
    expect(screen.getByText(/database system is ready/)).toBeTruthy();
  });

  it('shows the real error message and stays expanded on ERROR', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="ERROR" error="El puerto 5432 ya está en uso." />);

    expect(screen.getByText('Error')).toBeTruthy();
    expect(screen.getByText('El puerto 5432 ya está en uso.')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });

  it('collapses back to compact once RUNNING', () => {
    render(<ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase="RUNNING" error={null} />);

    expect(screen.getByText('En ejecución')).toBeTruthy();
    expect(screen.queryByText(/database system is ready/)).toBeNull();
  });
});
```

- [ ] **Step 15: Run the test to verify it fails on missing component**

Run: `cd ember-hub/ui && npm install && npm run test`
Expected: FAIL — `./ServiceCard` does not exist.

- [ ] **Step 16: Write `src/components/ServiceCard.tsx`**

```tsx
import { useEffect, useState, type ComponentType } from 'react';
import { ChevronDown } from 'lucide-react';
import Card, { cardShellClass, IconBadge } from './Card';
import Badge from './Badge';
import { START_SCRIPTS, STOP_SCRIPTS, type ServiceId } from '../lib/logScripts';
import type { ServicePhase } from '../lib/types';

const PHASE_LABEL: Record<ServicePhase, string> = {
  STOPPED: 'Detenido',
  STARTING: 'Iniciando…',
  RUNNING: 'En ejecución',
  STOPPING: 'Deteniendo…',
  ERROR: 'Error'
};

const PHASE_VARIANT: Record<ServicePhase, 'success' | 'warning' | 'danger' | 'neutral'> = {
  STOPPED: 'neutral',
  STARTING: 'warning',
  RUNNING: 'success',
  STOPPING: 'warning',
  ERROR: 'danger'
};

/** Reveals one script line every ~350ms while `active`, resets when `active` goes false. */
function useTypedLog(script: readonly string[], active: boolean): string[] {
  const [lines, setLines] = useState<string[]>([]);

  useEffect(() => {
    if (!active) {
      setLines([]);
      return;
    }
    setLines([]);
    let i = 0;
    const id = setInterval(() => {
      i += 1;
      setLines(script.slice(0, i));
      if (i >= script.length) clearInterval(id);
    }, 350);
    return () => clearInterval(id);
  }, [active, script]);

  return lines;
}

export default function ServiceCard({
  id,
  icon,
  title,
  phase,
  error
}: {
  id: ServiceId;
  icon: ComponentType<{ className?: string }>;
  title: string;
  phase: ServicePhase;
  error: string | null;
}) {
  const starting = phase === 'STARTING';
  const stopping = phase === 'STOPPING';
  const isError = phase === 'ERROR';
  const expanded = starting || stopping || isError;

  const startLines = useTypedLog(START_SCRIPTS[id], starting);
  const stopLines = useTypedLog(STOP_SCRIPTS[id], stopping);

  return (
    <section className={`${cardShellClass} p-4 flex flex-col min-w-0`}>
      <div className="flex items-center gap-3 min-w-0">
        <IconBadge icon={icon} />
        <h2 className="font-semibold text-lg flex-1 min-w-0">{title}</h2>
        <Badge variant={PHASE_VARIANT[phase]}>{PHASE_LABEL[phase]}</Badge>
        {expanded && <ChevronDown className="h-4 w-4 text-muted-foreground shrink-0" />}
      </div>
      {expanded && (
        <div className="mt-3 rounded-2xl bg-foreground text-background font-mono text-xs p-3 overflow-auto max-h-32">
          {isError ? (
            <p className="text-red-300">{error}</p>
          ) : (
            (starting ? startLines : stopLines).map((line, i) => <p key={i}>{line}</p>)
          )}
        </div>
      )}
    </section>
  );
}
```

`Card`'s default export isn't used here (`ServiceCard` needs the `Badge`+`ChevronDown` in its header row, which `Card`'s fixed header doesn't support) — only `cardShellClass`/`IconBadge` are reused, matching how `printing-agent/ui`'s `ConnectionCard`/`PairingSection` already mix-and-match those same exports instead of always using the full `Card` wrapper.

- [ ] **Step 17: Run the test to verify it passes**

Run: `cd ember-hub/ui && npm run test`
Expected: PASS, 4/4.

- [ ] **Step 18: Write the failing `LicenseCard` test**

```tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import LicenseCard from './LicenseCard';

describe('LicenseCard', () => {
  it('shows a neutral "Sin licencia" badge when status is NONE', () => {
    render(<LicenseCard license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('Sin licencia')).toBeTruthy();
  });

  it('shows a green "OK" badge with last-heartbeat text', () => {
    render(<LicenseCard license={{ status: 'OK', lastHeartbeatAt: new Date().toISOString(), suspendedSince: null }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('OK')).toBeTruthy();
    expect(screen.getByText(/último contacto/)).toBeTruthy();
  });

  it('shows a red "Suspendida" badge', () => {
    render(<LicenseCard license={{ status: 'SUSPENDED', lastHeartbeatAt: null, suspendedSince: new Date().toISOString() }} onSelectLicense={vi.fn()} />);

    expect(screen.getByText('Suspendida')).toBeTruthy();
  });

  it('calls onSelectLicense when the button is clicked', () => {
    const onSelectLicense = vi.fn();
    render(<LicenseCard license={{ status: 'NONE', lastHeartbeatAt: null, suspendedSince: null }} onSelectLicense={onSelectLicense} />);

    fireEvent.click(screen.getByText('Seleccionar license.key…'));

    expect(onSelectLicense).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 19: Run the test to verify it fails, then write `src/components/LicenseCard.tsx`**

Run: `cd ember-hub/ui && npm run test` — expect FAIL (`./LicenseCard` missing), then create:

```tsx
import { KeyRound } from 'lucide-react';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import type { LicenseSnapshot } from '../lib/types';

const STATUS_LABEL: Record<LicenseSnapshot['status'], string> = {
  OK: 'OK',
  SUSPENDED: 'Suspendida',
  NONE: 'Sin licencia'
};

const STATUS_VARIANT: Record<LicenseSnapshot['status'], 'success' | 'danger' | 'neutral'> = {
  OK: 'success',
  SUSPENDED: 'danger',
  NONE: 'neutral'
};

function humanizeSince(iso: string | null): string {
  if (!iso) return 'nunca';
  const ms = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(ms / 60000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `hace ${hours} h`;
  return `hace ${Math.floor(hours / 24)} d`;
}

export default function LicenseCard({
  license,
  onSelectLicense
}: {
  license: LicenseSnapshot;
  onSelectLicense: () => void;
}) {
  return (
    <Card icon={KeyRound} title="Licencia">
      <div className="flex items-center gap-2 mb-3">
        <Badge variant={STATUS_VARIANT[license.status]}>{STATUS_LABEL[license.status]}</Badge>
        {license.status === 'OK' && (
          <span className="text-sm text-muted-foreground">último contacto {humanizeSince(license.lastHeartbeatAt)}</span>
        )}
        {license.status === 'SUSPENDED' && (
          <span className="text-sm text-muted-foreground">suspendida {humanizeSince(license.suspendedSince)}</span>
        )}
      </div>
      <Button variant="primary" className="w-fit" onClick={onSelectLicense}>
        Seleccionar license.key…
      </Button>
    </Card>
  );
}
```

- [ ] **Step 20: Run the tests to verify they pass**

Run: `cd ember-hub/ui && npm run test`
Expected: PASS, 8/8 (4 `ServiceCard` + 4 `LicenseCard`).

- [ ] **Step 21: `vitest.config.ts`**

```ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true
  }
});
```

- [ ] **Step 22: `src/components/Dashboard.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Database, HardDrive, Server, Router } from 'lucide-react';
import { getStatus, startServices, stopServices, installLicense } from '../lib/api';
import type { HubStatus } from '../lib/types';
import { cardShellClass, IconBadge } from './Card';
import Button from './Button';
import ServiceCard from './ServiceCard';
import LicenseCard from './LicenseCard';

export default function Dashboard() {
  const [status, setStatus] = useState<HubStatus | null>(null);
  const [busy, setBusy] = useState(false);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, []);

  const stopped = status?.server === 'STOPPED' && status?.postgres === 'STOPPED';
  const running = status?.server === 'RUNNING';

  async function onStart() {
    setBusy(true);
    try {
      setStatus(await startServices());
    } finally {
      setBusy(false);
    }
  }

  async function onStop() {
    setBusy(true);
    try {
      setStatus(await stopServices());
    } finally {
      setBusy(false);
    }
  }

  async function onSelectLicense() {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const path = await open({ filters: [{ name: 'Licencia Ember', extensions: ['key'] }] });
    if (typeof path === 'string') {
      setStatus(await installLicense(path));
    }
  }

  async function onOpenBrowser() {
    if (!status) return;
    const { open } = await import('@tauri-apps/plugin-shell');
    await open(`http://localhost:${status.serverPort}/app/`);
  }

  async function onExit() {
    const { exit } = await import('@tauri-apps/plugin-process');
    await exit(0);
  }

  return (
    <main className="h-full p-4 flex flex-col gap-4 max-w-2xl mx-auto min-h-0 overflow-auto">
      <header className={`${cardShellClass} p-4 flex items-center justify-between gap-3 flex-wrap shrink-0`}>
        <div className="flex items-center gap-3 min-w-0">
          <IconBadge icon={Router} size="lg" />
          <div className="min-w-0">
            <h1 className="text-xl font-bold leading-tight">Ember Hub</h1>
            <p className="text-sm text-muted-foreground">Panel de control local</p>
          </div>
        </div>
        <div className="flex items-center gap-2 shrink-0">
          {stopped ? (
            <Button variant="primary" disabled={busy} onClick={onStart}>Iniciar servicios</Button>
          ) : (
            <Button variant="outline" disabled={busy || !running} onClick={onStop}>Detener</Button>
          )}
          <Button variant="outline" disabled={!running} onClick={onOpenBrowser}>Abrir en navegador</Button>
          <Button variant="outline" onClick={onExit}>Salir</Button>
        </div>
      </header>

      {status && (
        <>
          <ServiceCard id="postgres" icon={Database} title="PostgreSQL" phase={status.postgres} error={status.postgresError} />
          <ServiceCard id="minio" icon={HardDrive} title="MinIO" phase={status.minio} error={status.minioError} />
          <ServiceCard id="server" icon={Server} title="Servidor" phase={status.server} error={status.serverError} />
          <LicenseCard license={status.license} onSelectLicense={onSelectLicense} />
        </>
      )}
    </main>
  );
}
```

`onSelectLicense`/`onOpenBrowser`/`onExit` dynamic-`import()` the Tauri plugin modules (rather than a static top-level import) so this component still renders in Vitest/jsdom without those packages resolving — Task 4 installs them as real dependencies; until then nothing in this task's test suite clicks those three buttons (see Step 14/18's tests, which only render `ServiceCard`/`LicenseCard` directly, not `Dashboard`).

- [ ] **Step 23: Build the static site**

Run: `cd ember-hub/ui && npm run build`
Expected: `ember-hub/ui/dist/index.html` + assets produced, no errors.

- [ ] **Step 24: Commit**

```bash
git add ember-hub/ui
git commit -m "feat(hub): add Astro+React+Tailwind window UI for the Tauri shell"
```

---

## Task 4: Tauri shell — sidecar process, `hub.env` bootstrap, tray, license dialog, browser open

**Files:**
- Create: `ember-hub/src-tauri/Cargo.toml`
- Create: `ember-hub/src-tauri/build.rs`
- Create: `ember-hub/src-tauri/tauri.conf.json`
- Create: `ember-hub/src-tauri/src/main.rs`
- Create: `ember-hub/src-tauri/icons/` (generated, see Step 5)
- Create: `ember-hub/ui/src/lib/agent-events.ts`
- Modify: `ember-hub/ui/src/components/Dashboard.tsx`

**Interfaces:**
- Consumes: the app-image directory produced by `ember-hub/build-installer.ps1 -Stage appimage` (Task 5 wires the build order) at `ember-hub/dist/app-image/Ember Hub/`, containing `Ember Hub.exe` (jpackage launcher), `pgsql/`, `minio/`, `hub-public-key.der` — bundled by Tauri as a `resources` entry, same as printer-agent's Task 4.
- Produces: Tauri commands `get_port(): number`, `pick_license_file(): string | null` (wraps `tauri-plugin-dialog`), `open_in_browser(url: string): void` (wraps `tauri-plugin-shell`), `restart_agent(): void`; events `agent-ready` (payload: port number) and `agent-crashed` — consumed by `ember-hub/ui`'s `src/lib/api.ts`/`agent-events.ts` (Task 3).

- [ ] **Step 1: Scaffold `Cargo.toml`**

```toml
[package]
name = "ember-hub-shell"
version = "0.1.0"
edition = "2021"

[build-dependencies]
tauri-build = { version = "2", features = [] }

[dependencies]
tauri = { version = "2", features = ["tray-icon"] }
tauri-plugin-autostart = "2"
tauri-plugin-shell = "2"
tauri-plugin-dialog = "2"
serde = { version = "1", features = ["derive"] }
serde_json = "1"
rand = "0.8"

[[bin]]
name = "ember-hub-shell"
path = "src/main.rs"
```

`rand` is the one new crate beyond printer-agent's shell — used only to generate the two JWT secrets `hub.env` needs on first run (Step 3), replacing the Inno Setup Pascal script's hand-rolled (explicitly "not cryptographic") LCG with a real CSPRNG.

`build.rs`:

```rust
fn main() {
    tauri_build::build()
}
```

- [ ] **Step 2: `tauri.conf.json`**

```json
{
  "$schema": "https://schema.tauri.app/config/2",
  "productName": "Ember Hub",
  "version": "0.1.0",
  "identifier": "net.vanter.ember.hub",
  "build": {
    "beforeBuildCommand": "npm --prefix ui install && npm --prefix ui run build",
    "frontendDist": "../ui/dist"
  },
  "app": {
    "windows": [
      {
        "label": "main",
        "title": "Ember Hub",
        "width": 720,
        "height": 800,
        "minWidth": 640,
        "minHeight": 640,
        "visible": true
      }
    ],
    "security": {
      "csp": "default-src 'self'; connect-src 'self' http://127.0.0.1:*"
    }
  },
  "bundle": {
    "active": true,
    "targets": ["nsis"],
    "icon": ["icons/icon.ico"],
    "resources": {
      "../dist/app-image/Ember Hub": "app-image/Ember Hub"
    },
    "windows": {
      "nsis": {
        "installMode": "perMachine",
        "installerHooks": "installer-hooks.nsh"
      }
    }
  }
}
```

Window is one card taller than printer-agent's (`720×800` vs `720×768`, `minHeight` 640 vs 560) since this window stacks 4 cards (3 services + license) instead of printer-agent's 2-column grid + table. `installerHooks` is written in Task 5 (installer pipeline) — it doesn't exist yet at this task's end, which is fine: `cargo tauri build` in this task's Step 6 uses the debug/dev path, not the NSIS bundle.

- [ ] **Step 3: `src/main.rs` — `hub.env` bootstrap, sidecar spawn with injected env, port discovery, crash watcher, tray, license dialog, browser open**

```rust
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use rand::RngCore;
use std::collections::HashMap;
use std::io::{BufRead, BufReader, Write};
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, WindowEvent};
use tauri_plugin_autostart::{MacosLauncher, ManagerExt};

struct PortState(Arc<Mutex<u16>>);
struct AgentProcessState(Arc<Mutex<Option<Child>>>);

#[tauri::command]
fn get_port(state: tauri::State<PortState>) -> u16 {
    *state.0.lock().unwrap()
}

/// `%ProgramData%\EmberHub\hub.env` — same file `Iniciar Ember Hub.cmd` used to read before
/// launching the app; this shell now owns both writing it (first run) and reading it (every run),
/// replacing that batch shim and the Inno Setup `[Code]` section that used to generate it.
fn hub_env_path() -> PathBuf {
    let program_data = std::env::var("ProgramData").unwrap_or_else(|_| "C:\\ProgramData".into());
    PathBuf::from(program_data).join("EmberHub").join("hub.env")
}

fn random_hex(bytes: usize) -> String {
    let mut buf = vec![0u8; bytes];
    rand::thread_rng().fill_bytes(&mut buf);
    buf.iter().map(|b| format!("{:02x}", b)).collect()
}

/// Creates `hub.env` with fresh secrets if it does not exist yet (first run after install).
/// Never overwrites an existing file — this is the one-time bootstrap the old Inno Setup
/// installer used to do in `ssPostInstall`; doing it here instead of in NSIS lets it use a real
/// CSPRNG (`rand`) instead of hand-rolled Pascal LCG, and keeps the logic testable/versioned in
/// the same codebase as the rest of the shell.
fn ensure_hub_env(app_dir: &std::path::Path) -> std::io::Result<()> {
    let path = hub_env_path();
    if path.exists() {
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
        std::fs::create_dir_all(parent.join("data").join("postgres"))?;
        std::fs::create_dir_all(parent.join("data").join("minio"))?;
        std::fs::create_dir_all(parent.join("logs"))?;
        std::fs::create_dir_all(parent.join("backups"))?;
    }
    let program_data = path.parent().unwrap().to_string_lossy().to_string();
    let contents = format!(
        "# Generado por Ember Hub al primer arranque. No editar salvo el puerto.\n\
         EMBER_HUB_DATA_DIR={pd}\\data\\postgres\n\
         EMBER_HUB_MINIO_DATA_DIR={pd}\\data\\minio\n\
         EMBER_HUB_POSTGRES_BIN_DIR={app}\\pgsql\\bin\n\
         EMBER_HUB_MINIO_BIN_DIR={app}\\minio\n\
         EMBER_HUB_LICENSE_FILE={pd}\\license.key\n\
         EMBER_HUB_PUBLIC_KEY_FILE={app}\\hub-public-key.der\n\
         EMBER_HUB_STATE_FILE={pd}\\hub-state.json\n\
         EMBER_HUB_POSTGRES_PORT=5432\n\
         EMBER_HUB_MINIO_PORT=9000\n\
         EMBER_HUB_SERVER_PORT=8080\n\
         EMBER_HUB_ACTIVATION_URL=https://api.ember.vanter.net/v1/hub-activations\n\
         EMBER_HUB_HEARTBEAT_URL=https://api.ember.vanter.net/v1/hub-heartbeat\n\
         JWT_SECRET={jwt}\n\
         PLATFORM_JWT_SECRET={pjwt}\n",
        pd = program_data,
        app = app_dir.display(),
        jwt = random_hex(32),
        pjwt = random_hex(32)
    );
    let mut file = std::fs::File::create(&path)?;
    file.write_all(contents.as_bytes())
}

fn read_hub_env() -> HashMap<String, String> {
    let mut map = HashMap::new();
    if let Ok(contents) = std::fs::read_to_string(hub_env_path()) {
        for line in contents.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if let Some((key, value)) = line.split_once('=') {
                map.insert(key.trim().to_string(), value.trim().to_string());
            }
        }
    }
    map
}

fn spawn_agent(app: &AppHandle, port_state: Arc<Mutex<u16>>, agent_process: Arc<Mutex<Option<Child>>>) {
    let resource_dir = app.path().resource_dir().expect("no resource dir");
    let app_dir = resource_dir.join("app-image").join("Ember Hub");
    let exe = app_dir.join("Ember Hub.exe");

    ensure_hub_env(&app_dir).expect("failed to write hub.env");
    let env = read_hub_env();

    let mut command = Command::new(exe);
    command.env("SPRING_PROFILES_ACTIVE", "hub").envs(&env).stdout(Stdio::piped());
    let mut child = command.spawn().expect("failed to spawn Ember Hub sidecar");

    let stdout = child.stdout.take().expect("no stdout from sidecar");
    *agent_process.lock().unwrap() = Some(child);

    // Reads the "PORT=<n>" line EmberApplication's hub sidecar prints once HubControlServer is
    // listening.
    let app_handle = app.clone();
    std::thread::spawn(move || {
        let reader = BufReader::new(stdout);
        for line in reader.lines().flatten() {
            if let Some(rest) = line.strip_prefix("PORT=") {
                if let Ok(port) = rest.trim().parse::<u16>() {
                    *port_state.lock().unwrap() = port;
                    let _ = app_handle.emit("agent-ready", port);
                }
            }
        }
    });

    // Polls the same Child for an unexpected exit (crash) so the UI can show an error + retry.
    let app_handle_watch = app.clone();
    let watch_process = agent_process.clone();
    std::thread::spawn(move || loop {
        std::thread::sleep(std::time::Duration::from_secs(2));
        let mut guard = watch_process.lock().unwrap();
        match guard.as_mut() {
            Some(child) => match child.try_wait() {
                Ok(Some(_status)) => {
                    let _ = app_handle_watch.emit("agent-crashed", ());
                    *guard = None;
                    break;
                }
                Ok(None) => {}
                Err(_) => break,
            },
            None => break,
        }
    });
}

#[tauri::command]
fn restart_agent(
    app: AppHandle,
    port_state: tauri::State<PortState>,
    agent_process: tauri::State<AgentProcessState>,
) {
    if let Some(mut child) = agent_process.0.lock().unwrap().take() {
        let _ = child.kill();
    }
    *port_state.0.lock().unwrap() = 0;
    spawn_agent(&app, port_state.0.clone(), agent_process.0.clone());
}

fn main() {
    let port_state = Arc::new(Mutex::new(0u16));
    let agent_process: Arc<Mutex<Option<Child>>> = Arc::new(Mutex::new(None));
    let port_state_setup = port_state.clone();
    let agent_process_setup = agent_process.clone();
    let agent_process_exit = agent_process.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_autostart::init(MacosLauncher::LaunchAgent, None))
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(PortState(port_state.clone()))
        .manage(AgentProcessState(agent_process.clone()))
        .invoke_handler(tauri::generate_handler![get_port, restart_agent])
        .setup(move |app| {
            spawn_agent(app.handle(), port_state_setup.clone(), agent_process_setup.clone());

            let autostart = app.autolaunch();
            let _ = autostart.enable();

            let show = MenuItem::with_id(app, "show", "Mostrar Ember Hub", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Salir", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&show, &quit])?;

            let _tray = TrayIconBuilder::new()
                .menu(&menu)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "show" => {
                        if let Some(w) = app.get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { .. } = event {
                        if let Some(w) = tray.app_handle().get_webview_window("main") {
                            let _ = w.show();
                            let _ = w.set_focus();
                        }
                    }
                })
                .build(app)?;

            Ok(())
        })
        .on_window_event(|window, event| {
            // Mirrors HubDashboard's old windowClosing → onExit: closing the window stops the
            // sidecar. (Printer-agent hides to tray instead — Hub keeps its original "closing
            // means shutting down the services" behavior since a headless Hub with the window
            // hidden would otherwise silently keep Postgres/MinIO/the server running with no
            // visible way to tell; the tray's "Mostrar" still exists to bring the window back
            // while services are up.)
            if let WindowEvent::CloseRequested { .. } = event {
                // no-op: default behavior (window closes, app.run's Exit handler below fires)
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building the Ember Hub shell")
        .run(move |_app_handle, event| {
            if let tauri::RunEvent::Exit = event {
                if let Some(mut child) = agent_process_exit.lock().unwrap().take() {
                    let _ = child.kill();
                }
            }
        });
}
```

The design's confirmed "Salir" button (Task 3's `Dashboard.tsx`) calls `@tauri-apps/plugin-process`'s `exit(0)`, which triggers this same `RunEvent::Exit` cleanup — no separate Rust command needed for it. `pick_license_file`/`open_in_browser` are **not** Rust commands: Task 3's `Dashboard.tsx` calls the `tauri-plugin-dialog`/`tauri-plugin-shell` JS APIs (`@tauri-apps/plugin-dialog`'s `open()`, `@tauri-apps/plugin-shell`'s `open()`) directly — the plugins expose their own JS bindings, so no custom `#[tauri::command]` wrapper is needed for either (simpler than the interface note in this task's header implied; corrected here after actually writing the code — see this plan's own Self-Review Notes at the end).

- [ ] **Step 4: `ember-hub/ui/src/lib/agent-events.ts` (identical to printer-agent's, copied not imported)**

```ts
import { listen } from '@tauri-apps/api/event';

export type AgentShellState = 'starting' | 'ready' | 'crashed' | 'timeout';

export function watchAgentShell(
  onChange: (state: AgentShellState) => void,
  timeoutMs = 5000
): () => void {
  let settled = false;
  let pollId: ReturnType<typeof setInterval> | null = null;

  const timeout = setTimeout(() => {
    if (!settled) {
      settled = true;
      if (pollId) clearInterval(pollId);
      onChange('timeout');
    }
  }, timeoutMs);

  async function poll() {
    if (settled) return;
    const { invoke } = await import('@tauri-apps/api/core');
    const port = await invoke<number>('get_port').catch(() => 0);
    if (port > 0 && !settled) {
      settled = true;
      clearTimeout(timeout);
      if (pollId) clearInterval(pollId);
      onChange('ready');
    }
  }
  poll();
  pollId = setInterval(poll, 200);

  const unlistenCrashed = listen('agent-crashed', () => {
    onChange('crashed');
  });

  return () => {
    clearTimeout(timeout);
    if (pollId) clearInterval(pollId);
    unlistenCrashed.then((f) => f());
  };
}
```

(Polls `get_port` rather than relying solely on the `agent-ready` event — same race-condition fix printer-agent needed, report/Task 4 of that plan; ports it up front here instead of discovering it live, since we already know to.)

- [ ] **Step 5: Wire the shell state into `Dashboard.tsx`**

Add to the top of `ember-hub/ui/src/components/Dashboard.tsx` from Task 3 (keep the rest of the component body unchanged below the new state/effect, and keep all the existing imports):

```tsx
import { watchAgentShell, type AgentShellState } from '../lib/agent-events';
```

Inside the `Dashboard` function, before the existing `status`/`busy` state:

```tsx
  const [shellState, setShellState] = useState<AgentShellState>('starting');
  useEffect(() => watchAgentShell(setShellState), []);
```

Change the existing polling `useEffect` to gate on `shellState === 'ready'`:

```tsx
  useEffect(() => {
    if (shellState !== 'ready') return;
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, [shellState]);
```

And add the same starting/crashed guard printer-agent's `Dashboard.tsx` has, right before the `return (...)` that renders the header/cards:

```tsx
  if (shellState === 'starting') {
    return <main className="p-4 text-muted-foreground">Iniciando Ember Hub…</main>;
  }
  if (shellState === 'timeout' || shellState === 'crashed') {
    return (
      <main className="p-4 flex flex-col gap-3">
        <p className="text-red-700">Ember Hub no pudo iniciar.</p>
        <Button variant="primary" className="w-fit" onClick={async () => (await import('@tauri-apps/api/core')).invoke('restart_agent')}>
          Reintentar
        </Button>
      </main>
    );
  }
```

- [ ] **Step 6: Generate the Tauri icon set from the existing brand icon**

Run: `cd ember-hub/src-tauri && cargo tauri icon ../installer/ember-hub.ico`
Expected: populates `ember-hub/src-tauri/icons/`. Commit the generated folder.

- [ ] **Step 7: Build and manually smoke-test**

Run: `cd ember-hub/src-tauri && cargo tauri build --debug`
Expected: compiles; running the produced debug exe writes `%ProgramData%\EmberHub\hub.env` if it doesn't exist yet, shows "Iniciando Ember Hub…" then the dashboard with all 3 service cards "Detenido" and the license card "Sin licencia" (assuming a clean machine with no prior Hub install). Confirm in Task Manager that an `Ember Hub.exe` child process is running alongside the Tauri exe.

- [ ] **Step 8: Commit**

```bash
git add ember-hub/src-tauri ember-hub/ui/src/lib/agent-events.ts ember-hub/ui/src/components/Dashboard.tsx
git commit -m "feat(hub): add Tauri shell (hub.env bootstrap, sidecar spawn, tray, crash recovery)"
```

---

## Task 5: Rewrite the installer pipeline — Tauri bundler replaces Inno Setup

**Files:**
- Modify: `ember-hub/build-installer.ps1`
- Create: `ember-hub/src-tauri/installer-hooks.nsh`
- Delete: `ember-hub/installer/EmberHub.iss`
- Delete: `ember-hub/installer/Iniciar Ember Hub.cmd`

**Interfaces:**
- Consumes: `Build-AppImage` (this same script, unchanged logic — still produces `ember-hub/dist/app-image/Ember Hub/`, which Task 4's `tauri.conf.json` `bundle.resources` references by that exact relative path) and `Read-BuildEnv`/`build.env` (still used, but only for the CI/dev convenience of not hand-typing URLs — see Step 1, the values are now compiled into the app's own `ensure_hub_env` default strings in Task 4 rather than injected via Inno `#define`, since NSIS's scripting language has no clean equivalent to Inno's `/D` compiler defines threaded through `[Code]` — this plan accepts that trade-off explicitly, see Self-Review Notes).
- Produces: `ember-hub/dist/EmberHubSetup-<version>.exe` — same output path/naming `deploy/publish-installer.sh hub <version> <path>` already expects, no changes needed there.

- [ ] **Step 1: Replace `Build-Installer` and the header comment in `ember-hub/build-installer.ps1`**

Keep `Get-HubVersion`, `Build-Runtime`, `Build-AppImage`, and `Read-BuildEnv` exactly as they are today (Build-AppImage already assembles `pgsql/`, `minio/`, `hub-public-key.der` next to the launcher — nothing there changes). Delete the `Copy-Item (Join-Path $installerDir "Iniciar Ember Hub.cmd") $appImageDir` line from `Build-AppImage` (that shim is retired — Task 4's Rust shell reads `hub.env` itself). Replace `Build-Installer` and the header comment:

```powershell
<#
Builds the Ember Hub Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> ember-hub/dist/runtime            (jlink JRE image)
  appimage  -> ember-hub/dist/app-image          (jpackage + assembled binaries)
  installer -> ember-hub/dist/EmberHubSetup-*.exe (Tauri bundler, NSIS)
Requires: JDK 17 on PATH (java, jlink, jpackage), pnpm, mvn, Node (for ember-hub/ui), Rust +
`cargo install tauri-cli --version "^2"` for the last stage.
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")
```

Replace `Build-Installer`:

```powershell
function Clear-ReadOnlyRecurse($path) {
    if (-not (Test-Path $path)) { return }
    Get-ChildItem -Path $path -Recurse -Force -File | ForEach-Object {
        if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
            $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
        }
    }
}

function Build-Installer {
    Write-Host "== installer (Tauri) ==" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $appImageDir "Ember Hub.exe"))) { Build-AppImage }

    $tauriDir = Join-Path $hubDir "src-tauri"
    $cargoTauri = (Get-Command cargo-tauri.exe -ErrorAction SilentlyContinue) -or
                  (Get-Command cargo -ErrorAction SilentlyContinue)
    if (-not $cargoTauri) { throw "Rust/cargo not found - install Rust and `cargo install tauri-cli --version '^2'`." }

    # Same read-only app-image copy issue printer-agent's build-installer.ps1 hit (report 444):
    # jpackage's launcher exe is read-only and tauri-build's copy_resources step can't overwrite
    # a read-only destination on a second build.
    Clear-ReadOnlyRecurse (Join-Path $tauriDir "target\release\app-image")
    Clear-ReadOnlyRecurse (Join-Path $tauriDir "target\debug\app-image")

    Push-Location $tauriDir
    try {
        # cargo/tauri-cli write non-fatal "Info"/progress lines to stderr; under
        # $ErrorActionPreference="Stop" Windows PowerShell 5.1 treats any native stderr write as
        # a terminating NativeCommandError regardless of the real exit code (same bug class fixed
        # in printer-agent/build-installer.ps1 and ember-hub/build-installer.ps1's own frontend
        # step) -- relax it locally and trust $LASTEXITCODE for the real pass/fail signal.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & cargo tauri build
        $ErrorActionPreference = $prevEap
        if ($LASTEXITCODE -ne 0) { throw "cargo tauri build failed ($LASTEXITCODE)" }
    } finally { Pop-Location }

    $bundleDir = Join-Path $tauriDir "target\release\bundle\nsis"
    $produced = Get-ChildItem $bundleDir -Filter "*-setup.exe" | Select-Object -First 1
    if (-not $produced) { throw "no NSIS installer produced under $bundleDir" }

    $version = Get-HubVersion
    $out = Join-Path $distDir "EmberHubSetup-$version.exe"
    Copy-Item $produced.FullName $out -Force
    Write-Host "installer: $out" -ForegroundColor Green
}
```

`Read-BuildEnv`/`build.env` are no longer consumed by `Build-Installer` (the activation/heartbeat URLs and server port now live as defaults baked into Task 4's `ensure_hub_env` Rust code, not injected per-build) — leave the function and `build.env`/`build.env.example` in place unused for now rather than deleting them in this task; a follow-up can decide whether per-customer URL overrides are still needed (see Self-Review Notes).

- [ ] **Step 2: `ember-hub/src-tauri/installer-hooks.nsh` — directories, firewall rule, uninstall data-wipe prompt**

Tauri's NSIS bundler supports a custom `.nsh` include (`bundle.windows.nsis.installerHooks` in `tauri.conf.json`, already set in Task 4 Step 2) that defines macros the generated installer script calls at fixed points — this replaces Inno Setup's `[Dirs]`/`[Run]`/`[UninstallRun]`/`CurUninstallStepChanged` sections. `%ProgramData%\EmberHub\hub.env` itself is **not** written here (Task 4's Rust shell does that on first launch, not at install time) — this hook only creates the empty directories and manages the firewall rule/uninstall prompt, which don't depend on `hub.env` existing yet.

```nsis
; Ember Hub NSIS installer hooks (replaces EmberHub.iss's [Dirs]/[Run]/[UninstallRun]/uninstall
; prompt). See docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md Task 5.

!macro NSIS_HOOK_POSTINSTALL
  CreateDirectory "$COMMONAPPDATA\EmberHub"
  CreateDirectory "$COMMONAPPDATA\EmberHub\data\postgres"
  CreateDirectory "$COMMONAPPDATA\EmberHub\data\minio"
  CreateDirectory "$COMMONAPPDATA\EmberHub\logs"
  CreateDirectory "$COMMONAPPDATA\EmberHub\backups"

  ; Inbound firewall rule for LAN terminals - private + domain only, never public.
  ; delete-then-add so a re-install does not stack duplicate rules.
  nsExec::ExecToLog 'cmd.exe /c netsh advfirewall firewall delete rule name="Ember Hub 8080" >nul 2>&1 & netsh advfirewall firewall add rule name="Ember Hub 8080" dir=in action=allow protocol=TCP localport=8080 profile=private,domain'
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  nsExec::ExecToLog 'netsh advfirewall firewall delete rule name="Ember Hub 8080"'

  MessageBox MB_YESNO|MB_DEFBUTTON2 "Eliminar tambien los datos de Ember Hub (base de datos, licencia, respaldos) en $COMMONAPPDATA\EmberHub?  Elige 'No' para conservarlos." IDNO +2
  RMDir /r "$COMMONAPPDATA\EmberHub"
!macroend
```

The firewall rule's port (`8080`) is hardcoded here rather than templated from `build.env`'s `EMBER_HUB_SERVER_PORT` — Inno's version could inject it via `/DServerPort=`, but every installed Hub already defaults `hub.env`'s `EMBER_HUB_SERVER_PORT` to `8080` (Task 4's `ensure_hub_env`) and this project has never shipped a non-default port to a real customer (confirmed: `build.env.example`'s only committed value is `8080`) — flagged in Self-Review Notes as a known simplification, not a silent behavior change for any real install today.

- [ ] **Step 3: Remove the retired installer files**

```bash
git rm ember-hub/installer/EmberHub.iss
git rm "ember-hub/installer/Iniciar Ember Hub.cmd"
```

`ember-hub/installer/ember-hub.ico` is kept (source for Task 4 Step 6's `cargo tauri icon`).

- [ ] **Step 4: Run the full local build to confirm the pipeline end to end**

Run: `pwsh ember-hub/build-installer.ps1 -Stage all`
Expected: `ember-hub/dist/EmberHubSetup-<version>.exe` produced, no errors at any stage. (First run needs `fetch-vendor-binaries.ps1`'s portable Postgres/MinIO already staged, per `ember-hub/README.md` — unchanged prerequisite.)

- [ ] **Step 5: Commit**

```bash
git add ember-hub/build-installer.ps1 ember-hub/src-tauri/installer-hooks.nsh
git rm ember-hub/installer/EmberHub.iss
git rm "ember-hub/installer/Iniciar Ember Hub.cmd"
git commit -m "build(hub): replace Inno Setup with the Tauri bundler for the installer"
```

No CI job change in this task: `.github/workflows/lint.yml`'s existing `build-hub` job only compiles the frontend's Hub build variant (`frontend/`, `pnpm run build:hub`) — it has never run `ember-hub/build-installer.ps1` (confirmed: that job's only step is `pnpm run build:hub` + `pnpm run test:run`; per `ember-hub/README.md`, "no CI covers the .exe" today). Standing up Windows CI for the full Hub installer would additionally need `build.env` secrets and the portable Postgres/MinIO vendor download `fetch-vendor-binaries.ps1` performs — a materially bigger lift than printer-agent's CI addition (which needed neither) and out of this plan's shell-only scope; left as an explicit follow-up, not silently assumed done.

---

## Task 6: `ember-hub/VERIFY.md` — manual clean-machine checklist

**Files:**
- Create: `ember-hub/VERIFY.md`

**Interfaces:**
- Consumes: the finished installer from Task 5 (`EmberHubSetup-<version>.exe`).
- Produces: nothing consumed by later tasks — this is the last task of the plan.

- [ ] **Step 1: Write the checklist**

```markdown
# Ember Hub — manual installer verification

Run this on a **clean Windows 10/11 machine (or VM) with no JDK/JRE installed and no prior Ember
Hub install**. It verifies the packaged `EmberHubSetup-x.y.z.exe` (built by
`build-installer.ps1 -Stage installer`, needs `cargo tauri build` / the NSIS bundler), not the
raw `java -jar` path.

Fill in `[ ]` → `[x]` pass / `[F]` fail, one line per check. Record any deviation inline.
Bugs found here become their own follow-up tasks — do **not** fix them during verification.

| Field | Value |
| --- | --- |
| Tester | |
| Date | |
| Installer file | `EmberHubSetup-________.exe` |
| Hub version (`backend/pom.xml`, `-SNAPSHOT` stripped) | |
| Windows build | |
| `license.key` used | |

---

## Checklist

- [ ] **1. Clean install, no Java, `hub.env` generated on first launch.**
  `where java` before install finds nothing. Run `EmberHubSetup-x.y.z.exe`, accept the UAC
  prompt, finish the wizard. `%ProgramData%\EmberHub\{data\postgres,data\minio,logs,backups}`
  exist immediately after install (created by the NSIS post-install hook), but
  `%ProgramData%\EmberHub\hub.env` does **not** exist yet. Launch `Ember Hub.exe` from the Start
  Menu/desktop shortcut: window shows "Iniciando Ember Hub…", then `hub.env` appears (open it —
  confirm `JWT_SECRET`/`PLATFORM_JWT_SECRET` are each 64 distinct hex characters, not the same
  value twice), then the dashboard renders with 3 service cards auto-expanding one at a time with
  simulated logs (PostgreSQL → MinIO → Servidor order) before settling on "Detenido" or
  "En ejecución" depending on whether a `license.key` is already present.

- [ ] **2. No license yet — install one, services auto-start.**
  On first launch with no `license.key`, the license card shows "Sin licencia"; the 3 service
  cards show "Error" once startup is attempted (Postgres card carries the invalid-license
  message — see plan Task 1 Step 8's comment on why). Click **Seleccionar license.key…**, pick a
  valid file via the native dialog. License card flips to "OK", and — without clicking **Iniciar
  servicios** separately — PostgreSQL → MinIO → Servidor auto-expand in sequence with their
  simulated logs and settle on "En ejecución" (mirrors `HubDashboard.onSelectLicense`'s old
  auto-start behavior).

- [ ] **3. Abrir en navegador.**
  Once "Servidor: En ejecución", **Abrir en navegador** is enabled; clicking it opens the SPA at
  `http://localhost:8080/app/` in the system's default browser (unchanged — this button never
  routed through the Swing dashboard's own window, and still doesn't).

- [ ] **4. Detener.**
  Click **Detener**. All 3 cards auto-expand with their short simulated shutdown log
  ("Cerrando conexiones…"-style) and settle back on "Detenido" within a few seconds. **Iniciar
  servicios** re-enables.

- [ ] **5. Heartbeat badge reflects real state.**
  With services running and a valid license, confirm the license card's "último contacto"
  timestamp advances over time (driven by the real `HeartbeatScheduler`, unchanged) without any
  manual refresh — the window is polling `/api/status` every ~1.5s.

- [ ] **6. Auto-start on log-on.**
  Reboot, log in, do nothing else. Within ~30s the window/tray shows the dashboard again with
  services already running (Tauri autostart + `hub.env` already present from run 1).

- [ ] **7. `hub.env` survives an update.**
  Install `x.y.(z+1)` over the existing install. `hub.env` is untouched (same `JWT_SECRET` —
  compare timestamp/hash before/after); services still start without re-entering the license.

- [ ] **8. Uninstall — keep data.**
  Uninstall via *Agregar o quitar programas* → the "¿Eliminar tambien los datos…?" prompt
  appears → choose **No**. `%ProgramData%\EmberHub\` remains (including `hub.env`,
  `license.key`, `data\`). Reinstall → launches straight into "En ejecución" with no re-licensing.

- [ ] **9. Uninstall — remove data.**
  Uninstall again, choose **Sí** at the prompt. `%ProgramData%\EmberHub\` is gone entirely.

- [ ] **10. Firewall rule present, no inbound prompt.**
  After install, `netsh advfirewall firewall show rule name="Ember Hub 8080"` shows the rule
  (private+domain only). At no point does Windows itself show a firewall "Allow access?" dialog
  during normal use — the rule is pre-authorized by the installer hook.

- [ ] **11. Sidecar crash recovery.**
  While "En ejecución", kill `Ember Hub.exe`'s underlying `java.exe` child directly in Task
  Manager (**not** the Tauri parent). Within ~2s the window shows "Ember Hub no pudo iniciar." +
  **Reintentar**. Click it → the sidecar respawns (`hub.env` already exists, so this is fast),
  window reaches "En ejecución" again without a reinstall.

- [ ] **12. Closing the window stops the services (unlike printer-agent's close-to-tray).**
  Click the window's close (X) button → the window and the `Ember Hub.exe`/`java.exe` child
  process both exit (Task 4's design deliberately does **not** hide-to-tray for Hub, unlike
  printer-agent — see the comment in `src-tauri/src/main.rs`). Confirm no orphaned `java.exe`
  remains in Task Manager afterward.

- [ ] **13. Tray "Mostrar" reopens a still-running window; "Salir" stops everything.**
  While the window is visible and services are running, use the tray icon's **Mostrar** — no
  behavior change expected (window is already shown), just confirms the tray menu responds. Then
  use tray **Salir**: both the Tauri process and the `java.exe` child disappear from Task Manager.

---

## Result

**Go / No-Go:** ____

Blocking failures (if any), each to be filed as its own follow-up task:

1.
2.
```

- [ ] **Step 2: Run the manual checklist on a clean Windows machine/VM**

Ops action, not automatable — record pass/fail per item in the table at the top, same convention as `printing-agent/VERIFY.md`.

- [ ] **Step 3: Commit**

```bash
git add ember-hub/VERIFY.md
git commit -m "docs(hub): add VERIFY.md for the Tauri shell"
```

---

## Self-Review Notes (already applied above)

- **Spec coverage:** §2.2 (Tauri-as-parent, Java sidecar) → Task 4. §2.3 (loopback HTTP, `PORT=` stdout line, CORS) → Tasks 1/2/4. §2.4 (Astro+React+Tailwind, separate project, shared tokens, own component copies) → Task 3. §2.5's Hub-specific line ("estado de Postgres/MinIO/Servidor, selección de license.key, estado de licencia/heartbeat, botón Abrir en navegador") → Tasks 1 (API)/3 (UI)/4 (dialog+shell-open). This plan's own confirmed-in-brainstorming additions (per-service simulated logs honest about real errors, single Iniciar/Detener/Salir, license badge) → Task 1 (`ServicePhase`/error fields)/Task 3 (`ServiceCard`/`logScripts.ts`). §4 (error handling: sidecar fails to start / crashes / poll failure) → Tasks 1 (real error fields)/4 (timeout/crash/retry, identical to printer-agent). §5 (packaging) → Task 5, extended with the Hub-specific `hub.env`/firewall/uninstall-prompt migration off Inno Setup's Pascal script (not present in printer-agent's plan — Hub's installer previously did meaningfully more than printer-agent's). §6 (testing) → Tasks 1 (hermetic Java tests against fakes)/3 (component tests)/6 (manual VERIFY). §7-equivalent risks for Hub specifically (own risk, not in the shared spec): NSIS's Pascal-Script-free scripting language can't replicate Inno's `[Code]` section 1:1 — resolved by moving secret generation into Rust (Task 4) and keeping only directory/firewall/uninstall-prompt logic in NSIS (Task 5), documented inline in both tasks rather than left implicit.
- **Placeholder scan:** no TBD/TODO; every step has literal code or an exact command. One design decision is deliberately narrower than a first pass assumed and is called out inline rather than silently changed: Task 4's Interfaces line originally implied `pick_license_file`/`open_in_browser` would be custom Rust `#[tauri::command]`s; while writing the actual `main.rs` (Step 3) it became clear the `tauri-plugin-dialog`/`tauri-plugin-shell` JS bindings cover both directly, so no such commands exist in the final code — flagged in Step 3's own text instead of leaving a stale interface promise.
- **Type consistency:** `ServicePhase` (`STOPPED|STARTING|RUNNING|STOPPING|ERROR`) is spelled identically in Task 1's Java enum, `HubControlServer`'s `StatusDto` (via `.name()`), and Task 3's TS `ServicePhase` union + `ServiceCard`'s `PHASE_LABEL`/`PHASE_VARIANT` records. `HubOrchestrator.HubStatusSnapshot`'s field names (`postgres`, `postgresError`, `minio`, `minioError`, `server`, `serverError`, `license`, `serverPort`) match `StatusDto`'s JSON keys exactly, which match `HubStatus` (TS) exactly. `LicenseSnapshot.status` string constants (`OK`/`SUSPENDED`/`NONE`) match TS's `LicenseStatus` union and `LicenseCard`'s `STATUS_LABEL`/`STATUS_VARIANT` keys. `get_port`/`restart_agent` Tauri command names match between Task 4's `main.rs` `invoke_handler!` list and Task 3/4's `invoke(...)` call sites (same two names printer-agent uses, reused verbatim; `open_folder` is **not** carried over — Hub has no logs-folder button in the approved design, unlike printer-agent).
- **Scope check:** this plan is Ember Hub v2 only, and only the shell/presentation layer — no change to `PortableDatabaseBootstrap`, `PortableMinioBootstrap`, `LicenseService`, `HeartbeatScheduler`, or any tenant-facing backend code. The one non-shell-looking change (`HubBootstrapRunner`'s new overload, Task 1 Step 2) is additive and behavior-preserving, verified by the old no-arg method delegating unchanged. Deferred, not silently dropped: a Windows CI job for the full Hub installer pipeline (Task 5's closing note) and reconciling `ember-hub/build.env`'s per-customer URL overrides with Task 4's now-hardcoded defaults in `ensure_hub_env` (Task 5 Step 1's note) are both flagged as explicit follow-ups outside this plan, not assumed-solved.
