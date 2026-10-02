package com.vanter.ember.numbering;

/** An independent per-tenant numbering series: bill receipts and kitchen tickets never share a counter. */
public enum DocumentSeries {
    BILL(""),
    KDS("KDS-");

    private final String infix;

    DocumentSeries(String infix) {
        this.infix = infix;
    }

    /** {@code ELPO-000123} for bills, {@code ELPO-KDS-000045} for kitchen tickets; wider than 6 digits only past 999999. */
    String format(String prefix, int number) {
        return prefix + "-" + infix + String.format("%06d", number);
    }
}
