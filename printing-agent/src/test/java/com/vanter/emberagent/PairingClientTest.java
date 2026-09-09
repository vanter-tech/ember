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
        assertEquals("https://api.ember.test/v1", credential.backendBaseUrl());
        assertEquals(Optional.of(credential), store.saved);
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
