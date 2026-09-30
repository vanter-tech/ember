package com.vanter.ember.billing.event;

import java.math.BigDecimal;
import java.util.UUID;

/** One confirmed PHYSICAL (cash) payment — per split, unlike {@link PaymentCompleted} (whole bill). */
public record PhysicalPaymentRegistered(
        UUID tenantId, Long paymentId, Long billId, String sessionId,
        BigDecimal amount, Long cashShiftId, String processedBy) {}
