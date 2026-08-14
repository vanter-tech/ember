package com.vanter.ember.billing.repository;

import java.math.BigDecimal;

/**
 * Aggregate projection over {@code bills} — how many bills were settled in a window and what they
 * summed to. {@code salesTotal} is null when the window holds no bills.
 */
public record BillSalesTotals(Long billCount, BigDecimal salesTotal) {}
