package com.vanter.ember.export.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvWriterTest {

    @Test
    void writeRow_plainFieldsAreNotQuoted() {
        assertThat(CsvWriter.writeRow(List.of("a", "b", "3"))).isEqualTo("a,b,3\r\n");
    }

    @Test
    void writeRow_fieldWithCommaIsQuoted() {
        assertThat(CsvWriter.writeRow(List.of("Lomo, saltado", "5")))
                .isEqualTo("\"Lomo, saltado\",5\r\n");
    }

    @Test
    void writeRow_fieldWithDoubleQuoteIsEscapedByDoublingIt() {
        assertThat(CsvWriter.writeRow(List.of("6\" pizza"))).isEqualTo("\"6\"\" pizza\"\r\n");
    }

    @Test
    void writeRow_fieldWithNewlineIsQuoted() {
        assertThat(CsvWriter.writeRow(List.of("line1\nline2"))).isEqualTo("\"line1\nline2\"\r\n");
    }

    @Test
    void writeRow_nullFieldBecomesAnEmptyString() {
        assertThat(CsvWriter.writeRow(Arrays.asList("a", null))).isEqualTo("a,\r\n");
    }

    @Test
    void writeRow_emptyListProducesJustTheLineEnding() {
        assertThat(CsvWriter.writeRow(List.of())).isEqualTo("\r\n");
    }
}
