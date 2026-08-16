package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;

/**
 * One catalogue category's share of the tenant's settled sales, rolled up from the same line items
 * behind {@link ProductPerformance} and ordered the same way (revenue first).
 *
 * <p>{@code categoryId}/{@code name} are null for the bucket collecting items whose menu item was
 * deleted from the catalogue — those sales still happened, so they are reported rather than dropped.
 */
public record CategoryPerformance(
        Long categoryId,
        String name,
        long quantitySold,
        BigDecimal revenue,
        BigDecimal revenueShare) {}
