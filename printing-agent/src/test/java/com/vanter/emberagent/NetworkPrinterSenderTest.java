package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class NetworkPrinterSenderTest {

    /**
     * Reproduces the bug found while writing {@code PrintJobDispatcherTest} (report 250):
     * {@code escpos-coffee}'s {@code TcpIpOutputStream} connects the real socket on a detached
     * background thread fed by an internal pipe, so the caller's writes always "succeed"
     * against the pipe regardless of whether the TCP connection actually worked — a printer
     * that's offline/unreachable used to silently ack {@code PRINTED}. Uses a closed port
     * (bound then immediately released) rather than a non-routable IP, since the latter can
     * hang on some networks instead of failing fast with connection-refused.
     */
    @Test
    void print_connectionRefused_throwsIOException() throws Exception {
        int closedPort;
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            closedPort = serverSocket.getLocalPort();
        }

        PrinterConfigClient.PrinterConfigDto printer = new PrinterConfigClient.PrinterConfigDto(
                "p1", "a1", "KITCHEN", "NETWORK", "127.0.0.1", closedPort, null, null, null, "Cocina 1", true);

        assertThrows(IOException.class, () -> new NetworkPrinterSender().print(printer, "Mesa 5\n"));
    }

    @Test
    void print_sendsBytesToTcpSocket() throws Exception {
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

            new NetworkPrinterSender().print(printer, "Mesa 5\n1x Hamburguesa\n");
            serverThread.join(2000);

            assertTrue(received[0] != null && received[0].length > 0);
        }
    }
}
