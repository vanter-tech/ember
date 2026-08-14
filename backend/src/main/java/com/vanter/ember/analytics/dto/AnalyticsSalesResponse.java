package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The admin dashboard's temporal sales chart for one tenant.
 *
 * <p>{@code buckets} is a gap-free series ordered oldest-first: quiet periods appear as zero-valued
 * buckets so a chart can plot it directly. It runs from the client's {@code from} — or, when none
 * was sent, from the first bucket that actually saw activity — through {@code to}. Defaulting the
 * open-ended case to the first active bucket rather than to the epoch is deliberate: the summary
 * endpoint's epoch floor is harmless for a single sum, but here it would emit twenty thousand empty
 * daily buckets.
 *
 * <p>{@code totalRevenue}/{@code paidBillCount} are the series totals, and match what
 * {@code /admin/analytics/summary} reports for the same window.
 */
public record AnalyticsSalesResponse(
        SalesGranularity granularity,
        LocalDateTime from,
        LocalDateTime to,
        BigDecimal totalRevenue,
        long paidBillCount,
        List<SalesBucket> buckets) {}
