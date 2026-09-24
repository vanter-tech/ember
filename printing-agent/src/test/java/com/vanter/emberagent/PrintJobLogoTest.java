package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrintJobLogoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void payload_withLogoFlag_deserializes() throws Exception {
        AgentConnection.PrintJobPayload job = mapper.readValue(
                "{\"jobId\":\"j1\",\"role\":\"RECEIPT\",\"payload\":\"x\",\"logo\":true}",
                AgentConnection.PrintJobPayload.class);

        assertTrue(job.logo());
    }

    @Test
    void payload_fromAnOlderBackendWithoutTheFlag_defaultsToNoLogo() throws Exception {
        AgentConnection.PrintJobPayload job = mapper.readValue(
                "{\"jobId\":\"j1\",\"role\":\"RECEIPT\",\"payload\":\"x\"}",
                AgentConnection.PrintJobPayload.class);

        assertFalse(job.logo());
        assertEquals("x", job.payload());
    }

    @Test
    void dispatch_withALogo_sendsTheImageBeforeTheTextOverTcp() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            byte[][] received = new byte[1][];
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    received[0] = socket.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // teardown
                }
            });
            server.start();
            PrinterConfigClient.PrinterConfigDto printer = new PrinterConfigClient.PrinterConfigDto(
                    "p1", "a1", "RECEIPT", "NETWORK", "127.0.0.1", serverSocket.getLocalPort(),
                    null, null, null, "Caja", true);
            String[] result = new String[1];

            new PrintJobDispatcher(new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender())
                    .dispatch(
                            new AgentConnection.PrintJobPayload("j1", "RECEIPT", "Mesa 5\n", true),
                            List.of(printer),
                            TicketLogoRendererTest.logoPng(),
                            (jobId, printerId, res, error) -> result[0] = res);
            server.join(2000);

            assertEquals("PRINTED", result[0]);
            int raster = TicketLogoRendererTest.indexOf(received[0], new byte[] {0x1D, 0x76, 0x30});
            int text = TicketLogoRendererTest.indexOf(received[0], "Mesa 5".getBytes());
            assertTrue(raster >= 0 && text > raster);
        }
    }

    @Test
    void dispatch_withoutALogo_printsTextOnly() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            byte[][] received = new byte[1][];
            Thread server = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    received[0] = socket.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // teardown
                }
            });
            server.start();
            PrinterConfigClient.PrinterConfigDto printer = new PrinterConfigClient.PrinterConfigDto(
                    "p1", "a1", "RECEIPT", "NETWORK", "127.0.0.1", serverSocket.getLocalPort(),
                    null, null, null, "Caja", true);

            new PrintJobDispatcher(new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender())
                    .dispatch(
                            new AgentConnection.PrintJobPayload("j1", "RECEIPT", "Mesa 5\n"),
                            List.of(printer),
                            (jobId, printerId, res, error) -> {});
            server.join(2000);

            assertEquals(-1, TicketLogoRendererTest.indexOf(received[0], new byte[] {0x1D, 0x76, 0x30}));
        }
    }
}
