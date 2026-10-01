package com.vanter.ember.session.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TableLabelsTest {

    @Test
    void joined_aloneIsJustThePrimary() {
        assertThat(TableLabels.joined(3, List.of())).isEqualTo("M3");
    }

    @Test
    void joined_listsPrimaryThenLinkedInOrder() {
        assertThat(TableLabels.joined(3, List.of(4, 5))).isEqualTo("M3+M4+M5");
    }

    @Test
    void joined_toleratesNull() {
        assertThat(TableLabels.joined(3, null)).isEqualTo("M3");
    }

    @Test
    void ticketLine_individualTableKeepsTheLegacyWording() {
        assertThat(TableLabels.ticketLine(5, List.of())).isEqualTo("Mesa 5");
        assertThat(TableLabels.ticketLine(5, null)).isEqualTo("Mesa 5");
    }

    @Test
    void ticketLine_mergedTablesReadMxPlusMyUnidas() {
        assertThat(TableLabels.ticketLine(3, List.of(4))).isEqualTo("M3+M4 - Unidas");
        assertThat(TableLabels.ticketLine(3, List.of(4, 5))).isEqualTo("M3+M4+M5 - Unidas");
    }
}
