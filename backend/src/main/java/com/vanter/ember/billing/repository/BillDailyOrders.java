package com.vanter.ember.billing.repository;

import java.time.LocalDate;

/**
 * How many bills a tenant settled on one calendar day. Carries the date components for the same
 * reason as {@link PaymentDailyRevenue}, and is rolled up into coarser buckets in the service.
 */
public record BillDailyOrders(Integer year, Integer month, Integer day, Long billCount) {

    public LocalDate date() {
        return LocalDate.of(year, month, day);
    }
}
