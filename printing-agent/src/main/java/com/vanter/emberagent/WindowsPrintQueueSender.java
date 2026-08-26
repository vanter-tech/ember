package com.vanter.emberagent;

import com.github.anastaciocintra.escpos.EscPos;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;

/**
 * Sends a job to a printer registered as a Windows print queue rather than a serial/COM port or
 * a network socket. Some generic 80mm thermal printers (confirmed during the PRINT-07 manual
 * verification, 2026-08-26) enumerate over USB as a native printer-class device — Windows gives
 * them their own print queue, but jSerialComm ({@link UsbPrinterSender}) never sees them, since
 * it only lists serial ports.
 *
 * <p>Two render modes, per {@code printer.renderMode()}:
 * <ul>
 *   <li>{@code RAW} (default): submits ESC/POS bytes via {@link DocFlavor.BYTE_ARRAY#AUTOSENSE},
 *       which makes the Windows spooler write them straight to the port (RAW datatype),
 *       bypassing the queue's own driver rendering — needs a real ESC/POS-capable printer.
 *   <li>{@code DRIVER}: submits a {@link Printable} through {@link PrinterJob}, letting the
 *       queue's own Windows driver rasterize plain text — needed for printers with no ESC/POS
 *       support at all (e.g. an inkjet queue like "EPSON L3210 Series", used to validate this
 *       whole chain against real hardware before returning to a thermal printer — see report
 *       253) that only work through their driver.
 * </ul>
 */
public class WindowsPrintQueueSender {

    /** Factored out from {@link #print} so the ESC/POS rendering is testable without a real queue. */
    byte[] renderToBytes(String payload) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
             EscPos escPos = new EscPos(buffer)) {
            escPos.writeLF(payload);
            escPos.feed(3).cut(EscPos.CutMode.FULL);
            return buffer.toByteArray();
        }
    }

    /**
     * Factored out from {@link #printViaDriver} so the page layout is testable by invoking
     * {@link Printable#print} directly against an off-screen {@link Graphics2D}, without a real
     * printer or {@link PrinterJob}.
     */
    Printable renderToPrintable(String payload) {
        String[] lines = payload.split("\n", -1);
        return (Graphics graphics, PageFormat pageFormat, int pageIndex) -> {
            if (pageIndex > 0) {
                return Printable.NO_SUCH_PAGE;
            }
            Graphics2D g2d = (Graphics2D) graphics;
            g2d.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 10);
            g2d.setFont(font);
            int lineHeight = g2d.getFontMetrics(font).getHeight();
            int y = lineHeight;
            for (String line : lines) {
                g2d.drawString(line, 0, y);
                y += lineHeight;
            }
            return Printable.PAGE_EXISTS;
        };
    }

    public void print(PrinterConfigClient.PrinterConfigDto printer, String payload)
            throws IOException, PrintException {
        PrintService service = findService(printer.windowsQueueName());
        if ("DRIVER".equals(printer.renderMode())) {
            printViaDriver(service, payload);
        } else {
            printRaw(service, payload);
        }
    }

    private void printRaw(PrintService service, String payload) throws IOException, PrintException {
        byte[] bytes = renderToBytes(payload);
        DocPrintJob job = service.createPrintJob();
        SimpleDoc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        job.print(doc, null);
    }

    private void printViaDriver(PrintService service, String payload) throws IOException {
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
            job.setPrintable(renderToPrintable(payload));
            job.print();
        } catch (PrinterException e) {
            throw new IOException("Windows driver print failed for queue '"
                    + service.getName() + "': " + e.getMessage(), e);
        }
    }

    private PrintService findService(String queueName) throws IOException {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (service.getName().equals(queueName)) {
                return service;
            }
        }
        throw new IOException("Windows print queue not found: " + queueName);
    }
}
