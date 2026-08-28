package com.vanter.ember.hub.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic Hub -> cloud license heartbeat (spec 2026-08-28-hub-license-heartbeat-design.md).
 * On {@code OK} it resets the 4-day offline grace clock; on {@code SUSPENDED} it starts the
 * 48-hour courtesy-grace clock; on any failure it does nothing and lets the next cycle retry.
 * Every exception is swallowed inside {@link #runHeartbeat()} — an exception escaping a
 * {@code @Scheduled} method makes Spring stop scheduling it.
 */
@Component
@Profile("hub")
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HubProperties properties;
    private final HubStateStore stateStore;
    private final LicenseService licenseService;

    public HeartbeatScheduler(
            HubProperties properties, HubStateStore stateStore, LicenseService licenseService) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.licenseService = licenseService;
    }

    @Scheduled(fixedDelayString = "${ember.hub.heartbeat-interval-ms:300000}")
    public void runHeartbeat() {
        try {
            if (properties.heartbeatUrl().isBlank()) {
                log.debug("EMBER_HUB_HEARTBEAT_URL not set; skipping heartbeat.");
                return;
            }
            HubState state = stateStore.load().orElse(null);
            if (state == null) {
                log.warn("hub-state.json missing; cannot send heartbeat.");
                return;
            }

            String licenseKey = Files.readString(properties.licenseFile());
            String requestBody = MAPPER.writeValueAsString(
                    new HeartbeatRequestBody(licenseKey, state.hardwareFingerprint()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.heartbeatUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Heartbeat failed: HTTP {}", response.statusCode());
                return;
            }

            HeartbeatResponseBody body = MAPPER.readValue(response.body(), HeartbeatResponseBody.class);
            if ("OK".equals(body.status())) {
                licenseService.recordHeartbeatSuccess(state);
                log.debug("Heartbeat OK.");
            } else if ("SUSPENDED".equals(body.status())) {
                licenseService.recordSuspended(state);
                log.warn("Heartbeat reports the restaurant is SUSPENDED.");
            } else {
                log.warn("Heartbeat returned unrecognised status '{}'.", body.status());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Heartbeat cycle failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Heartbeat cycle failed unexpectedly", e);
        }
    }

    private record HeartbeatRequestBody(String licenseKey, String hardwareFingerprint) {}

    private record HeartbeatResponseBody(String status, String serverTime, String latestVersion) {}
}
