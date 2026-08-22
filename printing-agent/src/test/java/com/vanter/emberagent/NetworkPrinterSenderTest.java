package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;

class NetworkPrinterSenderTest {

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
                    "p1", "a1", "KITCHEN", "NETWORK", "127.0.0.1", port, null, "Cocina 1", true);

            new NetworkPrinterSender().print(printer, "Mesa 5\n1x Hamburguesa\n");
            serverThread.join(2000);

            assertTrue(received[0] != null && received[0].length > 0);
        }
    }
}
