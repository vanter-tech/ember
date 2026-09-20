package com.vanter.emberagent.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.AgentRunner;
import com.vanter.emberagent.HubDiscovery;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
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
    void pair_targetCloud_withApiKey_usesTheBuiltInCloudAddress_andNeverEchoesIt() throws Exception {
        HttpResponse<String> res = post("/api/pair", "{\"apiKey\":\"key-1\",\"target\":\"cloud\"}");

        assertEquals(200, res.statusCode());
        assertEquals(Optional.of(new AgentCredential("key-1", LocalControlServer.CLOUD_URL)), store.saved);
        assertTrue(!res.body().contains("http"), "the dashboard must never receive an address: " + res.body());
    }

    @Test
    void pair_targetLocal_withApiKey_usesTheFirstHubFound() throws Exception {
        restartWithHubs(List.of("http://192.168.1.10:8080"));

        HttpResponse<String> res = post("/api/pair", "{\"apiKey\":\"key-1\",\"target\":\"local\"}");

        assertEquals(200, res.statusCode());
        assertEquals(Optional.of(new AgentCredential("key-1", "http://192.168.1.10:8080")), store.saved);
    }

    @Test
    void pair_targetLocal_noHubOnTheNetwork_returns400WithAFriendlyMessage() throws Exception {
        restartWithHubs(List.of());

        HttpResponse<String> byCode = post("/api/pair", "{\"code\":\"ABCDE\",\"target\":\"local\"}");
        HttpResponse<String> byKey = post("/api/pair", "{\"apiKey\":\"key-1\",\"target\":\"local\"}");

        assertEquals(400, byCode.statusCode());
        assertEquals(400, byKey.statusCode());
        assertTrue(byCode.body().contains("No se encontró ningún servidor"), byCode.body());
        assertTrue(store.saved.isEmpty());
    }

    private void restartWithHubs(List<String> hubs) throws IOException {
        server.stop();
        HubDiscovery stub = new HubDiscovery() {
            @Override
            public List<String> discover() {
                return hubs;
            }
        };
        server = new LocalControlServer(hub, store, new AgentRunner(store, hub), stub);
        base = "http://127.0.0.1:" + server.start();
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

    @Test
    void status_includesCorsHeaderSoTheTauriWebviewCanReadIt() throws Exception {
        // The Tauri window's origin is never http://127.0.0.1:<port>, so every fetch() the React
        // UI makes is cross-origin; without this header the browser silently discards the
        // response and the dashboard is stuck on "Cargando..." forever (reproduced live against
        // the packaged installer — see report 444).
        HttpResponse<String> res = get("/api/status");

        assertEquals("*", res.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
    }

    @Test
    void pair_optionsPreflight_returns204WithCorsHeaders() throws Exception {
        // pairWithCode/pairWithApiKey send Content-Type: application/json, which isn't a
        // CORS-safelisted content type — the browser sends an OPTIONS preflight first and
        // requires a 2xx response carrying Allow-Methods/Allow-Headers before it will send the
        // real POST.
        HttpResponse<String> res = options("/api/pair");

        assertEquals(204, res.statusCode());
        assertEquals("*", res.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
        assertTrue(res.headers().firstValue("Access-Control-Allow-Methods").orElse("").contains("POST"));
        assertTrue(res.headers().firstValue("Access-Control-Allow-Headers").orElse("").contains("Content-Type"));
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

    private HttpResponse<String> options(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
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
