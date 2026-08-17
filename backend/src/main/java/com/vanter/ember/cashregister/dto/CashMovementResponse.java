package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashMovementResponse(
        Long id, String type, BigDecimal amount, String reason, String createdByName,
        LocalDateTime createdAt) {}
