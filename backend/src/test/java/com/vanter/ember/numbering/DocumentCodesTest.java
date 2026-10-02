package com.vanter.ember.numbering;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentCodesTest {

    @Test
    void prefix_isTheFirstFourLettersOfTheSlug_upperCase() {
        assertThat(DocumentCodes.prefixFromSlug("embers-grill")).isEqualTo("EMBE");
    }

    @Test
    void prefix_dropsHyphensAndSymbols_beforeCutting() {
        assertThat(DocumentCodes.prefixFromSlug("el-pollo-loco")).isEqualTo("ELPO");
        assertThat(DocumentCodes.prefixFromSlug("a_b.c-d-e")).isEqualTo("ABCD");
    }

    @Test
    void prefix_ofAShortSlug_keepsWhatItHas() {
        assertThat(DocumentCodes.prefixFromSlug("ab")).isEqualTo("AB");
    }

    @Test
    void prefix_keepsDigits() {
        assertThat(DocumentCodes.prefixFromSlug("7-sabores")).isEqualTo("7SAB");
    }

    @Test
    void prefix_ofAnEmptyOrSymbolOnlySlug_usesTheFallback() {
        assertThat(DocumentCodes.prefixFromSlug("---")).isEqualTo("EMBR");
        assertThat(DocumentCodes.prefixFromSlug(null)).isEqualTo("EMBR");
    }

    @Test
    void billCode_isPrefixAndSixDigitZeroPaddedNumber() {
        assertThat(DocumentSeries.BILL.format("ELPO", 123)).isEqualTo("ELPO-000123");
    }

    @Test
    void kitchenCode_carriesTheKdsMarker() {
        assertThat(DocumentSeries.KDS.format("ELPO", 45)).isEqualTo("ELPO-KDS-000045");
    }

    @Test
    void code_growsPastSixDigitsWithoutTruncating() {
        assertThat(DocumentSeries.BILL.format("ELPO", 1_234_567)).isEqualTo("ELPO-1234567");
    }
}
