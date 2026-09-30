package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {@code drawer}: NONE | OPENING | OPENED | FAILED (derived from the linked kick print job) | SKIPPED
 * (the accountant dismissed a failure). {@code drawerError} is the failure cause, only when FAILED.
 */
public record CashDrawerEventResponse(
        UUID id, String type, String status, Integer tableNumber, BigDecimal amount, String reason,
        LocalDateTime createdAt, LocalDateTime receivedAt, String drawer, String createdByName,
        String drawerError) {}
