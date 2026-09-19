package com.vanter.ember.hub.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.vanter.ember.hub.backup.BackupConfig;
import com.vanter.ember.hub.backup.BackupException;
import com.vanter.ember.hub.backup.HubBackup;
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
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            chain.doFilter(exchange);
        }
    };

    private final HubOrchestrator orchestrator;
    private final HubBackup backup;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer httpServer;

    public HubControlServer(HubOrchestrator orchestrator, HubBackup backup) {
        this.orchestrator = orchestrator;
        this.backup = backup;
    }

    /** Starts listening on 127.0.0.1 at an OS-assigned port and returns that port. */
    public int start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/api/status", this::handleStatus).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/start", this::handleStart).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/stop", this::handleStop).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/license", this::handleLicense).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/status", this::handleBackupStatus).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/config", this::handleBackupConfig).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/now", this::handleBackupNow).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/list", this::handleBackupList).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/inspect", this::handleBackupInspect).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/restore", this::handleBackupRestore).getFilters().add(CORS_FILTER);
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
        String method = exchange.getRequestMethod();
        if ("DELETE".equals(method)) {
            try {
                orchestrator.removeLicense();
                sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
            } catch (IOException | RuntimeException e) {
                sendJson(exchange, 400, Map.of("error", e.getMessage()));
            }
            return;
        }
        if (!"POST".equals(method)) {
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

    // --- backup handlers -----------------------------------------------------------

    private void handleBackupStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, backup.status());
    }

    private void handleBackupConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            sendJson(exchange, 200, backup.getConfig());
            return;
        }
        if (!"POST".equals(method)) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        ConfigRequest req = mapper.readValue(exchange.getRequestBody(), ConfigRequest.class);
        try {
            int retention = req.retention() == null ? backup.getConfig().retention() : req.retention();
            sendJson(exchange, 200, backup.setConfig(new BackupConfig(req.destDir(), retention)));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private void handleBackupNow(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        NowRequest req = body.length == 0 ? new NowRequest(null) : mapper.readValue(body, NowRequest.class);
        sendJson(exchange, 200, backup.backupNow(req.destDir()));
    }

    private void handleBackupList(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, backup.listSnapshots());
    }

    private void handleBackupInspect(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        PathRequest req = mapper.readValue(exchange.getRequestBody(), PathRequest.class);
        if (req.path() == null || req.path().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "path es obligatorio."));
            return;
        }
        try {
            sendJson(exchange, 200, backup.inspect(req.path()));
        } catch (BackupException e) {
            sendBackupError(exchange, e);
        }
    }

    private void handleBackupRestore(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        RestoreRequest req = mapper.readValue(exchange.getRequestBody(), RestoreRequest.class);
        if (req.path() == null || req.path().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "path es obligatorio."));
            return;
        }
        try {
            backup.restore(req.path(), req.skipSafetySnapshot());
            sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
        } catch (BackupException e) {
            sendBackupError(exchange, e);
        }
    }

    private void sendBackupError(HttpExchange exchange, BackupException e) throws IOException {
        int status = switch (e.code()) {
            case BackupException.INCOMPATIBLE, BackupException.SAFETY_FAILED -> 409;
            case BackupException.INVALID -> 400;
            default -> 500;
        };
        sendJson(exchange, status, Map.of("error", e.getMessage(), "code", e.code()));
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

    private record ConfigRequest(String destDir, Integer retention) {}

    private record NowRequest(String destDir) {}

    private record PathRequest(String path) {}

    private record RestoreRequest(String path, boolean skipSafetySnapshot) {}

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
