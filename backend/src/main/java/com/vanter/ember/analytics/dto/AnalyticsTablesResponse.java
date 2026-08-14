package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The admin dashboard's table-performance view for one tenant: turnover, revenue and average
 * session duration per table, ordered by revenue, highest first.
 *
 * <p>{@code activeTableCount} is a LIVE count of the tenant's currently active tables — like
 * {@code /admin/analytics/summary}'s {@code activeSessions}, it deliberately ignores {@code
 * from}/{@code to}, so it and {@code averageTurnoverRate} can move even when the window is fixed.
 * {@code averageTurnoverRate} is {@code totalTurnovers / activeTableCount} (2dp), {@code 0} when
 * there are no active tables.
 *
 * <p>{@code tables} only lists tables that turned over at least once inside the window — a table
 * that sat empty the whole time contributes nothing to report.
 */
public record AnalyticsTablesResponse(
        LocalDateTime from,
        LocalDateTime to,
        long activeTableCount,
        long totalTurnovers,
        BigDecimal totalRevenue,
        BigDecimal averageTurnoverRate,
        BigDecimal averageSessionDurationMinutes,
        List<TablePerformance> tables) {}
