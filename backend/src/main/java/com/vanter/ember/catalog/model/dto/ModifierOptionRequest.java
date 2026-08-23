package com.vanter.ember.catalog.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ModifierOptionRequest {

    @NotBlank(message = "Option name is required")
    private String name;

    @NotNull(message = "Price delta is required")
    @DecimalMin(value = "0", message = "Price delta cannot be negative")
    private BigDecimal priceDelta;
}
