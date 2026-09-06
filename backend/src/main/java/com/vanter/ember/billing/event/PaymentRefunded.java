package com.vanter.ember.billing.event;

import java.math.BigDecimal;

/**
 * Published by {@code PaymentService#refundPayment} once a {@code Refund} row is persisted, so
 * downstream concerns can undo what the matching payment triggered. Today the one listener is
 * loyalty: points accrued for this participant at {@code BILL_SETTLED} are clawed back in
 * proportion to {@code refundAmount}.
 *
 * <p>{@code refundAmount} is this single refund's amount, not the running total refunded against
 * the payment.
 */
public record PaymentRefunded(
        String sessionId, Long billId, String participantName, BigDecimal refundAmount) {}
