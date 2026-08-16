package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashShiftResponse(
        Long id,
        int shiftNumber,
        String status,
        BigDecimal openingFloat,
        String openedByName,
        LocalDateTime openedAt,
        String closedByName,
        LocalDateTime closedAt,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal totalCashSales,
        BigDecimal totalDigitalSales,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut) {}
