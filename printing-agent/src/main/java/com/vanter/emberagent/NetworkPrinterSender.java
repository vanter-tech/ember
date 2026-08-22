package com.vanter.emberagent;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.output.TcpIpOutputStream;
import java.io.IOException;

/**
 * Renders a structured job payload (spec §2.7: plain text, never bytes from the backend) to
 * ESC/POS and writes it over a raw TCP socket — the printer's own firmware interprets the
 * ESC/POS control sequences {@code EscPos} emits.
 */
public class NetworkPrinterSender {

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload) throws IOException {
        try (TcpIpOutputStream out = new TcpIpOutputStream(printer.host(), printer.port());
             EscPos escPos = new EscPos(out)) {
            escPos.writeLF(payload);
            escPos.feed(3).cut(EscPos.CutMode.FULL);
        }
    }
}
