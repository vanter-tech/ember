package com.vanter.ember.hub.license;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Local, Postgres-independent Hub state — has to be readable even when Postgres won't start.
 * {@code lastSeenAt} is the newest local clock reading ever recorded: it only moves forward, so a
 * clock set back behind it is detectable ({@link LicenseService#isClockRolledBack}).
 * {@code migratedSince} is when the cloud first reported MIGRATED (the restaurant moved to Ember Web);
 * omitted from the JSON while null so files read by older Hubs keep their shape.
 */
public record HubState(
        String hardwareFingerprint,
        UUID restaurantId,
        Instant lastHeartbeatAt,
        Instant suspendedSince,
        Instant lastSeenAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant migratedSince) {

    /** Back-compat constructor for the common "no suspension" case. */
    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, null, null, null);
    }

    public HubState(
            String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt, Instant suspendedSince) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, null, null);
    }

    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt,
                    Instant suspendedSince, Instant lastSeenAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, lastSeenAt, null);
    }

    /**
     * @param clockAgreesWithCloud the signed cloud time matched the local clock, so {@code
     *     lastSeenAt} may be reset to now (this is what clears a rollback flag after the user fixes
     *     a wrong clock); otherwise it only ever advances.
     *     A successful (OK) heartbeat also clears both a suspension and a migration.
     */
    public HubState withHeartbeatNow(boolean clockAgreesWithCloud) {
        Instant now = Instant.now();
        Instant seen = clockAgreesWithCloud ? now : latest(lastSeenAt, now);
        return new HubState(hardwareFingerprint, restaurantId, now, null, seen, null);
    }

    public HubState withSuspendedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, when, lastSeenAt, migratedSince);
    }

    public HubState withMigratedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince, lastSeenAt, when);
    }

    public HubState withLastHeartbeatAt(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, when, suspendedSince, lastSeenAt, migratedSince);
    }

    public HubState withLastSeenAtAdvancedTo(Instant now) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, suspendedSince,
                latest(lastSeenAt, now), migratedSince);
    }

    private static Instant latest(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}
