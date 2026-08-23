package com.vanter.ember.inventory.dto;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.inventory.model.InventoryItem;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemResponse {

    private Long id;
    private Long menuItemId;
    private String menuItemName;
    private boolean menuItemAvailable;
    private String unit;
    private BigDecimal currentStock;
    private BigDecimal lowStockThreshold;
    private LocalDateTime updatedAt;

    public static InventoryItemResponse from(InventoryItem item, MenuItem menuItem) {
        return InventoryItemResponse.builder()
                .id(item.getId())
                .menuItemId(item.getMenuItemId())
                .menuItemName(menuItem.getName())
                .menuItemAvailable(menuItem.isAvailable())
                .unit(item.getUnit())
                .currentStock(item.getCurrentStock())
                .lowStockThreshold(item.getLowStockThreshold())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
