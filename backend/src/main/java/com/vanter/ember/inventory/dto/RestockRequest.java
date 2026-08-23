package com.vanter.ember.inventory.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class RestockRequest {

    @NotNull(message = "Delta is required")
    private BigDecimal delta;
}
