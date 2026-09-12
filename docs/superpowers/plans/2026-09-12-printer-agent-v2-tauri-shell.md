# Printer Agent v2 (Tauri shell) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **This project's convention (see `PROGRESS.md`/`CLAUDE.md`): one task per context window, `/clear` between tasks, and a sequential numbered report in `/reports/` per completed task** — follow that cadence regardless of which sub-skill executes the tasks.

**Goal:** Replace printer-agent's Swing UI (`AgentDashboard`/`AgentTrayIcon`/`PairDialog`) with a Tauri + WebView2 native window (Astro + React + Tailwind), with the Java agent running headless as a spawned sidecar process talking to the window over a new loopback-only local HTTP server — full functional parity with today's Swing dashboard, no new user-facing features.

**Architecture:** Two processes. The installed app becomes a Tauri (Rust) binary that owns the window, tray icon, and autostart; on launch it spawns the existing jlink/jpackage Java app-image as a child process with no window of its own. The Java process gains one new class, `LocalControlServer` (JDK `HttpServer`, bound to `127.0.0.1` on an OS-assigned port), which exposes `StatusHub`/pairing/printer/diagnostics reads and actions as small JSON endpoints; it prints `PORT=<n>` to stdout once listening, which the Rust side reads to know where to send requests. The React UI polls `/api/status` every ~1.5s and calls the other endpoints on user action — no WebSocket/SSE, matching the project's existing decision to avoid persistent local connections (see spec §2.3/§2.7 reference).

**Tech Stack:** Java 17 (unchanged core: `AgentRunner`, `StatusHub`, `PairingClient`, `WindowsPrinterEnumerator`, `WindowsPrintQueueSender`, `credential/*`), JDK `com.sun.net.httpserver.HttpServer` (no new Java dependency), Astro 7 + `@astrojs/react` + React 19 + Tailwind 4 (`@tailwindcss/vite`) for the window's UI, Tauri 2.x (Rust) for the native shell/tray/sidecar/installer.

**Spec:** `docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md` (covers both Ember Hub v2 and printer-agent v2; this plan implements **only** the printer-agent v2 parts — Ember Hub v2 is a separate plan, executed after this one is verified).

## Global Constraints

- No new Java dependency for the control server — JDK `HttpServer` only (spec §2.3).
- `LocalControlServer` binds `127.0.0.1` only, never `0.0.0.0` — no auth needed, never add any (spec §2.3).
- Live updates are polling (~1–2s), never WebSocket/SSE for this local channel (spec §2.3/§2.7).
- Fase 1 = functional parity with today's Swing `AgentDashboard`/`AgentTrayIcon`/`PairDialog` — **no** `Reiniciar Servicio` / `Configurar RAW` (mockup-only, deferred to a future phase 2, spec §2.5/§8). "Imprimir página de prueba", "Abrir carpeta de logs" and "Copiar diagnóstico" **are** in scope — they already exist today.
- `AgentRunner`, `StatusHub`, `PairingClient`, `WindowsPrinterEnumerator`, `WindowsPrintQueueSender`, `PrintJobHandler`, `credential/*` are **not modified** — `LocalControlServer` only calls their existing public methods (spec §2.2).
- The Astro/React project is its own package (`printing-agent/ui/`), not a shared module with `frontend/` or `landing/` — copy only the Tailwind 4 design tokens (spec §2.4).
- Brand red token to reuse: `--primary: oklch(0.395 0.175 28.5)` (from `frontend/src/index.css`); radius base `--radius: 0.625rem`; font `'Inter', sans-serif`.
- Commit policy per `CLAUDE.md`: scoped `git add` (never `-A`/`.`), Conventional Commits, lowercase, **no** `Co-authored-by`/`Signed-off-by`/AI signature lines, on branch `spec/tauri-native-shells`.

---

## Task 1: `LocalControlServer` — the Java↔Tauri bridge

**Files:**
- Create: `printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/control/LocalControlServerTest.java`

**Interfaces:**
- Consumes: `com.vanter.emberagent.status.StatusHub` (`snapshot()`, `Snapshot`, `JobRecord`, `Phase` — existing, unchanged), `com.vanter.emberagent.credential.CredentialStore` (`load()`, `save(AgentCredential)` — existing), `com.vanter.emberagent.AgentRunner` (`requestReconnect()` — existing, public), `com.vanter.emberagent.PairingClient` (`new PairingClient(CredentialStore)`, `redeem(String backendBaseUrl, String code)` throwing `PairingException` — existing), `com.vanter.emberagent.WindowsPrinterEnumerator` (`enumerate()` returning `List<DiscoveredPrinter>` — existing), `com.vanter.emberagent.WindowsPrintQueueSender` (`print(PrinterConfigClient.PrinterConfigDto, String)` — existing), `com.vanter.emberagent.DiagnosticsReport` (`build(Snapshot, CredentialStore)` — existing), `com.vanter.emberagent.AgentPaths` (`logsDir()` — existing), `com.vanter.emberagent.AgentCredential` record `(String apiKey, String backendBaseUrl)`, `com.vanter.emberagent.DiscoveredPrinter` record `(String name, String driverName, String portName, boolean inkjetGuess)`.
- Produces: `public LocalControlServer(StatusHub hub, CredentialStore store, AgentRunner runner)`; `public int start() throws IOException` (binds, returns the bound port); `public void stop()`. Task 2 (`Main`) consumes exactly this constructor + `start()`/`stop()`.

- [ ] **Step 1: Write the failing test file**

```java
package com.vanter.emberagent.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.AgentRunner;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalControlServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private StatusHub hub;
    private FakeStore store;
    private LocalControlServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        hub = new StatusHub();
        store = new FakeStore();
        AgentRunner runner = new AgentRunner(store, hub);
        server = new LocalControlServer(hub, store, runner);
        int port = server.start();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void status_reflectsStatusHubSnapshot() throws Exception {
        hub.setConnected("agent-123", 2);

        HttpResponse<String> res = get("/api/status");

        assertEquals(200, res.statusCode());
        JsonNode body = mapper.readTree(res.body());
        assertEquals("CONNECTED", body.get("phase").asText());
        assertEquals("agent-123", body.get("agentId").asText());
        assertEquals(2, body.get("printerCount").asInt());
    }

    @Test
    void status_recentJobsRoundTrip() throws Exception {
        hub.recordJob(new StatusHub.JobRecord(
                java.time.Instant.now(), "Cocina", "COCINA-1", "OK", null));

        JsonNode body = mapper.readTree(get("/api/status").body());

        assertEquals(1, body.get("recentJobs").size());
        assertEquals("COCINA-1", body.get("recentJobs").get(0).get("queue").asText());
    }

    @Test
    void pair_withApiKey_savesCredentialDirectly() throws Exception {
        HttpResponse<String> res = post("/api/pair",
                "{\"apiKey\":\"key-1\",\"backendUrl\":\"https://api.ember.test/v1\"}");

        assertEquals(200, res.statusCode());
        assertEquals(Optional.of(new AgentCredential("key-1", "https://api.ember.test/v1")), store.saved);
    }

    @Test
    void pair_missingBackendUrl_returns400AndDoesNotSave() throws Exception {
        HttpResponse<String> res = post("/api/pair", "{\"apiKey\":\"key-1\"}");

        assertEquals(400, res.statusCode());
        assertTrue(store.saved.isEmpty());
    }

    @Test
    void printers_returnsCachedListAsJsonArray() throws Exception {
        HttpResponse<String> res = get("/api/printers");

        assertEquals(200, res.statusCode());
        assertTrue(mapper.readTree(res.body()).isArray());
    }

    @Test
    void testPrint_noQueueSelected_returns400() throws Exception {
        HttpResponse<String> res = post("/api/test-print", "{}");

        assertEquals(400, res.statusCode());
    }

    @Test
    void diagnostics_returnsPlainTextReport() throws Exception {
        HttpResponse<String> res = get("/api/diagnostics");

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("Ember Agent"));
    }

    @Test
    void paths_returnsLogsDir() throws Exception {
        HttpResponse<String> res = get("/api/paths");

        assertEquals(200, res.statusCode());
        assertTrue(mapper.readTree(res.body()).get("logsDir").asText().contains("logs"));
    }

    @Test
    void status_rejectsNonGet() throws Exception {
        HttpResponse<String> res = post("/api/status", "{}");

        assertEquals(405, res.statusCode());
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

    private static final class FakeStore implements CredentialStore {
        private Optional<AgentCredential> saved = Optional.empty();

        @Override
        public Optional<AgentCredential> load() {
            return saved;
        }

        @Override
        public void save(AgentCredential credential) {
            saved = Optional.of(credential);
        }

        @Override
        public void clear() {
            saved = Optional.empty();
        }

        @Override
        public boolean isEncrypted() {
            return false;
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails on missing class**

Run: `mvn -f printing-agent/pom.xml -q -Dtest=LocalControlServerTest test`
Expected: FAIL — compile error, `com.vanter.emberagent.control.LocalControlServer` does not exist.

- [ ] **Step 3: Write `LocalControlServer`**

```java
package com.vanter.emberagent.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.AgentPaths;
import com.vanter.emberagent.AgentRunner;
import com.vanter.emberagent.DiagnosticsReport;
import com.vanter.emberagent.DiscoveredPrinter;
import com.vanter.emberagent.PairingClient;
import com.vanter.emberagent.PairingException;
import com.vanter.emberagent.PrinterConfigClient;
import com.vanter.emberagent.WindowsPrintQueueSender;
import com.vanter.emberagent.WindowsPrinterEnumerator;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loopback-only HTTP bridge the Tauri shell polls/calls instead of the old Swing dashboard reading
 * {@link StatusHub} in-process (spec
 * docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md §2.3/§3). Binds only to
 * 127.0.0.1 on an OS-assigned port; no auth, because the channel never leaves this machine. Every
 * handler mirrors an existing AgentDashboard/PairDialog behavior — this class is new plumbing, not
 * new business logic.
 */
public final class LocalControlServer {

    private final StatusHub hub;
    private final CredentialStore store;
    private final AgentRunner runner;
    private final PairingClient pairingClient;
    private final WindowsPrinterEnumerator enumerator = new WindowsPrinterEnumerator();
    private final WindowsPrintQueueSender printQueueSender = new WindowsPrintQueueSender();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<List<DiscoveredPrinter>> printersCache = new AtomicReference<>(List.of());
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(daemonThreadFactory());

    private HttpServer httpServer;

    public LocalControlServer(StatusHub hub, CredentialStore store, AgentRunner runner) {
        this.hub = hub;
        this.store = store;
        this.runner = runner;
        this.pairingClient = new PairingClient(store);
    }

    /** Starts listening on 127.0.0.1 at an OS-assigned port and returns that port. */
    public int start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/status", this::handleStatus);
        httpServer.createContext("/api/pair", this::handlePair);
        httpServer.createContext("/api/printers", this::handlePrinters);
        httpServer.createContext("/api/test-print", this::handleTestPrint);
        httpServer.createContext("/api/diagnostics", this::handleDiagnostics);
        httpServer.createContext("/api/paths", this::handlePaths);
        httpServer.setExecutor(Executors.newCachedThreadPool(daemonThreadFactory()));
        httpServer.start();

        printersCache.set(enumerator.enumerate());
        scheduler.scheduleAtFixedRate(
                () -> printersCache.set(enumerator.enumerate()), 30, 30, TimeUnit.SECONDS);

        return httpServer.getAddress().getPort();
    }

    public void stop() {
        scheduler.shutdownNow();
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
        sendJson(exchange, 200, StatusDto.from(hub.snapshot()));
    }

    private void handlePair(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        PairRequest req = mapper.readValue(exchange.getRequestBody(), PairRequest.class);
        try {
            if (req.apiKey() != null && !req.apiKey().isBlank()) {
                requireField(req.backendUrl(), "backendUrl");
                store.save(new AgentCredential(req.apiKey().trim(), req.backendUrl().trim()));
            } else {
                requireField(req.code(), "code");
                requireField(req.backendUrl(), "backendUrl");
                pairingClient.redeem(req.backendUrl().trim(), req.code().trim());
            }
            runner.requestReconnect();
            sendJson(exchange, 200, StatusDto.from(hub.snapshot()));
        } catch (PairingException | IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private void handlePrinters(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, printersCache.get());
    }

    private void handleTestPrint(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        TestPrintRequest req = mapper.readValue(exchange.getRequestBody(), TestPrintRequest.class);
        if (req.queue() == null || req.queue().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "No hay ninguna cola seleccionada."));
            return;
        }
        boolean inkjet = printersCache.get().stream()
                .anyMatch(p -> p.name().equals(req.queue()) && p.inkjetGuess());
        String renderMode = inkjet ? "DRIVER" : "RAW";
        PrinterConfigClient.PrinterConfigDto dto = new PrinterConfigClient.PrinterConfigDto(
                "test", "test", "KITCHEN", "WINDOWS_QUEUE",
                null, null, null, req.queue(), renderMode, req.queue(), true);
        String ticket = "*** Ember Agent ***\nPrueba de impresión\n" + Instant.now() + "\n";
        try {
            printQueueSender.print(dto, ticket);
            sendJson(exchange, 200,
                    Map.of("message", "Enviado a '" + req.queue() + "' (" + renderMode + ")."));
        } catch (Exception e) {
            sendJson(exchange, 500, Map.of("error", "Falló: " + e.getMessage()));
        }
    }

    private void handleDiagnostics(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendText(exchange, 200, DiagnosticsReport.build(hub.snapshot(), store));
    }

    private void handlePaths(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, Map.of("logsDir", AgentPaths.logsDir().toString()));
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

    private void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void requireField(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " es obligatorio.");
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "ember-agent-control");
            t.setDaemon(true);
            return t;
        };
    }

    // --- wire DTOs -------------------------------------------------------------------

    private record PairRequest(String code, String apiKey, String backendUrl) {}

    private record TestPrintRequest(String queue) {}

    private record JobDto(String at, String role, String queue, String result, String error) {
        static JobDto from(StatusHub.JobRecord r) {
            return new JobDto(
                    r.at() == null ? null : DateTimeFormatter.ISO_INSTANT.format(r.at()),
                    r.role(), r.queue(), r.result(), r.error());
        }
    }

    private record StatusDto(
            String phase, String detail, String lastSeen, String agentId,
            int printerCount, List<JobDto> recentJobs) {
        static StatusDto from(StatusHub.Snapshot s) {
            return new StatusDto(
                    s.phase().name(),
                    s.detail(),
                    s.lastSeen() == null ? null : DateTimeFormatter.ISO_INSTANT.format(s.lastSeen()),
                    s.agentId(),
                    s.printerCount(),
                    s.recentJobs().stream().map(JobDto::from).toList());
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -f printing-agent/pom.xml -q -Dtest=LocalControlServerTest test`
Expected: PASS, 9/9.

- [ ] **Step 5: Run the full printer-agent suite to confirm no regressions**

Run: `mvn -f printing-agent/pom.xml test`
Expected: PASS, previous 41 + 9 new = 50/50 (exact prior count may drift — confirm it matches "previous total + 9" rather than a hardcoded number).

- [ ] **Step 6: Commit**

```bash
git add printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java printing-agent/src/test/java/com/vanter/emberagent/control/LocalControlServerTest.java
git commit -m "feat(print-agent): add loopback local control server for the Tauri shell"
```

---

## Task 2: Collapse `Main` to a single headless sidecar mode; remove the Swing UI

**Files:**
- Modify: `printing-agent/src/main/java/com/vanter/emberagent/Main.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java`
- Delete: `printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java`
- Modify: `printing-agent/jlink-modules.txt`

**Interfaces:**
- Consumes: `LocalControlServer` from Task 1 (`new LocalControlServer(StatusHub, CredentialStore, AgentRunner)`, `start()`, `stop()`), `AgentRunner` (unchanged: `runForever()`, `stop()`), `CredentialStores.forThisMachine()` (unchanged).
- Produces: `Main` prints exactly one line `PORT=<n>` to stdout once the control server is listening — Task 4 (Tauri `main.rs`) parses this exact line/prefix from the sidecar's stdout.

There are no existing tests for `AgentDashboard`/`AgentTrayIcon`/`PairDialog` (confirmed: `grep -rl "AgentDashboard\|AgentTrayIcon\|PairDialog" printing-agent/src/test` returns nothing) — safe to delete outright, nothing to update on the test side for the deletion itself.

- [ ] **Step 1: Replace `Main.java`**

```java
package com.vanter.emberagent;

import com.vanter.emberagent.control.LocalControlServer;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.credential.CredentialStores;
import com.vanter.emberagent.status.StatusHub;
import java.io.IOException;

/**
 * Entry point. Runs headless: the {@link AgentRunner} loop on a background thread plus a
 * {@link LocalControlServer} that a separate Tauri shell process polls/calls instead of the old
 * Swing dashboard reading {@link StatusHub} in-process (spec
 * docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md). Prints "PORT=&lt;n&gt;" to
 * stdout once the control server is listening — the Tauri shell reads that single line from this
 * process's stdout to know where to send requests. No more --tray/--headless flags: this process
 * never owns a window.
 */
public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        CredentialStore store = CredentialStores.forThisMachine();
        StatusHub status = new StatusHub();
        AgentRunner runner = new AgentRunner(store, status);

        Thread worker = new Thread(runner::runForever, "ember-agent-runner");
        worker.setDaemon(false);
        worker.start();

        LocalControlServer controlServer = new LocalControlServer(status, store, runner);
        int port = controlServer.start();
        System.out.println("PORT=" + port);
        System.out.flush();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            runner.stop();
            controlServer.stop();
        }, "ember-agent-shutdown"));

        worker.join();
    }
}
```

- [ ] **Step 2: Delete the Swing UI classes**

```bash
git rm printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java
git rm printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java
git rm printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java
```

- [ ] **Step 3: Add `jdk.httpserver` to the embedded runtime's module list**

`com.sun.net.httpserver.HttpServer` (used by `LocalControlServer`) lives in the JDK-specific `jdk.httpserver` module, which is **not** included by the `java.se` aggregator already in `printing-agent/jlink-modules.txt` — without this, `jlink` produces a runtime image that throws `NoClassDefFoundError` for `HttpServer` at first request.

Edit `printing-agent/jlink-modules.txt`:

```
# Modules baked into the Ember Agent's embedded JRE (build-installer.ps1 -Stage runtime).
# The shaded agent jar is not modular, so this is a conservative superset:
# java.se covers Swing/AWT/desktop (still needed by WindowsPrintQueueSender's java.awt.print /
# javax.print, even with the Swing UI gone) + java.net.http + JDBC; the jdk.* entries add crypto,
# JNA's native access path, charsets, the es/en locale data, and the embedded HTTP control server.
java.se
jdk.crypto.ec
jdk.crypto.cryptoki
jdk.unsupported
jdk.management
jdk.zipfs
jdk.localedata
jdk.charsets
jdk.httpserver
```

- [ ] **Step 4: Compile and run the full suite**

Run: `mvn -f printing-agent/pom.xml test`
Expected: PASS — same count as the end of Task 1 (deleting the untested Swing classes removes no tests; `Main` has no direct test).

- [ ] **Step 5: Commit**

```bash
git add printing-agent/src/main/java/com/vanter/emberagent/Main.java printing-agent/jlink-modules.txt
git add printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java
git commit -m "refactor(print-agent): drop Swing UI, run headless with the local control server as the only surface"
```

---

## Task 3: `printing-agent/ui/` — Astro + React + Tailwind window content

**Files:**
- Create: `printing-agent/ui/package.json`
- Create: `printing-agent/ui/astro.config.mjs`
- Create: `printing-agent/ui/tsconfig.json`
- Create: `printing-agent/ui/src/styles/global.css`
- Create: `printing-agent/ui/src/layouts/Layout.astro`
- Create: `printing-agent/ui/src/pages/index.astro`
- Create: `printing-agent/ui/src/lib/api.ts`
- Create: `printing-agent/ui/src/lib/types.ts`
- Create: `printing-agent/ui/src/components/Dashboard.tsx`
- Create: `printing-agent/ui/src/components/StatusSection.tsx`
- Create: `printing-agent/ui/src/components/PairingSection.tsx`
- Create: `printing-agent/ui/src/components/PrintersSection.tsx`
- Create: `printing-agent/ui/src/components/JobsTable.tsx`
- Create: `printing-agent/ui/src/components/FooterActions.tsx`
- Test: `printing-agent/ui/src/components/PairingSection.test.tsx`
- Test: `printing-agent/ui/src/components/JobsTable.test.tsx`
- Create: `printing-agent/ui/vitest.config.ts`

**Interfaces:**
- Consumes: `LocalControlServer`'s JSON contract from Task 1 (`/api/status`, `/api/pair`, `/api/printers`, `/api/test-print`, `/api/diagnostics`, `/api/paths`) and a Tauri-provided `invoke('get_port')` command (Task 4 produces it) plus an `agent-ready` / `agent-crashed` event pair (Task 4 emits them).
- Produces: a static build at `printing-agent/ui/dist/` — Task 4's `tauri.conf.json` `frontendDist` points here, and its `beforeBuildCommand` runs this project's `npm run build`.

This task's components read from a not-yet-existing `@tauri-apps/api` global (`invoke`, `listen`) — during **this task**, stub it so the UI is independently buildable/testable before Task 4 exists; Task 4 supplies the real Tauri runtime that satisfies the same import.

- [ ] **Step 1: Scaffold `package.json`**

```json
{
  "name": "ember-agent-ui",
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

- [ ] **Step 2: `astro.config.mjs`**

```js
// @ts-check
import { defineConfig } from 'astro/config';
import react from '@astrojs/react';
import tailwindcss from '@tailwindcss/vite';

// Static single-page build loaded directly by Tauri's WebView2 (no i18n/sitemap needed —
// single-tenant local app, Spanish-only, matches the rest of Hub/agent UI).
export default defineConfig({
  integrations: [react()],
  server: { port: 5175 },
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

- [ ] **Step 4: Design tokens — `src/styles/global.css`**

Values copied from `frontend/src/index.css` (Tailwind 4 `@theme inline`) so the window matches the rest of the product without depending on that project's code.

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

- [ ] **Step 5: `src/layouts/Layout.astro`**

```astro
---
import '../styles/global.css';
---
<html lang="es">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Ember Agent</title>
  </head>
  <body>
    <slot />
  </body>
</html>
```

- [ ] **Step 6: `src/pages/index.astro`**

```astro
---
import Layout from '../layouts/Layout.astro';
import Dashboard from '../components/Dashboard';
---
<Layout>
  <Dashboard client:load />
</Layout>
```

- [ ] **Step 7: `src/lib/types.ts`**

```ts
export type Phase = 'UNPAIRED' | 'CONNECTING' | 'CONNECTED' | 'RETRYING';

export interface JobRecord {
  at: string | null;
  role: string | null;
  queue: string | null;
  result: string | null;
  error: string | null;
}

export interface Status {
  phase: Phase;
  detail: string | null;
  lastSeen: string | null;
  agentId: string | null;
  printerCount: number;
  recentJobs: JobRecord[];
}

export interface DiscoveredPrinter {
  name: string;
  driverName: string;
  portName: string;
  inkjetGuess: boolean;
}
```

- [ ] **Step 8: `src/lib/api.ts`**

```ts
import { invoke } from '@tauri-apps/api/core';
import type { DiscoveredPrinter, Status } from './types';

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

export async function getStatus(): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/status`));
}

export async function getPrinters(): Promise<DiscoveredPrinter[]> {
  return asJson<DiscoveredPrinter[]>(await fetch(`${await base()}/api/printers`));
}

export async function pairWithCode(code: string, backendUrl: string): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ code, backendUrl })
  }));
}

export async function pairWithApiKey(apiKey: string, backendUrl: string): Promise<Status> {
  return asJson<Status>(await fetch(`${await base()}/api/pair`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKey, backendUrl })
  }));
}

export async function testPrint(queue: string): Promise<{ message: string }> {
  return asJson<{ message: string }>(await fetch(`${await base()}/api/test-print`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ queue })
  }));
}

export async function getDiagnostics(): Promise<string> {
  const res = await fetch(`${await base()}/api/diagnostics`);
  return res.text();
}

export async function getLogsDir(): Promise<string> {
  const data = await asJson<{ logsDir: string }>(await fetch(`${await base()}/api/paths`));
  return data.logsDir;
}
```

- [ ] **Step 9: `src/components/StatusSection.tsx`**

```tsx
import type { Status } from '../lib/types';

const DOT_COLOR: Record<Status['phase'], string> = {
  CONNECTED: 'bg-emerald-500',
  CONNECTING: 'bg-amber-500',
  RETRYING: 'bg-amber-500',
  UNPAIRED: 'bg-red-700'
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

export default function StatusSection({ status }: { status: Status | null }) {
  if (!status) {
    return <div className="rounded-lg border border-border p-4 text-muted-foreground">Cargando…</div>;
  }
  return (
    <section className="rounded-lg border border-border p-4">
      <div className="flex items-center gap-2 mb-3">
        <span className={`inline-block h-3 w-3 rounded-full ${DOT_COLOR[status.phase]}`} />
        <h2 className="font-semibold text-lg">Conexión</h2>
      </div>
      <dl className="grid grid-cols-2 gap-y-1 text-sm">
        <dt className="text-muted-foreground">Estado</dt>
        <dd>{status.detail ?? status.phase}</dd>
        <dt className="text-muted-foreground">Última vez visto</dt>
        <dd>{humanizeSince(status.lastSeen)}</dd>
        <dt className="text-muted-foreground">Impresoras</dt>
        <dd>{status.printerCount}</dd>
      </dl>
    </section>
  );
}
```

- [ ] **Step 10: `src/components/PairingSection.tsx`**

```tsx
import { useState } from 'react';
import { pairWithApiKey, pairWithCode } from '../lib/api';

const DEFAULT_BACKEND = 'https://api.ember.vanter.net/v1';

export default function PairingSection({ onPaired }: { onPaired: () => void }) {
  const [mode, setMode] = useState<'code' | 'key'>('code');
  const [code, setCode] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [backendUrl, setBackendUrl] = useState(DEFAULT_BACKEND);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setMessage('Procesando…');
    try {
      if (mode === 'key') {
        await pairWithApiKey(apiKey.trim(), backendUrl.trim());
      } else {
        await pairWithCode(code.trim().toUpperCase(), backendUrl.trim());
      }
      setMessage(null);
      onPaired();
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Error desconocido');
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="rounded-lg border border-border p-4">
      <h2 className="font-semibold text-lg mb-3">Emparejar este agente</h2>
      {mode === 'code' ? (
        <input
          className="border border-border rounded-md px-2 py-1 w-full mb-2 uppercase"
          placeholder="Código de 10 caracteres"
          maxLength={10}
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
      ) : (
        <input
          className="border border-border rounded-md px-2 py-1 w-full mb-2"
          placeholder="API key"
          value={apiKey}
          onChange={(e) => setApiKey(e.target.value)}
        />
      )}
      <input
        className="border border-border rounded-md px-2 py-1 w-full mb-2 text-sm text-muted-foreground"
        value={backendUrl}
        onChange={(e) => setBackendUrl(e.target.value)}
      />
      <div className="flex items-center justify-between">
        <button
          className="text-sm text-primary underline"
          onClick={() => setMode(mode === 'code' ? 'key' : 'code')}
        >
          {mode === 'code' ? 'Tengo una API key' : 'Usar un código'}
        </button>
        <button
          className="bg-primary text-primary-foreground rounded-md px-4 py-1.5 disabled:opacity-50"
          disabled={busy}
          onClick={submit}
        >
          {mode === 'code' ? 'Emparejar' : 'Guardar'}
        </button>
      </div>
      {message && <p className="text-red-700 text-sm mt-2">{message}</p>}
    </section>
  );
}
```

- [ ] **Step 11: `src/components/PrintersSection.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { getPrinters, testPrint } from '../lib/api';
import type { DiscoveredPrinter } from '../lib/types';

export default function PrintersSection() {
  const [printers, setPrinters] = useState<DiscoveredPrinter[]>([]);
  const [selected, setSelected] = useState('');
  const [message, setMessage] = useState<string | null>(null);

  async function refresh() {
    const list = await getPrinters();
    setPrinters(list);
    if (!selected && list.length > 0) {
      setSelected(list[0].name);
    }
  }

  useEffect(() => {
    refresh();
    const id = setInterval(refresh, 5000);
    return () => clearInterval(id);
  }, []);

  async function onTestPrint() {
    if (!selected) {
      setMessage('No hay ninguna cola seleccionada.');
      return;
    }
    try {
      const res = await testPrint(selected);
      setMessage(res.message);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Error desconocido');
    }
  }

  return (
    <section className="rounded-lg border border-border p-4">
      <h2 className="font-semibold text-lg mb-3">Impresora (local, sólo prueba)</h2>
      <div className="flex gap-2 mb-2">
        <select
          className="border border-border rounded-md px-2 py-1 flex-1"
          value={selected}
          onChange={(e) => setSelected(e.target.value)}
        >
          {printers.map((p) => (
            <option key={p.name} value={p.name}>{p.name}</option>
          ))}
        </select>
        <button className="border border-border rounded-md px-3 py-1" onClick={refresh}>
          Actualizar
        </button>
      </div>
      <button
        className="bg-primary text-primary-foreground rounded-md px-4 py-1.5"
        onClick={onTestPrint}
      >
        Imprimir página de prueba
      </button>
      {message && <p className="text-sm mt-2">{message}</p>}
    </section>
  );
}
```

- [ ] **Step 12: `src/components/JobsTable.tsx`**

```tsx
import type { JobRecord } from '../lib/types';

function formatTime(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('es-NI', { hour12: false });
}

function dash(s: string | null): string {
  return s && s.trim() !== '' ? s : '—';
}

export default function JobsTable({ jobs }: { jobs: JobRecord[] }) {
  return (
    <section className="rounded-lg border border-border p-4">
      <h2 className="font-semibold text-lg mb-3">Actividad</h2>
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-muted-foreground">
            <th className="pb-1">Hora</th>
            <th className="pb-1">Rol</th>
            <th className="pb-1">Cola</th>
            <th className="pb-1">Estado</th>
            <th className="pb-1">Error</th>
          </tr>
        </thead>
        <tbody>
          {jobs.length === 0 && (
            <tr><td colSpan={5} className="text-muted-foreground py-2">Sin trabajos recientes</td></tr>
          )}
          {jobs.map((j, i) => (
            <tr key={i} className="border-t border-border">
              <td className="py-1">{formatTime(j.at)}</td>
              <td className="py-1">{dash(j.role)}</td>
              <td className="py-1">{dash(j.queue)}</td>
              <td className="py-1">{dash(j.result)}</td>
              <td className="py-1">{dash(j.error)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
```

- [ ] **Step 13: `src/components/FooterActions.tsx`**

```tsx
import { invoke } from '@tauri-apps/api/core';
import { getDiagnostics, getLogsDir } from '../lib/api';

export default function FooterActions() {
  async function openLogs() {
    const dir = await getLogsDir();
    await invoke('open_folder', { path: dir });
  }

  async function copyDiagnostics() {
    const text = await getDiagnostics();
    await navigator.clipboard.writeText(text);
  }

  return (
    <div className="flex gap-2">
      <button className="border border-border rounded-md px-3 py-1.5 text-sm" onClick={openLogs}>
        Abrir carpeta de logs
      </button>
      <button className="border border-border rounded-md px-3 py-1.5 text-sm" onClick={copyDiagnostics}>
        Copiar diagnóstico
      </button>
    </div>
  );
}
```

- [ ] **Step 14: `src/components/Dashboard.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { getStatus } from '../lib/api';
import type { Status } from '../lib/types';
import StatusSection from './StatusSection';
import PairingSection from './PairingSection';
import PrintersSection from './PrintersSection';
import JobsTable from './JobsTable';
import FooterActions from './FooterActions';

export default function Dashboard() {
  const [status, setStatus] = useState<Status | null>(null);

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

  const needsPairing = status?.phase === 'UNPAIRED';

  return (
    <main className="p-4 flex flex-col gap-4 max-w-2xl mx-auto">
      <header className="flex items-center gap-2">
        <h1 className="text-xl font-bold">Ember Agent</h1>
      </header>
      <StatusSection status={status} />
      {needsPairing && <PairingSection onPaired={refresh} />}
      <PrintersSection />
      <JobsTable jobs={status?.recentJobs ?? []} />
      <FooterActions />
    </main>
  );
}
```

- [ ] **Step 15: `vitest.config.ts`**

```ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom',
    globals: true
  }
});
```

- [ ] **Step 16: Write the failing component tests**

`src/components/PairingSection.test.tsx`:

```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import PairingSection from './PairingSection';
import * as api from '../lib/api';

describe('PairingSection', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('shows the server error message when pairing fails', async () => {
    vi.spyOn(api, 'pairWithCode').mockRejectedValue(new Error('Código inválido, usado o vencido.'));
    const onPaired = vi.fn();
    render(<PairingSection onPaired={onPaired} />);

    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(screen.getByText('Código inválido, usado o vencido.')).toBeTruthy());
    expect(onPaired).not.toHaveBeenCalled();
  });

  it('calls onPaired after a successful pairing', async () => {
    vi.spyOn(api, 'pairWithCode').mockResolvedValue({
      phase: 'CONNECTING', detail: null, lastSeen: null, agentId: null, printerCount: 0, recentJobs: []
    });
    const onPaired = vi.fn();
    render(<PairingSection onPaired={onPaired} />);

    fireEvent.change(screen.getByPlaceholderText('Código de 10 caracteres'), { target: { value: 'ABCDEFGHIJ' } });
    fireEvent.click(screen.getByText('Emparejar'));

    await waitFor(() => expect(onPaired).toHaveBeenCalledTimes(1));
  });
});
```

`src/components/JobsTable.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import JobsTable from './JobsTable';

describe('JobsTable', () => {
  it('renders a placeholder row when there are no jobs', () => {
    render(<JobsTable jobs={[]} />);
    expect(screen.getByText('Sin trabajos recientes')).toBeTruthy();
  });

  it('renders one row per job with dashes for missing fields', () => {
    render(<JobsTable jobs={[
      { at: '2026-09-12T10:00:00Z', role: 'Cocina', queue: 'COCINA-1', result: 'OK', error: null }
    ]} />);
    expect(screen.getByText('Cocina')).toBeTruthy();
    expect(screen.getByText('COCINA-1')).toBeTruthy();
    expect(screen.getByText('—')).toBeTruthy();
  });
});
```

- [ ] **Step 17: Install dependencies and run the tests to verify they fail, then pass**

Run: `cd printing-agent/ui && npm install`
Run: `npm run test`
Expected: with steps 1–15 already written, this should PASS 4/4 on the first run (the component files exist before the test files run in this task ordering) — if instead you commit test-first strictly, expect the two test files to fail on missing component imports before Step 9–14 are written, then pass after.

- [ ] **Step 18: Build the static site**

Run: `npm run build`
Expected: `printing-agent/ui/dist/index.html` + assets produced, no errors.

- [ ] **Step 19: Commit**

```bash
git add printing-agent/ui
git commit -m "feat(print-agent): add Astro+React+Tailwind window UI for the Tauri shell"
```

---

## Task 4: Tauri shell — sidecar process, tray, window, port discovery, crash handling

**Files:**
- Create: `printing-agent/src-tauri/Cargo.toml`
- Create: `printing-agent/src-tauri/build.rs`
- Create: `printing-agent/src-tauri/tauri.conf.json`
- Create: `printing-agent/src-tauri/src/main.rs`
- Create: `printing-agent/src-tauri/icons/` (generated, see Step 6)
- Create: `printing-agent/ui/src/lib/agent-events.ts`
- Modify: `printing-agent/ui/src/components/Dashboard.tsx`

**Interfaces:**
- Consumes: the app-image directory produced by `build-installer.ps1 -Stage appimage` (Task 5 wires this into the build order) at `printing-agent/dist/app-image/Ember Agent/`, containing `Ember Agent.exe` (jpackage native launcher, now argument-free per Task 2) — bundled by Tauri as a `resources` entry.
- Produces: Tauri command `get_port(): number`, Tauri command `open_folder(path: string): void`, Tauri events `agent-ready` (payload: port number) and `agent-crashed` (no payload) — consumed by `printing-agent/ui`'s `src/lib/api.ts` (Task 3) and the new `agent-events.ts`.

Neither of us has prior Tauri/Rust experience (spec §7 risk, inherited from `docs/superpowers/specs/ember_hub.md` §2.3's original reason for excluding Tauri from Hub v1) — the exact API names below match Tauri 2.x's documented shape at plan-writing time; if `cargo build` reports a renamed/moved API, fix the call site to match the installed `tauri` crate version rather than reinterpreting the design.

- [ ] **Step 1: Install the Tauri CLI and scaffold the crate**

Run: `cargo install tauri-cli --version "^2" --locked` (one-time, on the machine that builds installers)

Create `printing-agent/src-tauri/Cargo.toml`:

```toml
[package]
name = "ember-agent-shell"
version = "0.1.0"
edition = "2021"

[build-dependencies]
tauri-build = { version = "2", features = [] }

[dependencies]
tauri = { version = "2", features = ["tray-icon"] }
tauri-plugin-autostart = "2"
serde = { version = "1", features = ["derive"] }
serde_json = "1"

[[bin]]
name = "ember-agent-shell"
path = "src/main.rs"
```

Create `printing-agent/src-tauri/build.rs`:

```rust
fn main() {
    tauri_build::build()
}
```

- [ ] **Step 2: `tauri.conf.json`**

```json
{
  "$schema": "https://schema.tauri.app/config/2",
  "productName": "Ember Agent",
  "version": "0.1.1",
  "identifier": "net.vanter.ember.agent",
  "build": {
    "beforeBuildCommand": "cd ../ui && npm install && npm run build",
    "frontendDist": "../ui/dist"
  },
  "app": {
    "windows": [
      {
        "label": "main",
        "title": "Ember Agent",
        "width": 640,
        "height": 560,
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
      "../dist/app-image/Ember Agent": "app-image/Ember Agent"
    },
    "windows": {
      "nsis": {
        "installMode": "perMachine"
      }
    }
  }
}
```

- [ ] **Step 3: `src/main.rs` — sidecar spawn, port discovery, crash watcher, tray, window-close-to-tray**

```rust
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::io::{BufRead, BufReader};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, WindowEvent};
use tauri_plugin_autostart::MacosLauncher;

struct PortState(Arc<Mutex<u16>>);
struct AgentProcessState(Arc<Mutex<Option<Child>>>);

#[tauri::command]
fn get_port(state: tauri::State<PortState>) -> u16 {
    *state.0.lock().unwrap()
}

#[tauri::command]
fn open_folder(path: String) -> Result<(), String> {
    Command::new("explorer").arg(path).spawn().map(|_| ()).map_err(|e| e.to_string())
}

fn spawn_agent(app: &AppHandle, port_state: Arc<Mutex<u16>>, agent_process: Arc<Mutex<Option<Child>>>) {
    let resource_dir = app.path().resource_dir().expect("no resource dir");
    let exe = resource_dir.join("app-image").join("Ember Agent").join("Ember Agent.exe");

    let mut child = Command::new(exe)
        .stdout(Stdio::piped())
        .spawn()
        .expect("failed to spawn Ember Agent sidecar");

    let stdout = child.stdout.take().expect("no stdout from sidecar");
    *agent_process.lock().unwrap() = Some(child);

    // Reads the "PORT=<n>" line Main.java prints once LocalControlServer is listening.
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

    // Polls the same Child for an unexpected exit (crash) so the UI can show an error + retry
    // instead of silently going stale, per spec §4's "sidecar muere en caliente".
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
        .manage(PortState(port_state.clone()))
        .manage(AgentProcessState(agent_process.clone()))
        .invoke_handler(tauri::generate_handler![get_port, open_folder, restart_agent])
        .setup(move |app| {
            spawn_agent(app.handle(), port_state_setup.clone(), agent_process_setup.clone());

            let autostart = app.autolaunch();
            let _ = autostart.enable();

            let show = MenuItem::with_id(app, "show", "Mostrar Ember Agent", true, None::<&str>)?;
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
            // Mirrors AgentDashboard's old windowClosing handler: minimize to tray, never exit.
            if let WindowEvent::CloseRequested { api, .. } = event {
                let _ = window.hide();
                api.prevent_close();
            }
        })
        .build(tauri::generate_context!())
        .expect("error while building the Ember Agent shell")
        .run(move |_app_handle, event| {
            // Kill the sidecar on real app exit so a force-killed/updated shell never leaves an
            // orphaned Java process behind (spec §7 "cierre de proceso huérfano").
            if let tauri::RunEvent::Exit = event {
                if let Some(mut child) = agent_process_exit.lock().unwrap().take() {
                    let _ = child.kill();
                }
            }
        });
}
```

- [ ] **Step 4: `printing-agent/ui/src/lib/agent-events.ts` — listen for the events Rust emits**

```ts
import { listen } from '@tauri-apps/api/event';

export type AgentShellState = 'starting' | 'ready' | 'crashed' | 'timeout';

export function watchAgentShell(
  onChange: (state: AgentShellState) => void,
  timeoutMs = 5000
): () => void {
  let settled = false;
  const timeout = setTimeout(() => {
    if (!settled) {
      settled = true;
      onChange('timeout');
    }
  }, timeoutMs);

  const unlistenReady = listen('agent-ready', () => {
    settled = true;
    clearTimeout(timeout);
    onChange('ready');
  });
  const unlistenCrashed = listen('agent-crashed', () => {
    onChange('crashed');
  });

  return () => {
    clearTimeout(timeout);
    unlistenReady.then((f) => f());
    unlistenCrashed.then((f) => f());
  };
}
```

- [ ] **Step 5: Wire the shell state into `Dashboard.tsx`**

Replace the top of `printing-agent/ui/src/components/Dashboard.tsx` (keep the rest of the component body from Task 3 unchanged below the new state/effect):

```tsx
import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { getStatus } from '../lib/api';
import type { Status } from '../lib/types';
import { watchAgentShell, type AgentShellState } from '../lib/agent-events';
import StatusSection from './StatusSection';
import PairingSection from './PairingSection';
import PrintersSection from './PrintersSection';
import JobsTable from './JobsTable';
import FooterActions from './FooterActions';

export default function Dashboard() {
  const [shellState, setShellState] = useState<AgentShellState>('starting');
  const [status, setStatus] = useState<Status | null>(null);

  useEffect(() => watchAgentShell(setShellState), []);

  async function refresh() {
    try {
      setStatus(await getStatus());
    } catch {
      // transient poll failure — retried on the next tick, not fatal (spec §4)
    }
  }

  useEffect(() => {
    if (shellState !== 'ready') return;
    refresh();
    const id = setInterval(refresh, 1500);
    return () => clearInterval(id);
  }, [shellState]);

  if (shellState === 'starting') {
    return <main className="p-4 text-muted-foreground">Iniciando Ember Agent…</main>;
  }
  if (shellState === 'timeout' || shellState === 'crashed') {
    return (
      <main className="p-4 flex flex-col gap-3">
        <p className="text-red-700">El agente no pudo iniciar.</p>
        <button
          className="bg-primary text-primary-foreground rounded-md px-4 py-1.5 w-fit"
          onClick={() => invoke('restart_agent')}
        >
          Reintentar
        </button>
      </main>
    );
  }

  const needsPairing = status?.phase === 'UNPAIRED';

  return (
    <main className="p-4 flex flex-col gap-4 max-w-2xl mx-auto">
      <header className="flex items-center gap-2">
        <h1 className="text-xl font-bold">Ember Agent</h1>
      </header>
      <StatusSection status={status} />
      {needsPairing && <PairingSection onPaired={refresh} />}
      <PrintersSection />
      <JobsTable jobs={status?.recentJobs ?? []} />
      <FooterActions />
    </main>
  );
}
```

- [ ] **Step 6: Generate the Tauri icon set from the existing brand icon**

Run: `cd printing-agent/src-tauri && cargo tauri icon ../installer/ember-agent.ico`
Expected: populates `printing-agent/src-tauri/icons/` (multiple sizes + `icon.ico`) referenced by `tauri.conf.json`'s `bundle.icon`. Commit the generated folder — it is not regenerated on every build, only when the source icon changes.

- [ ] **Step 7: Build and manually smoke-test**

Run: `cd printing-agent/src-tauri && cargo tauri build --debug` (debug build is faster for this first smoke test; the release/installer build is Task 5)
Expected: compiles; running the produced debug exe shows "Iniciando Ember Agent…" briefly, then the dashboard with "Sin emparejar", matching what `HubDashboard`'s Swing equivalent showed pre-boot. Confirm in Task Manager that a `java.exe` (or `Ember Agent.exe`) child process is running alongside the Tauri exe, and that closing the window (X button) hides it to tray instead of quitting.

- [ ] **Step 8: Commit**

```bash
git add printing-agent/src-tauri printing-agent/ui/src/lib/agent-events.ts printing-agent/ui/src/components/Dashboard.tsx
git commit -m "feat(print-agent): add Tauri shell (sidecar spawn, tray, crash recovery)"
```

---

## Task 5: Rewrite the installer pipeline — Tauri bundler replaces Inno Setup

**Files:**
- Modify: `printing-agent/build-installer.ps1`
- Delete: `printing-agent/installer/EmberAgent.iss`
- Delete: `printing-agent/installer/Iniciar Ember Agent.cmd`
- Modify: `.github/workflows/lint.yml` (`build-print-agent` job)

**Interfaces:**
- Consumes: `Build-AppImage` (unchanged function in this same script — still produces `printing-agent/dist/app-image/Ember Agent/`, which Task 4's `tauri.conf.json` `bundle.resources` references by that exact relative path).
- Produces: `printing-agent/dist/EmberAgentSetup-<version>.exe` — same output path/naming `deploy/publish-installer.sh agent <version> <path>` already expects, so that script needs no changes.

- [ ] **Step 1: Remove the now-unused `Iniciar Ember Agent.cmd` copy step and the Inno Setup stage from `build-installer.ps1`**

Replace the whole `Build-Installer` function and the header comment in `printing-agent/build-installer.ps1`:

```powershell
<#
Builds the Ember Agent Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> printing-agent/dist/runtime               (jlink JRE image)
  appimage  -> printing-agent/dist/app-image             (jpackage + shaded jar, headless sidecar)
  installer -> printing-agent/dist/EmberAgentSetup-*.exe  (Tauri bundler, NSIS)
Requires: JDK 17 on PATH (java, jlink, jpackage), mvn, Node (for printing-agent/ui), Rust +
`cargo install tauri-cli --version "^2"` for the last stage.
The agent installer bakes in no secrets/URLs -- the backend URL comes from POST /printing/agents/pair.
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$agentDir   = $PSScriptRoot
$distDir    = Join-Path $agentDir "dist"
$runtimeDir = Join-Path $distDir "runtime"

$appImageParent = Join-Path $distDir "app-image"
$appImageDir    = Join-Path $appImageParent "Ember Agent"
$installerDir   = Join-Path $agentDir "installer"
$tauriDir       = Join-Path $agentDir "src-tauri"

function Get-AgentVersion {
    $pom = Get-Content (Join-Path $agentDir "pom.xml") -Raw
    if ($pom -notmatch "<artifactId>printing-agent</artifactId>\s*<version>([^<]+)</version>") {
        throw "could not read <version> from printing-agent/pom.xml"
    }
    return $Matches[1] -replace "-SNAPSHOT",""
}

function Build-Runtime {
    Write-Host "== jlink runtime ==" -ForegroundColor Cyan
    $modules = (Get-Content (Join-Path $agentDir "jlink-modules.txt") |
                Where-Object { $_ -and -not $_.StartsWith("#") }) -join ","
    if (Test-Path $runtimeDir) { Remove-Item -Recurse -Force $runtimeDir }
    & jlink `
        --add-modules $modules `
        --strip-debug --no-header-files --no-man-pages `
        --compress=2 `
        --include-locales=en,es `
        --output $runtimeDir
    if ($LASTEXITCODE -ne 0) { throw "jlink failed ($LASTEXITCODE)" }
    & (Join-Path $runtimeDir "bin\java.exe") --version
    if ($LASTEXITCODE -ne 0) { throw "runtime java.exe is not runnable" }
}

function Build-AppImage {
    Write-Host "== app-image ==" -ForegroundColor Cyan
    if (-not (Test-Path $runtimeDir)) { Build-Runtime }

    Write-Host "-- mvn package --"
    & mvn -f (Join-Path $agentDir "pom.xml") -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }

    $jar = Get-ChildItem (Join-Path $agentDir "target") -Filter "printing-agent-*.jar" |
           Where-Object { $_.Name -notmatch "original|sources|javadoc" } |
           Select-Object -First 1
    if (-not $jar) { throw "no shaded printing-agent-*.jar in printing-agent/target" }

    $inputDir = Join-Path $distDir "jpackage-input"
    if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
    Copy-Item $jar.FullName (Join-Path $inputDir "printing-agent.jar")

    if (Test-Path $appImageParent) { Remove-Item -Recurse -Force $appImageParent }
    & jpackage `
        --type app-image `
        --name "Ember Agent" `
        --app-version (Get-AgentVersion) `
        --vendor "Vanter" `
        --input $inputDir `
        --main-jar "printing-agent.jar" `
        --main-class "com.vanter.emberagent.Main" `
        --runtime-image $runtimeDir `
        --icon (Join-Path $installerDir "ember-agent.ico") `
        --java-options "-Dfile.encoding=UTF-8" `
        --dest $appImageParent
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed ($LASTEXITCODE)" }

    if (-not (Test-Path (Join-Path $appImageDir "Ember Agent.exe"))) { throw "app-image launcher missing" }
    Write-Host "app-image at $appImageDir" -ForegroundColor Green
}

function Build-Installer {
    Write-Host "== installer (Tauri) ==" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $appImageDir "Ember Agent.exe"))) { Build-AppImage }

    $cargoTauri = (Get-Command cargo-tauri.exe -ErrorAction SilentlyContinue) -or
                  (Get-Command cargo -ErrorAction SilentlyContinue)
    if (-not $cargoTauri) { throw "Rust/cargo not found - install Rust and `cargo install tauri-cli --version '^2'`." }

    Push-Location $tauriDir
    try {
        & cargo tauri build
        if ($LASTEXITCODE -ne 0) { throw "cargo tauri build failed ($LASTEXITCODE)" }
    } finally { Pop-Location }

    $bundleDir = Join-Path $tauriDir "target\release\bundle\nsis"
    $produced = Get-ChildItem $bundleDir -Filter "*-setup.exe" | Select-Object -First 1
    if (-not $produced) { throw "no NSIS installer produced under $bundleDir" }

    $version = Get-AgentVersion
    $out = Join-Path $distDir "EmberAgentSetup-$version.exe"
    Copy-Item $produced.FullName $out -Force
    Write-Host "installer: $out" -ForegroundColor Green
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
if ($Stage -in @("all","installer")) { Build-Installer }
Write-Host "Done ($Stage)." -ForegroundColor Green
```

- [ ] **Step 2: Remove the retired installer files**

```bash
git rm printing-agent/installer/EmberAgent.iss
git rm "printing-agent/installer/Iniciar Ember Agent.cmd"
```

`printing-agent/installer/ember-agent.ico` is **kept** — it's the source Task 4 Step 6 fed to `cargo tauri icon`.

- [ ] **Step 3: Update the `build-print-agent` CI job**

Replace the job body in `.github/workflows/lint.yml`:

```yaml
  build-print-agent:
    name: build-print-agent
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - name: Configurar Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven
      - name: Empaquetar el fat jar del agente
        run: mvn -f printing-agent/pom.xml -q -B -DskipTests package
      - name: jlink + jpackage app-image
        run: pwsh printing-agent/build-installer.ps1 -Stage appimage
      - name: Verificar que el lanzador existe
        run: if (-not (Test-Path "printing-agent/dist/app-image/Ember Agent/Ember Agent.exe")) { exit 1 }
      - name: Configurar Node
        uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Instalar dependencias y compilar la UI del agente
        run: |
          cd printing-agent/ui
          npm install
          npm run test
          npm run build
      - name: Configurar Rust
        uses: dtolnay/rust-toolchain@stable
      - name: Instalar tauri-cli
        run: cargo install tauri-cli --version "^2" --locked
      - name: cargo tauri build (release + instalador NSIS)
        run: |
          cd printing-agent/src-tauri
          cargo tauri build
      - name: Verificar que el instalador existe
        run: if (-not (Get-ChildItem "printing-agent/src-tauri/target/release/bundle/nsis" -Filter "*-setup.exe")) { exit 1 }
```

- [ ] **Step 4: Run the full local build to confirm the pipeline end to end**

Run: `pwsh printing-agent/build-installer.ps1 -Stage all`
Expected: `printing-agent/dist/EmberAgentSetup-<version>.exe` produced, no errors at any stage.

- [ ] **Step 5: Commit**

```bash
git add printing-agent/build-installer.ps1 .github/workflows/lint.yml
git rm printing-agent/installer/EmberAgent.iss
git rm "printing-agent/installer/Iniciar Ember Agent.cmd"
git commit -m "build(print-agent): replace Inno Setup with the Tauri bundler for the installer"
```

---

## Task 6: Update `VERIFY.md` and run the manual clean-machine checklist

**Files:**
- Modify: `printing-agent/VERIFY.md`

**Interfaces:**
- Consumes: the finished installer from Task 5 (`EmberAgentSetup-<version>.exe`).
- Produces: nothing consumed by later tasks — this is the last task of the plan.

- [ ] **Step 1: Update the checklist wording and add shell-specific checks**

Replace `printing-agent/VERIFY.md`'s body (keep the header table as-is) with:

```markdown
## Checklist

- [ ] **1. Clean install, no Java.**
  `where java` before install finds nothing. Run `EmberAgentSetup-x.y.z.exe`, accept the UAC
  prompt, finish the wizard. Result: `%ProgramFiles%\Ember Agent\Ember Agent.exe` (Tauri shell)
  launches; window shows "Iniciando Ember Agent…" then **"Sin emparejar"**; tray icon present.
  Task Manager shows a `java.exe` (or `Ember Agent.exe` from the bundled app-image) child process
  under the Tauri process. `where java` still finds nothing (runtime is embedded inside the
  bundled resources). Startup entry exists (Tauri autostart) with no extra CLI flags.

- [ ] **2. Pair by code.**
  In the admin (**Configuración → Impresión**) create an agent (e.g. `Caja 1`) → copy the
  pairing code (valid ~15 min). In the window paste it → **Emparejar**. "Sin emparejar" →
  "Conectando…" → "Conectado". `%ProgramData%\EmberAgent\credential.bin` exists and opens as
  binary, **not** readable text (DPAPI machine-scope, unchanged). No `credential.json` was written.

- [ ] **3. Printer dropdown.**
  In the admin's **Agregar impresora**, the field lists the PC's real queues; matches
  `Get-Printer` output exactly (unchanged `WindowsPrinterEnumerator`).

- [ ] **4. Test page.**
  Window → select a reported queue → **Imprimir página de prueba** → paper comes out of that
  printer, success message shown. The "Actividad" table does **not** gain a row for it (matches
  today's behavior — manual test prints aren't recorded jobs).

- [ ] **5. Real job.**
  Trigger a kitchen ticket and a bill receipt from the app → both print. "Actividad" shows each
  job as `OK` with the right role/queue, refreshed within ~1.5s of completion (polling).

- [ ] **6. Auto-start on log-on.**
  Reboot, log in, do nothing else. Within ~30s the window/tray reaches "Conectado" again —
  **without** any re-pair prompt.

- [ ] **7. Credential survives an update.**
  Install `x.y.(z+1)` over the existing install. No re-pair prompt; agent reconnects.
  `credential.bin` is byte-identical before/after (check timestamp/hash).

- [ ] **8. Uninstall — keep data.**
  Uninstall via *Agregar o quitar programas*, keep data when prompted (or the NSIS-equivalent
  prompt if the wording changed — note the exact text seen). `%ProgramData%\EmberAgent\` remains.
  Reinstall → connects with **no** pairing step.

- [ ] **9. Uninstall — remove data.**
  Uninstall again, choose to remove data. `%ProgramData%\EmberAgent\` is gone entirely.

- [ ] **10. No inbound firewall prompt.**
  At no point does Windows show a firewall "Allow access?" dialog — `LocalControlServer` only
  binds `127.0.0.1`, which Windows Firewall never filters regardless of inbound rules.

- [ ] **11. Sidecar crash recovery (new).**
  While "Conectado", kill the `java.exe`/`Ember Agent.exe` child process directly in Task Manager
  (**not** the Tauri parent). Within ~2s the window shows "El agente no pudo iniciar." +
  **Reintentar**. Click it → the sidecar respawns, window reaches "Conectado" again without a
  reinstall or a Tauri restart.

- [ ] **12. Close-to-tray, no orphaned process.**
  Click the window's close (X) button → window hides, tray icon remains, `java.exe` child process
  **still running** (mirrors old Swing "minimize to tray, never exit"). Then quit via the tray's
  **Salir** → both the Tauri process and the `java.exe` child process disappear from Task
  Manager — no orphan left behind.

- [ ] **13. Force-kill the shell, no orphan.**
  With the agent "Conectado", kill the Tauri parent process itself (not via **Salir**) in Task
  Manager. Confirm the `java.exe` child either exits with it or is cleanly killable afterward —
  record which happened; if the child survives the parent's forced kill, file it as a follow-up
  (the spec §7 orphan-process risk was flagged as accepted-but-unverified, not fully closed by
  Task 4's normal-exit kill path).
```

- [ ] **Step 2: Run the manual checklist on a clean Windows machine/VM**

This is an ops action, not something to automate — record pass/fail per item in the table at the top of `VERIFY.md` as today, and file any failing item as its own follow-up task per the file's existing "Bugs found here become their own follow-up tasks" rule.

- [ ] **Step 3: Commit**

```bash
git add printing-agent/VERIFY.md
git commit -m "docs(print-agent): update VERIFY.md for the Tauri shell (sidecar crash/orphan checks)"
```

---

## Self-Review Notes (already applied above)

- **Spec coverage:** §2.2 (Tauri-as-parent, Java sidecar) → Task 4. §2.3 (loopback HTTP, `PORT=` stdout line) → Tasks 1–2/4. §2.4 (Astro+React+Tailwind, separate project, shared tokens) → Task 3. §2.5 (fase 1 scope, corrected) → Tasks 1/3 endpoint+screen list. §3 (API contract) → Task 1. §4 (error handling: sidecar fails to start / crashes / poll failure / pairing failure) → Tasks 1 (poll-failure-tolerant fetch), 4 (timeout/crash/retry), 3 (pairing error display). §5 (packaging: jlink/jpackage unchanged, Tauri bundler replaces Inno Setup, autostart plugin replaces `{commonstartup}`, CI update) → Task 5. §6 (testing: Java unit tests, Astro/React component tests, manual VERIFY) → Tasks 1/3/6. §7 risks (Rust unfamiliarity, `astro build` under `tauri://`, orphaned process) → called out inline in Tasks 4/6 (Step 13 turns the orphan risk into an explicit checked item rather than leaving it silently assumed-fixed).
- **Placeholder scan:** no TBD/TODO; every step has literal code or an exact command.
- **Type consistency:** `LocalControlServer(StatusHub, CredentialStore, AgentRunner)` used identically in Task 1's test, Task 2's `Main`, and referenced (not re-instantiated) nowhere else. `Status`/`DiscoveredPrinter`/`JobRecord` TS types in Task 3 match the JSON field names `StatusDto`/`JobDto` in Task 1 produce (`phase`, `detail`, `lastSeen`, `agentId`, `printerCount`, `recentJobs`, `at`/`role`/`queue`/`result`/`error`, `name`/`driverName`/`portName`/`inkjetGuess`). `get_port`/`open_folder`/`restart_agent` Tauri command names match between Task 4's `main.rs` `invoke_handler!` list and Task 3/4's `invoke(...)` call sites.
- **Scope check:** this plan is printer-agent v2 only. Ember Hub v2 (same architectural pattern, different sidecar surface — Postgres/MinIO/license bootstrap instead of pairing/printing) is intentionally a separate plan, written after this one ships and is verified (spec §8).
