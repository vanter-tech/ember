package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class DrawerKickerTest {

    // ESC p m t1 t2 — pin 2, 100 ms on / 500 ms off (units of 2 ms)
    private static final byte[] PULSE = {0x1B, 0x70, 0x00, 50, (byte) 250};

    private static PrinterConfigClient.PrinterConfigDto printer(String type, String host, Integer port) {
        return new PrinterConfigClient.PrinterConfigDto(
                "p1", "a1", "RECEIPT", type, host, port, null, null, "RAW", "Caja", true, true);
    }

    @Test
    void renderPulse_containsTheEscPPulseAndNoPrintableText() throws Exception {
        byte[] bytes = new DrawerKicker().renderPulse();
        assertTrue(TicketLogoRendererTest.indexOf(bytes, PULSE) >= 0,
                "expected ESC p 0 50 250 in " + java.util.HexFormat.of().formatHex(bytes));
    }

    @Test
    void kick_networkPrinter_writesThePulseToTheSocket() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            byte[][] received = new byte[1][];
            Thread t = new Thread(() -> {
                try (Socket s = server.accept()) {
                    received[0] = s.getInputStream().readAllBytes();
                } catch (IOException ignored) {
                    // teardown
                }
            });
            t.start();

            new DrawerKicker().kick(printer("NETWORK", "127.0.0.1", server.getLocalPort()));
            t.join(2000);

            assertTrue(received[0] != null && TicketLogoRendererTest.indexOf(received[0], PULSE) >= 0);
        }
    }

    @Test
    void kick_windowsQueue_isReportedAsUnsupported() {
        IOException e = assertThrows(IOException.class,
                () -> new DrawerKicker().kick(printer("WINDOWS_QUEUE", null, null)));
        assertTrue(e.getMessage().contains("cola de Windows"));
    }

    @Test
    void kick_unknownConnection_isRejected() {
        assertEquals("Conexión no soportada para la gaveta: FAX",
                assertThrows(IOException.class, () -> new DrawerKicker().kick(printer("FAX", null, null)))
                        .getMessage());
    }
}
