package com.vanter.ember.printing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lays a bill out as fixed-width plain text for a thermal roll or a driver-rendered page. Pure
 * formatting — no repositories, no settings — so it is tested with plain data. Plain ASCII on
 * purpose (an "x", never a "×"): ESC/POS printers print whatever code page they default to.
 * No line is ever wider than {@link Data#width()}: long names wrap, over-long labels are cut.
 */
final class ReceiptLayout {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private ReceiptLayout() {
    }

    /** One printed line item: identical units are already grouped, {@code amount} is the line total. */
    record Line(int quantity, String name, List<String> modifiers, BigDecimal amount) {}

    /**
     * Everything the receipt shows. {@code total == null} means "no amounts to print";
     * {@code taxLabel == null} means "no subtotal/tax breakdown, just the total".
     */
    record Data(
            String header, Integer tableNumber, Long billId, LocalDateTime when, List<Line> lines,
            BigDecimal subtotal, String taxLabel, BigDecimal tax, BigDecimal total,
            String currency, String footer, int width, List<String> infoLines) {

        static Builder builder() {
            return new Builder();
        }

        static final class Builder {
            private String header;
            private Integer tableNumber;
            private Long billId;
            private LocalDateTime when;
            private final List<Line> lines = new ArrayList<>();
            private BigDecimal subtotal;
            private String taxLabel;
            private BigDecimal tax;
            private BigDecimal total;
            private String currency;
            private String footer;
            private int width = 42;
            private final List<String> infoLines = new ArrayList<>();

            Builder header(String v) { this.header = v; return this; }
            Builder tableNumber(Integer v) { this.tableNumber = v; return this; }
            Builder billId(Long v) { this.billId = v; return this; }
            Builder when(LocalDateTime v) { this.when = v; return this; }
            Builder line(Line v) { this.lines.add(v); return this; }
            Builder subtotal(BigDecimal v) { this.subtotal = v; return this; }
            Builder taxLabel(String v) { this.taxLabel = v; return this; }
            Builder tax(BigDecimal v) { this.tax = v; return this; }
            Builder total(BigDecimal v) { this.total = v; return this; }
            Builder currency(String v) { this.currency = v; return this; }
            Builder footer(String v) { this.footer = v; return this; }
            Builder width(int v) { this.width = v; return this; }
            /** One line of the business-info block under the header (wrapped and centered on render). */
            Builder infoLine(String v) { this.infoLines.add(v); return this; }

            Data build() {
                return new Data(header, tableNumber, billId, when, List.copyOf(lines), subtotal, taxLabel,
                        tax, total, currency, footer, width, List.copyOf(infoLines));
            }
        }
    }

    static String render(Data d) {
        int w = d.width();
        StringBuilder out = new StringBuilder();

        for (String line : wrap(d.header(), w)) {
            out.append(center(line, w)).append('\n');
        }
        for (String info : d.infoLines()) {
            for (String line : wrap(info, w)) {
                out.append(center(line, w)).append('\n');
            }
        }
        if (d.tableNumber() != null) {
            out.append("Mesa ").append(d.tableNumber()).append('\n');
        }
        String bill = "Cuenta #" + d.billId();
        out.append(d.when() == null ? bill : twoCols(bill, WHEN.format(d.when()), w)).append('\n');

        boolean hasItems = !d.lines().isEmpty();
        boolean hasTotal = d.total() != null;
        if (hasItems || hasTotal) {
            out.append(rule(w));
        }
        for (Line item : d.lines()) {
            appendItem(out, item, d.currency(), w);
        }
        if (hasItems && hasTotal) {
            out.append(rule(w));
        }
        if (hasTotal) {
            if (d.taxLabel() != null) {
                out.append(twoCols("Subtotal", money(d.currency(), d.subtotal()), w)).append('\n');
                out.append(twoCols(d.taxLabel(), money(d.currency(), d.tax()), w)).append('\n');
            }
            out.append(twoCols("TOTAL", money(d.currency(), d.total()), w)).append('\n');
        }
        List<String> footer = wrap(d.footer(), w);
        if (!footer.isEmpty()) {
            if (hasItems || hasTotal) {
                out.append(rule(w));
            }
            for (String line : footer) {
                out.append(center(line, w)).append('\n');
            }
        }
        return out.toString();
    }

    static String money(String currency, BigDecimal amount) {
        String symbol = currency == null ? "" : currency.strip();
        String value = amount == null ? "" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return symbol + value;
    }

    private static void appendItem(StringBuilder out, Line item, String currency, int w) {
        String amount = money(currency, item.amount());
        String left = item.quantity() + " " + item.name();
        int available = Math.max(1, w - amount.length() - 1);
        List<String> chunks = wrap(left, available);
        out.append(twoCols(chunks.get(0), amount, w)).append('\n');
        for (int i = 1; i < chunks.size(); i++) {
            out.append("  ").append(chunks.get(i)).append('\n');
        }
        for (String modifier : item.modifiers()) {
            List<String> parts = wrap(modifier, Math.max(1, w - 4));
            for (int i = 0; i < parts.size(); i++) {
                out.append(i == 0 ? "  + " : "    ").append(parts.get(i)).append('\n');
            }
        }
    }

    private static String rule(int w) {
        return "-".repeat(w) + "\n";
    }

    private static String center(String text, int w) {
        return " ".repeat(Math.max(0, (w - text.length()) / 2)) + text;
    }

    /** {@code left} then {@code right} pushed to the edge; {@code left} is cut when they cannot both fit. */
    private static String twoCols(String left, String right, int w) {
        int room = Math.max(0, w - right.length() - 1);
        String l = left.length() > room ? left.substring(0, room) : left;
        return l + " ".repeat(Math.max(1, w - l.length() - right.length())) + right;
    }

    /** Word wrap; a word longer than {@code width} is split. A blank or null text has no lines. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }
}
