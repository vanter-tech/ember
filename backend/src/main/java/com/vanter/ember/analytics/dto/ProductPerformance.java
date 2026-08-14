package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;

/**
 * One menu item's share of the tenant's settled sales.
 *
 * <p>{@code quantitySold} counts line items — an order item is always one unit — and {@code revenue}
 * sums the price each was sold at, not the item's current catalogue price.
 *
 * <p>{@code revenueShare} is this item's percentage of the window's item revenue; {@code
 * cumulativeShare} is the running total of that percentage down the revenue-ordered list, which is
 * what makes the response plottable as a Pareto curve. Both are computed from unrounded money over
 * the FULL product set, so a truncated (top-N) list still reports true shares.
 *
 * <p>{@code itemId}/{@code categoryId}/{@code categoryName} are null for a line item whose menu item
 * no longer exists in the catalogue; {@code name} then falls back to the name stored on the order.
 */
public record ProductPerformance(
        Long itemId,
        String name,
        Long categoryId,
        String categoryName,
        long quantitySold,
        BigDecimal revenue,
        BigDecimal revenueShare,
        BigDecimal cumulativeShare) {}
