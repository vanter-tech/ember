package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReceiptLayoutTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 9, 20, 17, 9);

    private static String sp(int n) {
        return " ".repeat(n);
    }

    private static String rule(int width) {
        return "-".repeat(width);
    }

    private static ReceiptLayout.Data.Builder base(int width) {
        return ReceiptLayout.Data.builder()
                .header("EMBER").tableNumber(5).billId(12L).when(WHEN)
                .currency("C$").footer("Gracias por visitarnos").width(width);
    }

    @Test
    void render_fullReceipt_on32Columns() {
        ReceiptLayout.Data data = base(32)
                .line(new ReceiptLayout.Line(2, "Hamburguesa", List.of(), new BigDecimal("25.00")))
                .line(new ReceiptLayout.Line(1, "Coca Cola", List.of(), new BigDecimal("3.00")))
                .subtotal(new BigDecimal("28.00")).taxLabel("Impuesto (16%)").tax(new BigDecimal("4.48"))
                .total(new BigDecimal("32.48"))
                .build();

        String expected = String.join("\n",
                sp(13) + "EMBER",
                "Mesa 5",
                "Cuenta #12" + sp(8) + "20/09/26 17:09",
                rule(32),
                "2 Hamburguesa" + sp(12) + "C$25.00",
                "1 Coca Cola" + sp(15) + "C$3.00",
                rule(32),
                "Subtotal" + sp(17) + "C$28.00",
                "Impuesto (16%)" + sp(12) + "C$4.48",
                "TOTAL" + sp(20) + "C$32.48",
                rule(32),
                sp(5) + "Gracias por visitarnos") + "\n";

        assertThat(ReceiptLayout.render(data)).isEqualTo(expected);
    }

    @Test
    void render_withoutTaxLine_showsOnlyTheTotal() {
        ReceiptLayout.Data data = base(32)
                .line(new ReceiptLayout.Line(1, "Agua", List.of(), new BigDecimal("2.00")))
                .subtotal(new BigDecimal("2.00")).total(new BigDecimal("2.00"))
                .build();

        String out = ReceiptLayout.render(data);

        assertThat(out).doesNotContain("Subtotal").doesNotContain("Impuesto");
        assertThat(out).contains("TOTAL" + sp(21) + "C$2.00");
    }

    @Test
    void render_modifiersGoUnderTheirItem() {
        ReceiptLayout.Data data = base(32)
                .line(new ReceiptLayout.Line(1, "Hamburguesa", List.of("Extra queso", "Sin cebolla"), new BigDecimal("14.00")))
                .total(new BigDecimal("14.00"))
                .build();

        assertThat(ReceiptLayout.render(data)).contains(
                "1 Hamburguesa" + sp(12) + "C$14.00\n  + Extra queso\n  + Sin cebolla\n");
    }

    @Test
    void render_narrowPaper_isNeverWiderThanTheConfiguredWidth() {
        ReceiptLayout.Data data = base(32)
                .header("Restaurante con un nombre demasiado largo para el papel")
                .line(new ReceiptLayout.Line(3, "Pizza cuatro quesos familiar con borde relleno", List.of("Extra aceitunas negras y champinones"), new BigDecimal("123456.78")))
                .subtotal(new BigDecimal("100.00")).taxLabel("Impuesto (16%)").tax(new BigDecimal("16.00"))
                .total(new BigDecimal("116.00"))
                .footer("Gracias por visitarnos, esperamos verle muy pronto de nuevo")
                .build();

        for (String line : ReceiptLayout.render(data).split("\n")) {
            assertThat(line.length()).as(line).isLessThanOrEqualTo(32);
        }
    }

    @Test
    void render_longItemName_wrapsButKeepsTheAmountOnTheFirstLine() {
        ReceiptLayout.Data data = base(32)
                .line(new ReceiptLayout.Line(1, "Pizza cuatro quesos familiar con borde relleno", List.of(), new BigDecimal("18.50")))
                .total(new BigDecimal("18.50"))
                .build();

        String out = ReceiptLayout.render(data);
        String firstItemLine = out.split("\n")[4];

        assertThat(firstItemLine).endsWith("C$18.50").startsWith("1 Pizza");
        assertThat(out).contains("familiar").contains("relleno");
    }

    @Test
    void render_58mmAnd80mm_useDifferentWidths() {
        ReceiptLayout.Data narrow = base(32).total(new BigDecimal("1.00")).build();
        ReceiptLayout.Data wide = base(42).total(new BigDecimal("1.00")).build();

        assertThat(ReceiptLayout.render(narrow)).contains(rule(32) + "\n").doesNotContain(rule(33));
        assertThat(ReceiptLayout.render(wide)).contains(rule(42) + "\n").doesNotContain(rule(43));
    }

    @Test
    void render_minimalReceipt_whenThereIsNothingButTheBillNumber() {
        ReceiptLayout.Data data = ReceiptLayout.Data.builder().billId(7L).width(32).build();

        assertThat(ReceiptLayout.render(data)).isEqualTo("Cuenta #7\n");
    }

    @Test
    void render_omitsBlankHeaderAndFooter_andMissingTable() {
        ReceiptLayout.Data data = ReceiptLayout.Data.builder()
                .header("  ").footer("").billId(3L).width(32)
                .total(new BigDecimal("5.00"))
                .build();

        String out = ReceiptLayout.render(data);

        assertThat(out).startsWith("Cuenta #3\n").doesNotContain("Mesa");
        assertThat(out).endsWith("TOTAL" + sp(23) + "5.00\n");
    }

    @Test
    void money_roundsToTwoDecimals_andAcceptsABlankCurrency() {
        assertThat(ReceiptLayout.money(null, new BigDecimal("3.456"))).isEqualTo("3.46");
        assertThat(ReceiptLayout.money("$", new BigDecimal("3"))).isEqualTo("$3.00");
    }
}
