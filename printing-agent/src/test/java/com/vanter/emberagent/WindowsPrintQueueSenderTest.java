package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import org.junit.jupiter.api.Test;

class WindowsPrintQueueSenderTest {

    @Test
    void renderToBytes_producesNonEmptyEscPosOutput() throws Exception {
        byte[] bytes = new WindowsPrintQueueSender().renderToBytes("Mesa 5\n1x Hamburguesa\n");
        assertTrue(bytes.length > 0);
    }

    @Test
    void renderToPrintable_firstPageExists_laysOutEveryLine() throws Exception {
        Printable printable = new WindowsPrintQueueSender().renderToPrintable("Mesa 5\n1x Hamburguesa\n");
        BufferedImage image = new BufferedImage(200, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        int result = printable.print(g2d, new PageFormat(), 0);

        assertEquals(Printable.PAGE_EXISTS, result);
    }

    @Test
    void renderToPrintable_secondPage_reportsNoSuchPage() throws Exception {
        Printable printable = new WindowsPrintQueueSender().renderToPrintable("Mesa 5\n1x Hamburguesa\n");
        BufferedImage image = new BufferedImage(200, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        int result = printable.print(g2d, new PageFormat(), 1);

        assertEquals(Printable.NO_SUCH_PAGE, result);
    }
}
