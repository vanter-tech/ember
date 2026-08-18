package com.vanter.ember.loyalty.model;

/** Fixed four-tier ladder, computed on read from {@link LoyaltyAccount#getTotalPoints()} against
 * the tenant's configured thresholds — never stored on the account itself. */
public enum LoyaltyTier {
    BRONCE,
    PLATA,
    ORO,
    PLATINO
}
