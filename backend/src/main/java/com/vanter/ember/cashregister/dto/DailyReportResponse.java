package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportResponse(
        LocalDate date,
        BigDecimal totalCashSales,
        BigDecimal totalDigitalSales,
        BigDecimal totalVariance,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        List<CashShiftResponse> shifts) {}
