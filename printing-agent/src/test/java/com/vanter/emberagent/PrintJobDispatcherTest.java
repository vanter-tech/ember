package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrintJobDispatcherTest {

    private record AckCall(String jobId, String printerConfigId, String result, String error) {}

    private final PrintJobDispatcher dispatcher = new PrintJobDispatcher(
            new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());

    @Test
    void dispatch_matchingNetworkPrinter_printsAndAcksPrinted() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            byte[][] received = new byte[1][];
            Thread serverThread = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    received[0] = socket.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // test thread teardown
                }
            });
            serverThread.start();

            PrinterConfigClient.PrinterConfigDto printer = new PrinterConfigClient.PrinterConfigDto(
                    "p1", "a1", "KITCHEN", "NETWORK", "127.0.0.1", port, null, null, null, "Cocina 1", true);
            AgentConnection.PrintJobPayload job =
                    new AgentConnection.PrintJobPayload("job-1", "KITCHEN", "Mesa 5\n1x Hamburguesa\n");

            List<AckCall> acks = new ArrayList<>();
            dispatcher.dispatch(job, List.of(printer),
                    (jobId, printerConfigId, result, error) -> acks.add(new AckCall(jobId, printerConfigId, result, error)));
            serverThread.join(2000);

            assertTrue(received[0] != null && received[0].length > 0);
            assertEquals(1, acks.size());
            assertEquals(new AckCall("job-1", "p1", "PRINTED", null), acks.get(0));
        }
    }

    @Test
    void dispatch_printerThrows_acksError() {
        // WINDOWS_QUEUE with a nonexistent queue name is used here (rather than a NETWORK
        // printer pointed at a closed port) because escpos-coffee's TcpIpOutputStream connects
        // asynchronously on a background thread and never propagates a connection failure to the
        // caller — see PrintJobDispatcherTest's class javadoc note / report 250. WindowsPrintQueueSender's
        // own PrintService lookup throws synchronously, which is what this test needs to exercise.
        PrinterConfigClient.PrinterConfigDto printer = new PrinterConfigClient.PrinterConfigDto(
                "p1", "a1", "KITCHEN", "WINDOWS_QUEUE", null, null, null,
                "queue-that-does-not-exist", "RAW", "Cocina 1", true);
        AgentConnection.PrintJobPayload job =
                new AgentConnection.PrintJobPayload("job-2", "KITCHEN", "Mesa 5\n1x Hamburguesa\n");

        List<AckCall> acks = new ArrayList<>();
        dispatcher.dispatch(job, List.of(printer),
                (jobId, printerConfigId, result, error) -> acks.add(new AckCall(jobId, printerConfigId, result, error)));

        assertEquals(1, acks.size());
        assertEquals("job-2", acks.get(0).jobId());
        assertEquals("p1", acks.get(0).printerConfigId());
        assertEquals("ERROR", acks.get(0).result());
    }

    @Test
    void dispatch_noPrinterMatchesRole_acksErrorWithNullPrinterId() {
        PrinterConfigClient.PrinterConfigDto receiptPrinter = new PrinterConfigClient.PrinterConfigDto(
                "p1", "a1", "RECEIPT", "NETWORK", "127.0.0.1", 9100, null, null, null, "Recibo", true);
        AgentConnection.PrintJobPayload kitchenJob =
                new AgentConnection.PrintJobPayload("job-3", "KITCHEN", "Mesa 5\n1x Hamburguesa\n");

        List<AckCall> acks = new ArrayList<>();
        dispatcher.dispatch(kitchenJob, List.of(receiptPrinter),
                (jobId, printerConfigId, result, error) -> acks.add(new AckCall(jobId, printerConfigId, result, error)));

        assertEquals(1, acks.size());
        assertEquals("job-3", acks.get(0).jobId());
        assertNull(acks.get(0).printerConfigId());
        assertEquals("ERROR", acks.get(0).result());
        assertTrue(acks.get(0).error().contains("KITCHEN"));
    }
}
