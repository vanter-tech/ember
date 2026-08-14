package com.vanter.ember.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point of the temporal-sales series. {@code bucketStart}/{@code bucketEnd} are both inclusive
 * calendar dates delimiting the bucket, so a client can label the axis without re-deriving week or
 * month boundaries.
 *
 * <p>{@code revenue} is confirmed-payment money and {@code paidBillCount} counts settled bills, the
 * same two measures the summary cards use — a bucket can therefore carry revenue with no paid bill
 * (a partially-paid table) or the reverse (a bill settled the day after its payments landed).
 */
public record SalesBucket(
        LocalDate bucketStart, LocalDate bucketEnd, BigDecimal revenue, long paidBillCount) {}
