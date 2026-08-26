package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

public record LicenseKey(UUID restaurantId, Instant issuedAt) {}
