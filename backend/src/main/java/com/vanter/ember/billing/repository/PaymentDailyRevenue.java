package com.vanter.ember.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calendar day of confirmed revenue for a tenant. The date is carried as its three components
 * because {@code year()}/{@code month()}/{@code day()} are the widest-supported way to group a
 * timestamp column in JPQL — {@code date_trunc} spells differently on H2 and PostgreSQL.
 *
 * <p>Day is the finest bucket the temporal-sales endpoint offers, so coarser granularities are
 * rolled up from these rows in the service rather than re-queried.
 */
public record PaymentDailyRevenue(Integer year, Integer month, Integer day, BigDecimal revenue) {

    public LocalDate date() {
        return LocalDate.of(year, month, day);
    }
}
