package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.DenominationCount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        BigDecimal totalCashOut,
        LocalDateTime expiresAt,
        LocalDateTime effectiveDeadline,
        boolean overdue,
        LocalDate businessDay,
        int prolongCount,
        List<DenominationCount> openingBreakdown,
        List<DenominationCount> closingBreakdown,
        String closeNotes) {}
