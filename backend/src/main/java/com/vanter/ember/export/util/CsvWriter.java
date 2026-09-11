package com.vanter.ember.export.util;

import java.util.List;

/**
 * Minimal RFC 4180 CSV row writer — no external dependency, since this app's only CSV need is
 * two fixed-column files (see {@code ExportService}). A field is quoted only when it contains a
 * comma, a double quote, or a newline; embedded quotes are doubled. Rows are CRLF-terminated for
 * maximum Excel/Sheets compatibility.
 */
public final class CsvWriter {

    private CsvWriter() {}

    public static String writeRow(List<String> fields) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                row.append(',');
            }
            row.append(escape(fields.get(i)));
        }
        row.append("\r\n");
        return row.toString();
    }

    private static String escape(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuoting =
                field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r");
        if (!needsQuoting) {
            return field;
        }
        return "\"" + field.replace("\"", "\"\"") + "\"";
    }
}
