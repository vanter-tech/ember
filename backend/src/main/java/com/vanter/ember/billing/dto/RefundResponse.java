package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long id, BigDecimal amount, String reason, String refundedByName, LocalDateTime createdAt) {}
