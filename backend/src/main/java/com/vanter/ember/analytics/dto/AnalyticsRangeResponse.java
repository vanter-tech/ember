package com.vanter.ember.analytics.dto;

import java.time.LocalDateTime;

/**
 * The window of billing activity a tenant actually has data for, so the admin dashboard can
 * bound its date pickers instead of offering ranges that can only come back empty.
 *
 * <p>{@code firstBillAt}/{@code lastBillAt} are null when the tenant has never been billed.
 */
public record AnalyticsRangeResponse(
        LocalDateTime firstBillAt, LocalDateTime lastBillAt, long billCount) {}
