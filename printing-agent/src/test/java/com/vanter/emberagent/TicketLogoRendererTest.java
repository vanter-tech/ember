package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.anastaciocintra.escpos.EscPos;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TicketLogoRendererTest {

    /** ESC/POS "GS v 0": print raster bit image. */
    private static final byte[] RASTER_HEADER = {0x1D, 0x76, 0x30};
    /** ESC/POS "ESC a 0": left alignment. */
    private static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};

    static byte[] logoPng() throws Exception {
        BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, 32, 32);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Test
    void write_emitsARasterImageThenRestoresLeftAlignment() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (EscPos escPos = new EscPos(buffer)) {
            assertTrue(TicketLogoRenderer.write(escPos, logoPng()));
        }
        byte[] bytes = buffer.toByteArray();

        int raster = indexOf(bytes, RASTER_HEADER);
        int alignLeft = indexOf(bytes, ALIGN_LEFT);
        assertTrue(raster >= 0, "raster image command expected");
        assertTrue(alignLeft > raster, "left alignment must be restored after the image");
    }

    @Test
    void write_withAnUndecodableImage_writesNothingAndReportsFalse() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean written;
        try (EscPos escPos = new EscPos(buffer)) {
            written = TicketLogoRenderer.write(escPos, "not a png".getBytes());
        }

        assertFalse(written);
        assertEquals(-1, indexOf(buffer.toByteArray(), RASTER_HEADER));
    }

    @Test
    void decode_nullOrEmpty_isNull() {
        assertNull(TicketLogoRenderer.decode(null));
        assertNull(TicketLogoRenderer.decode(new byte[0]));
    }

    @Test
    void usbRender_withLogo_putsTheImageBeforeTheText() throws Exception {
        byte[] withLogo = new UsbPrinterSender().renderToBytes("Mesa 5\n", logoPng());
        byte[] withoutLogo = new UsbPrinterSender().renderToBytes("Mesa 5\n");

        int raster = indexOf(withLogo, RASTER_HEADER);
        int text = indexOf(withLogo, "Mesa 5".getBytes());
        assertTrue(raster >= 0 && text > raster, "logo must come before the ticket text");
        assertEquals(-1, indexOf(withoutLogo, RASTER_HEADER));
    }

    @Test
    void usbRender_withoutLogo_isUnchanged() throws Exception {
        UsbPrinterSender sender = new UsbPrinterSender();

        assertArrayEquals(sender.renderToBytes("Mesa 5\n"), sender.renderToBytes("Mesa 5\n", null));
    }

    @Test
    void windowsRender_withLogo_putsTheImageBeforeTheText() throws Exception {
        byte[] bytes = new WindowsPrintQueueSender().renderToBytes("Mesa 5\n", logoPng());

        int raster = indexOf(bytes, RASTER_HEADER);
        assertTrue(raster >= 0 && indexOf(bytes, "Mesa 5".getBytes()) > raster);
    }

    @Test
    void windowsDriverPrintable_withLogo_drawsIntoTheTopOfThePage() throws Exception {
        java.awt.print.Printable printable = new WindowsPrintQueueSender().renderToPrintable("", logoPng());
        BufferedImage page = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);

        java.awt.print.PageFormat format = new java.awt.print.PageFormat();
        int result = printable.print(g, format, 0);
        g.dispose();

        assertEquals(java.awt.print.Printable.PAGE_EXISTS, result);
        boolean anyDark = false;
        int top = (int) format.getImageableY();
        int left = (int) format.getImageableX();
        for (int y = top; y < top + 40 && !anyDark; y++) {
            for (int x = left; x < left + (int) format.getImageableWidth() && !anyDark; x++) {
                anyDark = (page.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF;
            }
        }
        assertTrue(anyDark, "the logo should have drawn dark pixels near the top of the page");
    }
}
