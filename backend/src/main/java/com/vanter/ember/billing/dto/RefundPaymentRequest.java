package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RefundPaymentRequest(@Positive BigDecimal amount, @NotBlank String reason) {}
