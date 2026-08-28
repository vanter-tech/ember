package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

/** Local, Postgres-independent Hub state — has to be readable even when Postgres won't start. */
public record HubState(
        String hardwareFingerprint,
        UUID restaurantId,
        Instant lastHeartbeatAt,
        Instant suspendedSince) {

    /** Back-compat constructor for the common "no suspension" case. */
    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, null);
    }

    public HubState withHeartbeatNow() {
        return new HubState(hardwareFingerprint, restaurantId, Instant.now(), null);
    }

    public HubState withSuspendedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, when);
    }
}
