package com.vanter.emberagent.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
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

    // The Tauri window's origin is never http://127.0.0.1:<port>, so every fetch() the React UI
    // makes is cross-origin; without this, the browser silently discards every response (and
    // blocks the POST endpoints' preflight outright), leaving the dashboard stuck on "Cargando...".
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
        httpServer.createContext("/api/status", this::handleStatus).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/pair", this::handlePair).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/printers", this::handlePrinters).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/test-print", this::handleTestPrint).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/diagnostics", this::handleDiagnostics).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/paths", this::handlePaths).getFilters().add(CORS_FILTER);
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
