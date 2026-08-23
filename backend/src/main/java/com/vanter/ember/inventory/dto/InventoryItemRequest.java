package com.vanter.ember.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class InventoryItemRequest {

    @NotNull(message = "Menu item is required")
    private Long menuItemId;

    @NotBlank(message = "Unit is required")
    private String unit;

    @NotNull(message = "Initial stock is required")
    @DecimalMin(value = "0", message = "Initial stock cannot be negative")
    private BigDecimal currentStock;

    @NotNull(message = "Low stock threshold is required")
    @DecimalMin(value = "0", message = "Low stock threshold cannot be negative")
    private BigDecimal lowStockThreshold;
}
