package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscoveredPrintersClientTest {

    private MockWebServer server;

    @BeforeEach
    void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    private DiscoveredPrintersClient client() {
        return new DiscoveredPrintersClient(HttpClient.newHttpClient());
    }

    @Test
    void report_204_sendsPrintersWithBearerToken() throws InterruptedException {
        server.enqueue(new MockResponse.Builder().code(204).build());

        client().report(server.url("/").toString(), "jwt-abc", List.of(
                new DiscoveredPrinter("EPSON L3210 Series", "EPSON L3210 Series", "USB001", true)));

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertTrue(request.getTarget().endsWith("/printing/agents/me/discovered-printers"));
        assertEquals("Bearer jwt-abc", request.getHeaders().get("Authorization"));
        String body = request.getBody().utf8();
        assertTrue(body.contains("\"printers\""));
        assertTrue(body.contains("EPSON L3210 Series"));
        assertTrue(body.contains("\"inkjetGuess\":true"));
    }

    @Test
    void report_500_swallowsErrorBestEffort() {
        server.enqueue(new MockResponse.Builder().code(500).body("boom").build());

        assertDoesNotThrow(() -> client().report(
                server.url("/").toString(), "jwt-abc", List.of()));
    }
}
