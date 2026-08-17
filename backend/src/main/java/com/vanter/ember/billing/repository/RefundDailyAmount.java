package com.vanter.ember.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calendar day of refunds for a tenant — mirrors {@link PaymentDailyRevenue}'s shape so
 * {@code AnalyticsService} can net the two together bucket-by-bucket.
 */
public record RefundDailyAmount(Integer year, Integer month, Integer day, BigDecimal amount) {

    public LocalDate date() {
        return LocalDate.of(year, month, day);
    }
}
