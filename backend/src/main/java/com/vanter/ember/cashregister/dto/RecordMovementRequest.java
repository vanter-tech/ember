package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecordMovementRequest(
        @NotNull CashMovementType type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String reason) {}
