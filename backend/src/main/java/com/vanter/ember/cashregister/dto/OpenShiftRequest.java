package com.vanter.ember.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OpenShiftRequest(@NotNull @DecimalMin("0.00") BigDecimal openingFloat) {}
