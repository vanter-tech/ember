package com.vanter.ember.cashregister.model;

/**
 * How many of one denomination were counted. Persisted as a JSON array on
 * {@link CashShift#getOpeningBreakdown()}/{@link CashShift#getClosingBreakdown()} — {@code
 * denominationId} refers to {@link NicaraguaDenominations#byId(String)}, the face value is never
 * stored here so it can't drift from the catalog.
 */
public record DenominationCount(String denominationId, int quantity) {}
