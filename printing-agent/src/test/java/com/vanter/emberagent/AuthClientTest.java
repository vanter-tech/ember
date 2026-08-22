package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthClientTest {

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

    @Test
    void fetchToken_parsesTokenFromResponse() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("{\"token\":\"signed.jwt.token\",\"expiresInSeconds\":1200}")
                .addHeader("Content-Type", "application/json")
                .build());

        AgentConfig config = new AgentConfig(server.url("/").toString(), "test-api-key");
        AuthClient authClient = new AuthClient();

        String token = authClient.fetchToken(config);

        assertEquals("signed.jwt.token", token);
    }
}
