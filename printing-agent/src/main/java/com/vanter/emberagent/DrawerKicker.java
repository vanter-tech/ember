package com.vanter.emberagent;

import com.fazecast.jSerialComm.SerialPort;
import com.github.anastaciocintra.escpos.EscPos;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Opens a cash drawer wired to a receipt printer's RJ11 kick-out port by sending the ESC/POS drawer
 * pulse ({@code ESC p m t1 t2}) — no paper is printed. Connects synchronously with a timeout for
 * the same reason as {@link NetworkPrinterSender}: a swallowed connection failure would ack a
 * drawer that never opened.
 *
 * <p>{@code WINDOWS_QUEUE} printers go through the driver, which may not forward raw pulses, so they
 * are reported as unsupported instead of pretending success. USB-direct drawers (no printer in
 * between) are a future implementation of this same role.
 */
public class DrawerKicker {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** Pulse timings in units of 2 ms: 100 ms on, 500 ms off — accepted by common 12/24 V drawers. */
    private static final int PULSE_ON = 50;
    private static final int PULSE_OFF = 250;

    /**
     * {@code ESC p 0 t1 t2} (connector pin 2). Written byte by byte instead of via {@code
     * EscPos#pulsePin}, which sends the pin as ASCII {@code '0'} (0x30): Epson accepts both forms, but
     * the classic {@code m = 0} is what generic/clone printers document and reliably honor.
     */
    byte[] renderPulse() throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             EscPos escPos = new EscPos(buffer)) {
            escPos.write(0x1B).write(0x70).write(0x00).write(PULSE_ON).write(PULSE_OFF);
            return buffer.toByteArray();
        }
    }

    public void kick(PrinterConfigClient.PrinterConfigDto printer) throws IOException {
        String type = printer.connectionType();
        if ("WINDOWS_QUEUE".equals(type)) {
            throw new IOException("La gaveta no es compatible con impresoras por cola de Windows");
        }
        if ("NETWORK".equals(type)) {
            byte[] bytes = renderPulse();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(printer.host(), printer.port()), CONNECT_TIMEOUT_MS);
                socket.getOutputStream().write(bytes);
                socket.getOutputStream().flush();
            }
        } else if ("USB".equals(type)) {
            byte[] bytes = renderPulse();
            SerialPort serialPort = SerialPort.getCommPort(printer.comPort());
            serialPort.openPort();
            try {
                serialPort.getOutputStream().write(bytes);
                serialPort.getOutputStream().flush();
            } finally {
                serialPort.closePort();
            }
        } else {
            throw new IOException("Conexión no soportada para la gaveta: " + type);
        }
    }
}
