package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PrinterConfigClientTest {

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
    void fetchMyPrinters_parsesListFromResponse() throws Exception {
        server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("[{\"id\":\"p1\",\"agentId\":\"a1\",\"role\":\"KITCHEN\",\"connectionType\":\"NETWORK\","
                        + "\"host\":\"10.0.0.5\",\"port\":9100,\"comPort\":null,\"label\":\"Cocina 1\",\"active\":true}]")
                .addHeader("Content-Type", "application/json")
                .build());

        List<PrinterConfigClient.PrinterConfigDto> printers =
                new PrinterConfigClient().fetchMyPrinters(server.url("/").toString(), "fake-jwt");

        assertEquals(1, printers.size());
        assertEquals("Cocina 1", printers.get(0).label());
    }
}
