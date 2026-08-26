package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HardwareFingerprintServiceTest {

    @Test
    void currentFingerprint_isStableAcrossCalls() {
        HardwareFingerprintService service = new HardwareFingerprintService();

        String first = service.currentFingerprint();
        String second = service.currentFingerprint();

        assertThat(first).isNotBlank();
        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[0-9a-f]{64}");
    }
}
