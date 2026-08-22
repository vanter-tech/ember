package com.vanter.emberagent;

import com.fazecast.jSerialComm.SerialPort;
import com.github.anastaciocintra.escpos.EscPos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class UsbPrinterSender {

    /** Factored out from {@link #print} so the ESC/POS rendering is testable without a real port. */
    byte[] renderToBytes(String payload) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             EscPos escPos = new EscPos(buffer)) {
            escPos.writeLF(payload);
            escPos.feed(3).cut(EscPos.CutMode.FULL);
            return buffer.toByteArray();
        }
    }

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload) throws IOException {
        byte[] bytes = renderToBytes(payload);
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
