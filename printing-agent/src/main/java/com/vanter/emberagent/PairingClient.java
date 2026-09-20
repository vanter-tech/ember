package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.emberagent.credential.CredentialStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

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

    static final String TOO_MANY_ATTEMPTS = "Demasiados intentos. Espera unos minutos e intenta de nuevo.";
    static final String INVALID_CODE = "Código inválido, usado o vencido.";
    static final String UNREACHABLE = "No se pudo contactar al servidor. Verifica tu conexión a internet.";
    public static final String NO_LOCAL_SERVER =
            "No se encontró ningún servidor Ember en esta red. Verifica que el Ember Hub esté encendido y en la misma red.";

    /**
     * Redeems {@code code} against each candidate server in turn (the Hubs found on the LAN) and
     * keeps the first that accepts it. When none does, the most useful failure wins: rate-limited,
     * then wrong/expired code (a server answered), then unreachable.
     */
    public AgentCredential redeemAny(List<String> backendBaseUrls, String code) {
        if (backendBaseUrls.isEmpty()) {
            throw new PairingException(NO_LOCAL_SERVER);
        }
        PairingException best = null;
        for (String url : backendBaseUrls) {
            try {
                return redeem(url, code);
            } catch (PairingException e) {
                if (best == null || rank(e) > rank(best)) {
                    best = e;
                }
            }
        }
        throw best;
    }

    private static int rank(PairingException e) {
        return switch (e.getMessage()) {
            case TOO_MANY_ATTEMPTS -> 2;
            case INVALID_CODE -> 1;
            default -> 0;
        };
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
                throw new PairingException(TOO_MANY_ATTEMPTS);
            }
            if (res.statusCode() != 200) {
                throw new PairingException(INVALID_CODE);
            }
            PairResult parsed = mapper.readValue(res.body(), PairResult.class);
            // Keep the server the code was actually redeemed against, NOT the URL in the response:
            // the response carries the cloud's address, and against an on-premise Ember Hub (LAN
            // IP, API at "/") that would silently point the agent back at the cloud.
            AgentCredential credential = new AgentCredential(parsed.apiKey(), trimTrailingSlash(backendBaseUrl));
            store.save(credential);
            return credential;
        } catch (PairingException e) {
            throw e;
        } catch (Exception e) {
            // The exception text can carry the request URL (e.g. a malformed-URI message); the
            // operator must never see the backend address, so it goes to the log, not the UI.
            System.err.println("[print-agent] pairing request failed: " + e);
            throw new PairingException(UNREACHABLE);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private record PairBody(String code) {}

    private record PairResult(String apiKey, String backendBaseUrl, String agentId, String agentName) {}
}
