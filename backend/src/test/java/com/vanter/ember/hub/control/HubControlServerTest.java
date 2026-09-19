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

    @Test
    void license_delete_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = delete("/api/license");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.removeLicenseCalled);
    }

    @Test
    void license_delete_failure_returns400WithMessage() throws Exception {
        orchestrator.removeLicenseFailure = new IOException("no se pudo borrar");

        HttpResponse<String> res = delete("/api/license");

        assertEquals(400, res.statusCode());
        assertEquals("no se pudo borrar", mapper.readTree(res.body()).get("error").asText());
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

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static final class FakeOrchestrator implements HubOrchestrator {
        boolean startCalled;
        boolean stopCalled;
        boolean removeLicenseCalled;
        Path installedFrom;
        IOException installLicenseFailure;
        IOException removeLicenseFailure;
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
        public void stopAndWait() {
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
        public void removeLicense() throws IOException {
            if (removeLicenseFailure != null) {
                throw removeLicenseFailure;
            }
            removeLicenseCalled = true;
        }

        @Override
        public HubStatusSnapshot snapshot() {
            return snapshot;
        }
    }
}
