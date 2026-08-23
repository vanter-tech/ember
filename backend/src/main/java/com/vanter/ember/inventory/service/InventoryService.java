package com.vanter.ember.inventory.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.inventory.dto.LowStockAlertMessage;
import com.vanter.ember.inventory.model.InventoryItem;
import com.vanter.ember.inventory.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public InventoryItem applyDelta(Long inventoryItemId, BigDecimal delta) {
        inventoryItemRepository.applyClampedDelta(inventoryItemId, delta, LocalDateTime.now());
        InventoryItem item = findEntityById(inventoryItemId);
        applyStockSideEffects(item);
        return item;
    }

    private void applyStockSideEffects(InventoryItem item) {
        MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + item.getMenuItemId()));

        if (item.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0) {
            if (menuItem.isAvailable()) {
                menuItem.setAvailable(false);
                menuItemRepository.save(menuItem);
            }
        } else if (!menuItem.isAvailable()) {
            menuItem.setAvailable(true);
            menuItemRepository.save(menuItem);
        } else if (item.getCurrentStock().compareTo(item.getLowStockThreshold()) <= 0) {
            broadcastLowStock(item, menuItem);
        }
    }

    private void broadcastLowStock(InventoryItem item, MenuItem menuItem) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        messagingTemplate.convertAndSend(
                "/topic/inventory/" + tenantId, LowStockAlertMessage.of(item, menuItem));
    }

    private InventoryItem findEntityById(Long id) {
        return inventoryItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
    }
}
