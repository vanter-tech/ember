package com.vanter.ember.platform.model.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Console-facing liveness of a customer's Hub, derived from
 * {@code hub_activations.last_heartbeat_at}. The Hub beats every 5 minutes, so ONLINE tolerates
 * two missed beats. Never persisted — computed on read.
 */
public enum HubStatus {
    NEVER, ONLINE, STALE, OFFLINE;

    private static final Duration ONLINE_WITHIN = Duration.ofMinutes(15);
    private static final Duration OFFLINE_AFTER = Duration.ofHours(24);

    public static HubStatus from(Instant lastHeartbeatAt, Instant now) {
        if (lastHeartbeatAt == null) {
            return NEVER;
        }
        Duration since = Duration.between(lastHeartbeatAt, now);
        if (since.compareTo(ONLINE_WITHIN) <= 0) {
            return ONLINE;
        }
        if (since.compareTo(OFFLINE_AFTER) <= 0) {
            return STALE;
        }
        return OFFLINE;
    }
}
