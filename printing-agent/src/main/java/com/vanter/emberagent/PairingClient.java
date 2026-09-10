package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.emberagent.credential.CredentialStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Redeems a one-time pairing code (spec §2.2) for the persistent API key, then hands it to the
 * {@link CredentialStore} (DPAPI-encrypted on Windows). After a successful redeem the agent has
 * everything {@code AgentConfig.resolve} needs and never touches the code again.
 */
public class PairingClient {

    private final CredentialStore store;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public PairingClient(CredentialStore store) {
        this(store, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    PairingClient(CredentialStore store, HttpClient http) {
        this.store = store;
        this.http = http;
    }

    public AgentCredential redeem(String backendBaseUrl, String code) {
        try {
            String body = mapper.writeValueAsString(new PairBody(code.trim()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(backendBaseUrl) + "/printing/agents/pair"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429) {
                throw new PairingException("Demasiados intentos. Espera unos minutos e intenta de nuevo.");
            }
            if (res.statusCode() != 200) {
                throw new PairingException("Código inválido, usado o vencido.");
            }
            PairResult parsed = mapper.readValue(res.body(), PairResult.class);
            AgentCredential credential = new AgentCredential(parsed.apiKey(), parsed.backendBaseUrl());
            store.save(credential);
            return credential;
        } catch (PairingException e) {
            throw e;
        } catch (Exception e) {
            throw new PairingException("No se pudo contactar al servidor: " + e.getMessage());
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private record PairBody(String code) {}

    private record PairResult(String apiKey, String backendBaseUrl, String agentId, String agentName) {}
}
