package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Reports the locally enumerated Windows print queues (spec §2.3) to the backend on every
 * connect/refetch. Best-effort: a non-204 is logged and swallowed — discovery never blocks
 * the agent from printing.
 */
public class DiscoveredPrintersClient {

    private static final Logger log = System.getLogger(DiscoveredPrintersClient.class.getName());

    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public DiscoveredPrintersClient() {
        this(HttpClient.newHttpClient());
    }

    DiscoveredPrintersClient(HttpClient http) {
        this.http = http;
    }

    public void report(String backendBaseUrl, String jwt, List<DiscoveredPrinter> printers) {
        try {
            String body = mapper.writeValueAsString(new ReportBody(printers));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(backendBaseUrl) + "/printing/agents/me/discovered-printers"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + jwt)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 204) {
                log.log(Level.WARNING, "No se pudo reportar impresoras descubiertas: HTTP " + res.statusCode());
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "No se pudo reportar impresoras descubiertas: " + e.getMessage());
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private record ReportBody(List<DiscoveredPrinter> printers) {}
}
