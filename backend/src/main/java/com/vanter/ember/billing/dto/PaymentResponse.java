package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long billId,
        String participantName,
        BigDecimal amount,
        String method,
        String status,
        LocalDateTime createdAt,
        BigDecimal refundedAmount,
        BigDecimal remaining) {}
