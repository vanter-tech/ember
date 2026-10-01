package com.vanter.ember.session.model;

import java.util.List;

/** Display strings for a (possibly merged) table. Pure; shared by kitchen, receipt and reports. */
public final class TableLabels {

    private TableLabels() {
    }

    /** {@code M3} alone, {@code M3+M4+M5} when merged. */
    public static String joined(int primary, List<Integer> linked) {
        StringBuilder sb = new StringBuilder("M").append(primary);
        if (linked != null) {
            for (Integer number : linked) {
                sb.append("+M").append(number);
            }
        }
        return sb.toString();
    }

    /** Ticket/receipt line: {@code Mesa 3} for an individual table, {@code M3+M4 - Unidas} when merged. */
    public static String ticketLine(int primary, List<Integer> linked) {
        if (linked == null || linked.isEmpty()) {
            return "Mesa " + primary;
        }
        return joined(primary, linked) + " - Unidas";
    }
}
