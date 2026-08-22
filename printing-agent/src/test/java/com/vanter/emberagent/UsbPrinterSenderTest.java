package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UsbPrinterSenderTest {

    @Test
    void renderToBytes_producesNonEmptyEscPosOutput() throws Exception {
        byte[] bytes = new UsbPrinterSender().renderToBytes("Mesa 5\n1x Hamburguesa\n");
        assertTrue(bytes.length > 0);
    }
}
