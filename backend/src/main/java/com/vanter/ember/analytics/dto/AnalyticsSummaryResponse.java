package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The admin dashboard's summary cards for one tenant.
 *
 * <p>{@code totalRevenue} is money actually collected (confirmed payments) inside
 * {@code from}..{@code to}, while {@code averageOrderValue} is the mean total of the bills settled
 * in that same window ({@code salesTotal / paidBillCount}, 2dp). The two are deliberately different
 * measures: a partially-paid bill moves revenue but not the average, so they can disagree.
 *
 * <p>{@code activeSessions} is a LIVE count of currently open table sessions — it ignores the window
 * entirely, because "how full is the restaurant right now" has no meaning inside a past date range.
 *
 * <p>{@code from}/{@code to} echo the window actually applied, including the defaults the service
 * substituted when the client sent none.
 */
public record AnalyticsSummaryResponse(
        BigDecimal totalRevenue,
        long activeSessions,
        BigDecimal averageOrderValue,
        long paidBillCount,
        LocalDateTime from,
        LocalDateTime to) {}
