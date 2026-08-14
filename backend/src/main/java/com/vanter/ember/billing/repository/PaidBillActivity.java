package com.vanter.ember.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One {@code PAID} bill inside an analytics window — the session it settled, what it totalled, and
 * when. Table analytics joins this against the Mongo {@code Session} (for its {@code tableId} and
 * {@code createdAt}) to attribute revenue, turnovers and session duration to a table.
 */
public record PaidBillActivity(String sessionId, BigDecimal total, LocalDateTime createdAt) {}
