package com.vanter.ember.inventory.dto;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.inventory.model.InventoryItem;
import java.math.BigDecimal;

public record LowStockAlertMessage(
        String type,
        Long menuItemId,
        String menuItemName,
        BigDecimal currentStock,
        String unit,
        BigDecimal threshold) {

    public static LowStockAlertMessage of(InventoryItem item, MenuItem menuItem) {
        return new LowStockAlertMessage(
                "LOW_STOCK",
                menuItem.getId(),
                menuItem.getName(),
                item.getCurrentStock(),
                item.getUnit(),
                item.getLowStockThreshold());
    }
}
