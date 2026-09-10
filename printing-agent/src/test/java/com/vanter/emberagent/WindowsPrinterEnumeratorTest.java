package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowsPrinterEnumeratorTest {

    private String fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/get-printer-sample.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parse_fixture_returnsAllThreePrintersWithInkjetGuess() throws Exception {
        List<DiscoveredPrinter> printers = WindowsPrinterEnumerator.parse(fixture());

        assertEquals(3, printers.size());
        assertEquals("EPSON L3210 Series", printers.get(0).name());
        assertEquals("USB001", printers.get(0).portName());
        assertTrue(printers.get(0).inkjetGuess());
        assertFalse(printers.get(2).inkjetGuess()); // POS-80 / Generic / Text Only
    }

    @Test
    void parse_bareSingleObject_returnsOnePrinter() {
        List<DiscoveredPrinter> printers = WindowsPrinterEnumerator.parse(
                "{\"Name\":\"EPSON ET-2850 Series\",\"DriverName\":\"EPSON ET-2850 Series\",\"PortName\":\"USB002\"}");

        assertEquals(1, printers.size());
        assertEquals("EPSON ET-2850 Series", printers.get(0).name());
        assertTrue(printers.get(0).inkjetGuess());
    }

    @Test
    void parse_emptyOrGarbage_returnsEmptyList() {
        assertTrue(WindowsPrinterEnumerator.parse("").isEmpty());
        assertTrue(WindowsPrinterEnumerator.parse("   ").isEmpty());
        assertTrue(WindowsPrinterEnumerator.parse(null).isEmpty());
        assertTrue(WindowsPrinterEnumerator.parse("not json").isEmpty());
    }

    @Test
    void looksLikeInkjet_truthTable() {
        assertTrue(WindowsPrinterEnumerator.looksLikeInkjet("EPSON L3210 Series"));
        assertTrue(WindowsPrinterEnumerator.looksLikeInkjet("EPSON ET-2850 Series"));
        assertFalse(WindowsPrinterEnumerator.looksLikeInkjet("Generic / Text Only"));
        assertFalse(WindowsPrinterEnumerator.looksLikeInkjet("OKI POS80"));
        assertFalse(WindowsPrinterEnumerator.looksLikeInkjet(null));
    }
}
