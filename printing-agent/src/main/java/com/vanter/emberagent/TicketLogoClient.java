package com.vanter.emberagent;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Downloads the restaurant's receipt logo from {@code /printing/agents/me/ticket-logo}. The last
 * copy is kept in memory with its {@code ETag}, so a ticket costs one tiny conditional request
 * (304) instead of re-downloading the bitmap.
 *
 * <p>Best effort by design: any failure (offline backend, 404, bad status) yields empty so the
 * ticket still prints, without a logo. If the backend is unreachable but a copy is cached, the
 * cached copy is used.
 */
public class TicketLogoClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Same ceiling the renderer enforces: the served bitmap is a small 1-bit PNG. */
    static final int MAX_BYTES = TicketLogoRenderer.MAX_BYTES;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    private volatile String cachedEtag;
    private volatile byte[] cachedPng;

    public Optional<byte[]> fetch(String backendBaseUrl, String jwt) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(backendBaseUrl + "/printing/agents/me/ticket-logo"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + jwt)
                    .GET();
            if (cachedEtag != null && cachedPng != null) {
                request.header("If-None-Match", cachedEtag);
            }
            HttpResponse<InputStream> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            // Bounded read: a misbehaving backend cannot make the agent buffer an unbounded body.
            byte[] body;
            try (InputStream in = response.body()) {
                body = in.readNBytes(MAX_BYTES + 1);
            }
            if (status == 304 && cachedPng != null) {
                return Optional.of(cachedPng);
            }
            if (status == 200 && body.length > MAX_BYTES) {
                System.err.println("[print-agent] el logo del ticket excede el tamano maximo; se ignora");
                return Optional.empty();
            }
            if (status == 200 && body.length > 0) {
                cachedPng = body;
                cachedEtag = response.headers().firstValue("ETag").orElse(null);
                return Optional.of(cachedPng);
            }
            if (status == 404) {
                // The admin removed the logo: forget the stale copy.
                cachedPng = null;
                cachedEtag = null;
            }
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("[print-agent] no se pudo obtener el logo del ticket: " + e.getMessage());
            return Optional.ofNullable(cachedPng);
        }
    }
}
