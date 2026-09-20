package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.credential.CredentialStore;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.Optional;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PairingClientTest {

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

    private PairingClient client(CredentialStore store) {
        return new PairingClient(store, HttpClient.newHttpClient());
    }

    @Test
    void redeem_ok_savesAndReturnsCredential() {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("{\"apiKey\":\"live-key-123\",\"backendBaseUrl\":\"https://api.ember.test/v1\","
                        + "\"agentId\":\"3f1c9e0a-0000-4000-8000-000000000001\",\"agentName\":\"Cocina PC\"}")
                .addHeader("Content-Type", "application/json")
                .build());
        FakeStore store = new FakeStore();

        AgentCredential credential = client(store).redeem(server.url("/").toString(), "  ABCDE-FGHIJ  ");

        assertEquals("live-key-123", credential.apiKey());
        assertEquals(Optional.of(credential), store.saved);
    }

    @Test
    void redeem_keepsTheServerItWasRedeemedAgainst_notTheUrlInTheResponse() {
        // An on-premise Hub answers with the cloud's default address (its own LAN IP isn't known
        // to the backend); the agent must stay on the Hub it actually paired with.
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("{\"apiKey\":\"hub-key\",\"backendBaseUrl\":\"https://api.ember.vanter.net/v1\","
                        + "\"agentId\":\"3f1c9e0a-0000-4000-8000-000000000001\",\"agentName\":\"Caja 1\"}")
                .addHeader("Content-Type", "application/json")
                .build());
        FakeStore store = new FakeStore();
        String hubUrl = server.url("/").toString();

        AgentCredential credential = client(store).redeem(hubUrl, "ABCDE-FGHIJ");

        String expected = hubUrl.substring(0, hubUrl.length() - 1);
        assertEquals(expected, credential.backendBaseUrl());
        assertEquals(expected, store.saved.orElseThrow().backendBaseUrl());
    }

    @Test
    void redeem_invalidCode_throwsPairingException() {
        server.enqueue(new MockResponse.Builder().code(401).body("nope").build());
        FakeStore store = new FakeStore();

        PairingException ex = assertThrows(PairingException.class,
                () -> client(store).redeem(server.url("/").toString(), "BADCODE"));

        assertTrue(ex.getMessage().contains("inválido"));
        assertTrue(store.saved.isEmpty());
    }

    @Test
    void redeem_rateLimited_throwsWithTooManyAttemptsMessage() {
        server.enqueue(new MockResponse.Builder().code(429).body("").build());

        PairingException ex = assertThrows(PairingException.class,
                () -> client(new FakeStore()).redeem(server.url("/").toString(), "ABCDE"));

        assertTrue(ex.getMessage().contains("Demasiados intentos"));
    }

    @Test
    void redeem_unreachableOrMalformedServer_neverExposesTheAddressInTheMessage() {
        // A space makes URI.create fail with a message that quotes the whole URL.
        PairingException malformed = assertThrows(PairingException.class,
                () -> client(new FakeStore()).redeem("https://api.secret host.example/v1", "ABCDE"));
        assertTrue(!malformed.getMessage().contains("secret"), malformed.getMessage());
        assertTrue(!malformed.getMessage().contains("http"), malformed.getMessage());

        int deadPort = server.getPort();
        server.close();
        PairingException unreachable = assertThrows(PairingException.class,
                () -> client(new FakeStore()).redeem("http://127.0.0.1:" + deadPort, "ABCDE"));
        assertTrue(!unreachable.getMessage().contains("127.0.0.1"), unreachable.getMessage());
        assertTrue(unreachable.getMessage().contains("No se pudo contactar"), unreachable.getMessage());
    }

    private static final class FakeStore implements CredentialStore {
        private Optional<AgentCredential> saved = Optional.empty();

        @Override
        public Optional<AgentCredential> load() {
            return saved;
        }

        @Override
        public void save(AgentCredential credential) {
            saved = Optional.of(credential);
        }

        @Override
        public void clear() {
            saved = Optional.empty();
        }

        @Override
        public boolean isEncrypted() {
            return false;
        }
    }
}
