package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A bill voided before any payment: kept in the series on purpose, so the audit shows who cancelled it and why. */
public record VoidedBillResponse(
        Long id,
        /** Printable bill code ({@code ELPO-000123}); null for a bill issued before numbering existed. */
        String billCode,
        BigDecimal total,
        Integer tableNumber,
        /** {@code M3+M4} when the table was merged with others, otherwise null. */
        String tableLabel,
        String voidReason,
        String voidedByName,
        LocalDateTime voidedAt) {}
