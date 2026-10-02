package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One refund of a payment taken in a shift: what came back, why, who issued it and for which bill/participant. */
public record ShiftRefundResponse(
        Long id,
        Long paymentId,
        Long billId,
        /** Printable bill code ({@code ELPO-000123}); null for a bill issued before numbering existed. */
        String billCode,
        String participantName,
        BigDecimal amount,
        String reason,
        String refundedByName,
        LocalDateTime createdAt) {}
