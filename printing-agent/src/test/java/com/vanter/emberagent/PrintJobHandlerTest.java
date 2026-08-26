package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link PrintJobHandler} refetches printers from the backend on every job instead of reusing a
 * snapshot from connect time — a printer added/edited/deactivated mid-session (a long-lived
 * STOMP connection can run for hours) used to stay invisible to the agent until its next
 * reconnect. These tests assert the refetch actually happens per job, not once.
 */
class PrintJobHandlerTest {

    private MockWebServer server;
    private static final String PRINTER_JSON =
            "[{\"id\":\"p1\",\"agentId\":\"a1\",\"role\":\"KITCHEN\",\"connectionType\":\"WINDOWS_QUEUE\","
                    + "\"host\":null,\"port\":null,\"comPort\":null,\"windowsQueueName\":\"missing-queue\","
                    + "\"label\":\"Cocina 1\",\"active\":true}]";

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
    void handle_fetchesPrintersFreshOnEveryJob_notJustOnce() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body(PRINTER_JSON)
                .addHeader("Content-Type", "application/json").build());
        server.enqueue(new MockResponse.Builder().code(200).body(PRINTER_JSON)
                .addHeader("Content-Type", "application/json").build());

        PrintJobDispatcher dispatcher = new PrintJobDispatcher(
                new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());
        PrintJobHandler handler = new PrintJobHandler(
                new PrinterConfigClient(), dispatcher, server.url("/").toString(), "fake-jwt");

        List<Object[]> acks = new ArrayList<>();
        PrintJobDispatcher.AckCallback ackCallback =
                (jobId, printerConfigId, result, error) -> acks.add(new Object[] {jobId, result});

        handler.handle(new AgentConnection.PrintJobPayload("job-1", "KITCHEN", "payload-1"), ackCallback);
        handler.handle(new AgentConnection.PrintJobPayload("job-2", "KITCHEN", "payload-2"), ackCallback);

        assertEquals(2, server.getRequestCount(),
                "expected one printer-list fetch per job, not a cached snapshot");
        assertEquals(2, acks.size());
    }

    @Test
    void handle_printerFetchFails_acksErrorInsteadOfThrowing() throws Exception {
        server.enqueue(new MockResponse.Builder().code(500).body("boom").build());

        PrintJobDispatcher dispatcher = new PrintJobDispatcher(
                new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());
        PrintJobHandler handler = new PrintJobHandler(
                new PrinterConfigClient(), dispatcher, server.url("/").toString(), "fake-jwt");

        List<Object[]> acks = new ArrayList<>();
        PrintJobDispatcher.AckCallback ackCallback =
                (jobId, printerConfigId, result, error) -> acks.add(new Object[] {jobId, result, error});

        handler.handle(new AgentConnection.PrintJobPayload("job-1", "KITCHEN", "payload-1"), ackCallback);

        assertEquals(1, acks.size());
        assertEquals("job-1", acks.get(0)[0]);
        assertEquals("ERROR", acks.get(0)[1]);
        assertTrue(((String) acks.get(0)[2]).length() > 0);
    }
}
