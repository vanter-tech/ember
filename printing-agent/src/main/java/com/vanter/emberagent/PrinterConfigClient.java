package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class PrinterConfigClient {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<PrinterConfigDto> fetchMyPrinters(String backendBaseUrl, String jwt) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(backendBaseUrl + "/printing/agents/me/printers"))
                .header("Authorization", "Bearer " + jwt)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Failed to fetch printers: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PrinterConfigDto.class));
    }

    public record PrinterConfigDto(
            String id, String agentId, String role, String connectionType,
            String host, Integer port, String comPort, String label, boolean active) {}
}
