package com.vanter.ember.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.catalog.model.MenuItem;
import com.vanter.ember.catalog.repository.MenuItemRepository;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.inventory.dto.LowStockAlertMessage;
import com.vanter.ember.inventory.model.InventoryItem;
import com.vanter.ember.inventory.repository.InventoryItemRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryItemRepository inventoryItemRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks InventoryService inventoryService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final Long INVENTORY_ITEM_ID = 1L;
    private static final Long MENU_ITEM_ID = 10L;

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private InventoryItem itemWith(BigDecimal currentStock, BigDecimal threshold) {
        return InventoryItem.builder()
                .id(INVENTORY_ITEM_ID)
                .menuItemId(MENU_ITEM_ID)
                .unit("kg")
                .currentStock(currentStock)
                .lowStockThreshold(threshold)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private MenuItem menuItemAvailable(boolean available) {
        MenuItem menuItem = new MenuItem();
        menuItem.setId(MENU_ITEM_ID);
        menuItem.setName("Salmón a la parrilla");
        menuItem.setAvailable(available);
        return menuItem;
    }

    @Test
    void applyDelta_stockReachesZero_autoDisablesMenuItem() {
        when(inventoryItemRepository.findById(INVENTORY_ITEM_ID))
                .thenReturn(Optional.of(itemWith(BigDecimal.ZERO, new BigDecimal("2"))));
        MenuItem menuItem = menuItemAvailable(true);
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(menuItem));

        inventoryService.applyDelta(INVENTORY_ITEM_ID, new BigDecimal("-1"));

        verify(inventoryItemRepository).applyClampedDelta(eq(INVENTORY_ITEM_ID), eq(new BigDecimal("-1")), any());
        assertThat(menuItem.isAvailable()).isFalse();
        verify(menuItemRepository).save(menuItem);
    }

    @Test
    void applyDelta_stockAlreadyZero_menuItemAlreadyUnavailable_noSave() {
        when(inventoryItemRepository.findById(INVENTORY_ITEM_ID))
                .thenReturn(Optional.of(itemWith(BigDecimal.ZERO, new BigDecimal("2"))));
        MenuItem menuItem = menuItemAvailable(false);
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(menuItem));

        inventoryService.applyDelta(INVENTORY_ITEM_ID, new BigDecimal("-1"));

        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void applyDelta_stockRisesAboveZero_autoReEnablesMenuItem() {
        when(inventoryItemRepository.findById(INVENTORY_ITEM_ID))
                .thenReturn(Optional.of(itemWith(new BigDecimal("5"), new BigDecimal("2"))));
        MenuItem menuItem = menuItemAvailable(false);
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(menuItem));

        inventoryService.applyDelta(INVENTORY_ITEM_ID, new BigDecimal("5"));

        assertThat(menuItem.isAvailable()).isTrue();
        verify(menuItemRepository).save(menuItem);
    }

    @Test
    void applyDelta_stockAtOrBelowThreshold_broadcastsLowStock() {
        when(inventoryItemRepository.findById(INVENTORY_ITEM_ID))
                .thenReturn(Optional.of(itemWith(new BigDecimal("2"), new BigDecimal("2"))));
        MenuItem menuItem = menuItemAvailable(true);
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(menuItem));

        inventoryService.applyDelta(INVENTORY_ITEM_ID, new BigDecimal("-1"));

        verify(messagingTemplate).convertAndSend(
                eq("/topic/inventory/" + TENANT_ID), any(LowStockAlertMessage.class));
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void applyDelta_stockAboveThreshold_noBroadcastNoAvailabilityChange() {
        when(inventoryItemRepository.findById(INVENTORY_ITEM_ID))
                .thenReturn(Optional.of(itemWith(new BigDecimal("8"), new BigDecimal("2"))));
        MenuItem menuItem = menuItemAvailable(true);
        when(menuItemRepository.findById(MENU_ITEM_ID)).thenReturn(Optional.of(menuItem));

        inventoryService.applyDelta(INVENTORY_ITEM_ID, new BigDecimal("-1"));

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        verify(menuItemRepository, never()).save(any());
    }
}
