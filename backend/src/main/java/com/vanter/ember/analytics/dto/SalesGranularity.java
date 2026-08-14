package com.vanter.ember.analytics.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * How the temporal-sales series is bucketed. Weeks are ISO-8601 (Monday-start) and are computed in
 * Java rather than by the database, whose week semantics differ per vendor.
 */
public enum SalesGranularity {
    DAY,
    WEEK,
    MONTH,
    YEAR;

    /**
     * Parses the {@code granularity} request parameter, which clients send lowercase.
     *
     * @throws IllegalArgumentException on an unknown value, naming the accepted ones — silently
     *     falling back to DAY would hand the caller a chart that answers a different question.
     */
    public static SalesGranularity from(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown analytics granularity '" + value + "'; expected day, week, month or year");
        }
    }

    /** The first day of the bucket {@code date} falls in — the series key. */
    public LocalDate bucketStart(LocalDate date) {
        return switch (this) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
            case YEAR -> date.withDayOfYear(1);
        };
    }

    /** The last day covered by the bucket starting at {@code bucketStart}, inclusive. */
    public LocalDate bucketEnd(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart;
            case WEEK -> bucketStart.plusDays(6);
            case MONTH -> bucketStart.with(TemporalAdjusters.lastDayOfMonth());
            case YEAR -> bucketStart.with(TemporalAdjusters.lastDayOfYear());
        };
    }

    /** The start of the bucket immediately after the one starting at {@code bucketStart}. */
    public LocalDate next(LocalDate bucketStart) {
        return switch (this) {
            case DAY -> bucketStart.plusDays(1);
            case WEEK -> bucketStart.plusWeeks(1);
            case MONTH -> bucketStart.plusMonths(1);
            case YEAR -> bucketStart.plusYears(1);
        };
    }
}
