package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.DenominationCount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record OpenShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingFloat, List<DenominationCount> breakdown) {}
