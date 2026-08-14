package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The admin dashboard's product-performance (Pareto / top-selling) view for one tenant.
 *
 * <p>Both lists are ordered by revenue, highest first, so the products list reads straight into a
 * Pareto chart via {@link ProductPerformance#cumulativeShare()}. {@code productCount} is the size of
 * the FULL product set: when the client sends a {@code limit}, {@code products} is truncated but the
 * totals and every share still describe the whole window.
 *
 * <p>{@code totalRevenue} is line-item money — the sum of what each item was sold for — and is
 * therefore not the same figure as {@code /admin/analytics/summary}'s payment-derived revenue: taxes,
 * tips and rounding live on the bill, not on the items.
 */
public record AnalyticsProductsResponse(
        LocalDateTime from,
        LocalDateTime to,
        BigDecimal totalRevenue,
        long totalQuantity,
        int productCount,
        List<ProductPerformance> products,
        List<CategoryPerformance> categories) {}
