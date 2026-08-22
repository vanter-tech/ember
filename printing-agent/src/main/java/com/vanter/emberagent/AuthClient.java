package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AuthClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String fetchToken(AgentConfig config) throws Exception {
        String body = objectMapper.writeValueAsString(new TokenRequestBody(config.apiKey()));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.backendBaseUrl() + "/printing/agents/token"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token exchange failed: HTTP " + response.statusCode());
        }
        TokenResponseBody parsed = objectMapper.readValue(response.body(), TokenResponseBody.class);
        return parsed.token();
    }

    private record TokenRequestBody(String apiKey) {}

    private record TokenResponseBody(String token, long expiresInSeconds) {}
}
