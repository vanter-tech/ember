package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Optional;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketLogoClientTest {

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

    private String base() {
        String url = server.url("/").toString();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static MockResponse png(byte[] body, String etag) {
        return new MockResponse.Builder()
                .code(200)
                .body(new Buffer().write(body))
                .addHeader("Content-Type", "image/png")
                .addHeader("ETag", etag)
                .build();
    }

    @Test
    void fetch_returnsTheLogoAndSendsTheAgentJwt() throws Exception {
        server.enqueue(png(new byte[] {1, 2, 3}, "\"v1\""));

        Optional<byte[]> logo = new TicketLogoClient().fetch(base(), "jwt-1");

        assertTrue(logo.isPresent());
        assertArrayEquals(new byte[] {1, 2, 3}, logo.get());
        RecordedRequest request = server.takeRequest();
        assertEquals("/printing/agents/me/ticket-logo", request.getUrl().encodedPath());
        assertEquals("Bearer jwt-1", request.getHeaders().get("Authorization"));
    }

    @Test
    void fetch_secondCallSendsTheEtagAndReusesTheCachedCopyOn304() throws Exception {
        server.enqueue(png(new byte[] {7, 7}, "\"v1\""));
        server.enqueue(new MockResponse.Builder().code(304).build());
        TicketLogoClient client = new TicketLogoClient();
        client.fetch(base(), "jwt");
        server.takeRequest();

        Optional<byte[]> second = client.fetch(base(), "jwt");

        assertArrayEquals(new byte[] {7, 7}, second.orElseThrow());
        assertEquals("\"v1\"", server.takeRequest().getHeaders().get("If-None-Match"));
    }

    @Test
    void fetch_404MeansNoLogoAndForgetsTheStaleCopy() throws Exception {
        server.enqueue(png(new byte[] {5}, "\"v1\""));
        server.enqueue(new MockResponse.Builder().code(404).build());
        TicketLogoClient client = new TicketLogoClient();
        client.fetch(base(), "jwt");

        assertTrue(client.fetch(base(), "jwt").isEmpty());

        // once forgotten, a later failure must not resurrect the removed logo
        server.close();
        assertTrue(client.fetch(base(), "jwt").isEmpty());
    }

    @Test
    void fetch_unreachableBackend_withNothingCached_isEmptyNotAnError() throws Exception {
        String url = base();
        server.close();

        assertTrue(new TicketLogoClient().fetch(url, "jwt").isEmpty());
    }

    @Test
    void fetch_unreachableBackend_fallsBackToTheCachedCopy() throws Exception {
        server.enqueue(png(new byte[] {9}, "\"v1\""));
        TicketLogoClient client = new TicketLogoClient();
        String url = base();
        client.fetch(url, "jwt");
        server.close();

        assertArrayEquals(new byte[] {9}, client.fetch(url, "jwt").orElseThrow());
    }
}
