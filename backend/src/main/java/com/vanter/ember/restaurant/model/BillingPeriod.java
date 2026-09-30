package com.vanter.ember.restaurant.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Length of the prepaid period a tenant's plan is billed in. */
public enum BillingPeriod {
    MONTHLY, SEMESTRAL, ANNUAL;

    /** The end of a period of this length that starts at {@code start}. Monthly is a flat 30 days. */
    public Instant endFrom(Instant start) {
        return switch (this) {
            case MONTHLY -> start.plus(30, ChronoUnit.DAYS);
            case SEMESTRAL -> start.atZone(ZoneOffset.UTC).plusMonths(6).toInstant();
            case ANNUAL -> start.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
        };
    }
}
