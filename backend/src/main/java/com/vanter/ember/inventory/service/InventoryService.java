package com.vanter.ember.inventory.service;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.inventory.dto.InventoryItemRequest;
import com.vanter.ember.inventory.dto.InventoryItemResponse;
import com.vanter.ember.inventory.dto.InventoryItemUpdateRequest;
import com.vanter.ember.inventory.dto.LowStockAlertMessage;
import com.vanter.ember.inventory.model.InventoryItem;
import com.vanter.ember.inventory.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    public List<InventoryItemResponse> findAll() {
        return inventoryItemRepository.findAll().stream()
                .map(item -> InventoryItemResponse.from(item, requireMenuItem(item.getMenuItemId())))
                .toList();
    }

    public InventoryItemResponse create(InventoryItemRequest request) {
        MenuItem menuItem = requireMenuItem(request.getMenuItemId());
        if (inventoryItemRepository.findByMenuItemId(request.getMenuItemId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Menu item already has inventory tracking: " + request.getMenuItemId());
        }

        InventoryItem item = InventoryItem.builder()
                .menuItemId(request.getMenuItemId())
                .unit(request.getUnit())
                .currentStock(request.getCurrentStock())
                .lowStockThreshold(request.getLowStockThreshold())
                .updatedAt(LocalDateTime.now())
                .build();
        InventoryItem saved = inventoryItemRepository.save(item);
        applyStockSideEffects(saved);
        return InventoryItemResponse.from(saved, requireMenuItem(saved.getMenuItemId()));
    }

    public InventoryItemResponse update(Long id, InventoryItemUpdateRequest request) {
        InventoryItem item = findEntityById(id);
        item.setUnit(request.getUnit());
        item.setLowStockThreshold(request.getLowStockThreshold());
        InventoryItem saved = inventoryItemRepository.save(item);
        return InventoryItemResponse.from(saved, requireMenuItem(saved.getMenuItemId()));
    }

    public InventoryItemResponse restock(Long id, BigDecimal delta) {
        InventoryItem item = applyDelta(id, delta);
        return InventoryItemResponse.from(item, requireMenuItem(item.getMenuItemId()));
    }

    public void delete(Long id) {
        InventoryItem item = findEntityById(id);
        inventoryItemRepository.delete(item);
    }

    private MenuItem requireMenuItem(Long menuItemId) {
        return menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + menuItemId));
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
