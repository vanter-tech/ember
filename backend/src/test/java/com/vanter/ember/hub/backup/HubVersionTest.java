package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HubVersionTest {

    @Test
    void newerPatchIsNewer() {
        assertThat(HubVersion.isNewer("0.2.7", "0.2.6")).isTrue();
    }

    @Test
    void fourthComponentCounts() {
        assertThat(HubVersion.isNewer("0.2.6.1", "0.2.6")).isTrue();
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6.1")).isFalse();
    }

    @Test
    void numericNotLexicographic() {
        assertThat(HubVersion.isNewer("0.10.0", "0.9.0")).isTrue();
    }

    @Test
    void equalIsNotNewer() {
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6")).isFalse();
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6.0")).isFalse();
    }

    @Test
    void unknownOrUnparseableNeverBlocks() {
        assertThat(HubVersion.isNewer(null, "0.2.6")).isFalse();
        assertThat(HubVersion.isNewer("0.2.6", null)).isFalse();
        assertThat(HubVersion.isNewer("abc", "0.2.6")).isFalse();
    }
}
