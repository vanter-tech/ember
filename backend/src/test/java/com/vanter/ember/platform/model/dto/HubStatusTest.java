package com.vanter.ember.platform.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HubStatusTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    @Test
    void nullLastHeartbeatIsNever() {
        assertThat(HubStatus.from(null, NOW)).isEqualTo(HubStatus.NEVER);
    }

    @Test
    void withinFifteenMinutesIsOnline() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofMinutes(14)), NOW)).isEqualTo(HubStatus.ONLINE);
    }

    @Test
    void betweenFifteenMinutesAndTwentyFourHoursIsStale() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofMinutes(20)), NOW)).isEqualTo(HubStatus.STALE);
        assertThat(HubStatus.from(NOW.minus(Duration.ofHours(23)), NOW)).isEqualTo(HubStatus.STALE);
    }

    @Test
    void olderThanTwentyFourHoursIsOffline() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofHours(25)), NOW)).isEqualTo(HubStatus.OFFLINE);
    }
}
