package com.vanter.ember.hub.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeartbeatSchedulerTest {

    @TempDir Path tempDir;

    private HubStateStore stateStore;
    private LicenseService licenseService;
    private HttpServer server;
    private UUID restaurantId;
    private Path licenseFile;
    private Path stateFile;

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();
        licenseFile = tempDir.resolve("license.key");
        Files.writeString(licenseFile, "signed.license");
        stateFile = tempDir.resolve("state.json");
        stateStore = new HubStateStore(stateFile);

        // A real LicenseService so recordHeartbeatSuccess / recordSuspended write through the
        // real HubStateStore. Its license-file / public-key args are unused by those two methods.
        licenseService = new LicenseService(
                licenseFile, null, null, mock(com.vanter.ember.hub.license.HardwareFingerprintService.class),
                stateStore, Duration.ofHours(48));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private HubProperties propsPointingAt(String url) {
        return new HubProperties(
                tempDir, tempDir, licenseFile, tempDir.resolve("pub.der"), stateFile,
                5432, 8080, "", tempDir, tempDir, 9000, url, 48);
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hub-heartbeat", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/hub-heartbeat";
    }

    @Test
    void ok_bumpsLastHeartbeatAndClearsSuspended() throws Exception {
        Instant oldHeartbeat = Instant.now().minus(2, ChronoUnit.DAYS);
        stateStore.save(new HubState("fp", restaurantId, oldHeartbeat,
                Instant.now().minus(1, ChronoUnit.HOURS)));
        startServer(200, "{\"status\":\"OK\",\"serverTime\":\"2026-08-28T00:00:00Z\",\"latestVersion\":null}");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isAfter(oldHeartbeat);
        assertThat(after.suspendedSince()).isNull();
    }

    @Test
    void suspended_stampsSuspendedSinceAndLeavesHeartbeat() throws Exception {
        Instant oldHeartbeat = Instant.now().minus(2, ChronoUnit.DAYS);
        stateStore.save(new HubState("fp", restaurantId, oldHeartbeat, null));
        startServer(200, "{\"status\":\"SUSPENDED\",\"serverTime\":\"2026-08-28T00:00:00Z\"}");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isEqualTo(oldHeartbeat);
        assertThat(after.suspendedSince()).isNotNull();
    }

    @Test
    void serverError_leavesStateUntouched() throws Exception {
        HubState before = new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        stateStore.save(before);
        startServer(500, "boom");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isEqualTo(before.lastHeartbeatAt());
        assertThat(after.suspendedSince()).isNull();
    }

    @Test
    void blankUrl_isNoOp() {
        HubState before = new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        stateStore.save(before);

        new HeartbeatScheduler(propsPointingAt(""), stateStore, licenseService).runHeartbeat();

        assertThat(stateStore.load().orElseThrow().lastHeartbeatAt()).isEqualTo(before.lastHeartbeatAt());
    }
}
