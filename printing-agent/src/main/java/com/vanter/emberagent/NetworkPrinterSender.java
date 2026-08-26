package com.vanter.emberagent;

import com.github.anastaciocintra.escpos.EscPos;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Renders a structured job payload (spec §2.7: plain text, never bytes from the backend) to
 * ESC/POS and writes it over a raw TCP socket — the printer's own firmware interprets the
 * ESC/POS control sequences {@code EscPos} emits.
 *
 * <p>Opens the {@link Socket} directly rather than via {@code escpos-coffee}'s own
 * {@code TcpIpOutputStream}: that class connects the real socket on a detached background
 * thread fed by an internal in-memory pipe, so the caller's writes always "succeed" against the
 * pipe regardless of whether the TCP connection actually worked — a printer that's offline or
 * unreachable silently acked {@code PRINTED} (found+fixed 2026-08-26, report 255). Connecting
 * synchronously here, with an explicit timeout, makes any connection/write failure a normal
 * {@link IOException} on the calling thread — exactly what {@link PrintJobDispatcher}'s
 * {@code catch} already expects.
 */
public class NetworkPrinterSender {

    private static final int CONNECT_TIMEOUT_MS = 5000;

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.host(), printer.port()), CONNECT_TIMEOUT_MS);
            try (EscPos escPos = new EscPos(socket.getOutputStream())) {
                escPos.writeLF(payload);
                escPos.feed(3).cut(EscPos.CutMode.FULL);
            }
        }
    }
}
