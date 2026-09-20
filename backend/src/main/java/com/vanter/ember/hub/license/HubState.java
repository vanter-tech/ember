package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

/**
 * Local, Postgres-independent Hub state — has to be readable even when Postgres won't start.
 * {@code lastSeenAt} is the newest local clock reading ever recorded: it only moves forward, so a
 * clock set back behind it is detectable ({@link LicenseService#isClockRolledBack}).
 */
public record HubState(
        String hardwareFingerprint,
        UUID restaurantId,
        Instant lastHeartbeatAt,
        Instant suspendedSince,
        Instant lastSeenAt) {

    /** Back-compat constructor for the common "no suspension" case. */
    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, null, null);
    }

    public HubState(
            String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt, Instant suspendedSince) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, null);
    }

    /**
     * @param clockAgreesWithCloud the signed cloud time matched the local clock, so {@code
     *     lastSeenAt} may be reset to now (this is what clears a rollback flag after the user fixes
     *     a wrong clock); otherwise it only ever advances.
     */
    public HubState withHeartbeatNow(boolean clockAgreesWithCloud) {
        Instant now = Instant.now();
        Instant seen = clockAgreesWithCloud ? now : latest(lastSeenAt, now);
        return new HubState(hardwareFingerprint, restaurantId, now, null, seen);
    }

    public HubState withSuspendedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, when, lastSeenAt);
    }

    public HubState withLastHeartbeatAt(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, when, suspendedSince, lastSeenAt);
    }

    public HubState withLastSeenAtAdvancedTo(Instant now) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince,
                latest(lastSeenAt, now));
    }

    private static Instant latest(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}
