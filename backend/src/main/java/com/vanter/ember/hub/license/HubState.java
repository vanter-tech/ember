package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

/** Local, Postgres-independent Hub state — has to be readable even when Postgres won't start. */
public record HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {}
