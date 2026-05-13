package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PhysicalPaymentRequest(
        @NotNull Long billId,
        @NotBlank String participantName,
        @NotNull BigDecimal amount) {}
