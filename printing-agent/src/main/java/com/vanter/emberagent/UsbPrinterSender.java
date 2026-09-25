package com.vanter.emberagent;

import com.fazecast.jSerialComm.SerialPort;
import com.github.anastaciocintra.escpos.EscPos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class UsbPrinterSender {

    /** Factored out from {@link #print} so the ESC/POS rendering is testable without a real port. */
    byte[] renderToBytes(String payload) throws IOException {
        return renderToBytes(payload, null);
    }

    byte[] renderToBytes(String payload, byte[] logoPng) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             EscPos escPos = new EscPos(buffer)) {
            if (logoPng != null) {
                TicketLogoRenderer.write(escPos, logoPng);
            }
            escPos.writeLF(payload);
            escPos.feed(3).cut(EscPos.CutMode.FULL);
            return buffer.toByteArray();
        }
    }

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload) throws IOException {
        print(printer, payload, null);
    }

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload, byte[] logoPng)
            throws IOException {
        byte[] bytes = renderToBytes(payload, logoPng);
        SerialPort serialPort = SerialPort.getCommPort(printer.comPort());
        serialPort.openPort();
        try {
            serialPort.getOutputStream().write(bytes);
            serialPort.getOutputStream().flush();
        } finally {
            serialPort.closePort();
        }
    }
}
