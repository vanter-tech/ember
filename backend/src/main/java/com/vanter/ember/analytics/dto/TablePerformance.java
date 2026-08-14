package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One dining table's activity inside the reporting window.
 *
 * <p>{@code turnoverCount} is how many {@code PAID} bills settled at this table — the same
 * "settled bill is a sale" rule every analytics read shares. {@code averageSessionDurationMinutes}
 * is the mean time between a session opening and its bill settling, in minutes, over only the
 * turnovers where both instants were available; it is {@code null} when none were.
 *
 * <p>{@code tableNumber} is {@code null} for a table that has since been deleted — it still keeps
 * the revenue it earned while it existed, the same way a deleted menu item keeps the name it was
 * sold under.
 */
public record TablePerformance(
        UUID tableId,
        Integer tableNumber,
        long turnoverCount,
        BigDecimal revenue,
        BigDecimal revenueShare,
        BigDecimal averageSessionDurationMinutes) {}
