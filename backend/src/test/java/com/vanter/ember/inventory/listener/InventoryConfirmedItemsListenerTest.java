package com.vanter.ember.inventory.listener;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.inventory.model.InventoryItem;
import com.vanter.ember.inventory.repository.InventoryItemRepository;
import com.vanter.ember.inventory.service.InventoryService;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.model.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryConfirmedItemsListenerTest {

    @Mock InventoryItemRepository inventoryItemRepository;
    @Mock InventoryService inventoryService;
    @InjectMocks InventoryConfirmedItemsListener listener;

    private OrderItem orderItemFor(Long menuItemId) {
        return OrderItem.builder().itemId(menuItemId).name("Item " + menuItemId).build();
    }

    @Test
    void onKitchenItemsConfirmed_duplicateItemId_decrementsByOccurrenceCount() {
        InventoryItem tracked = InventoryItem.builder().id(99L).menuItemId(10L).build();
        when(inventoryItemRepository.findByMenuItemId(10L)).thenReturn(Optional.of(tracked));

        listener.onKitchenItemsConfirmed(new KitchenItemsConfirmed(
                UUID.randomUUID(), "sess-1", 4,
                List.of(orderItemFor(10L), orderItemFor(10L))));

        verify(inventoryService).applyDelta(99L, new BigDecimal("-2"));
    }

    @Test
    void onKitchenItemsConfirmed_untrackedMenuItem_skippedSilently() {
        when(inventoryItemRepository.findByMenuItemId(20L)).thenReturn(Optional.empty());

        listener.onKitchenItemsConfirmed(new KitchenItemsConfirmed(
                UUID.randomUUID(), "sess-1", 4, List.of(orderItemFor(20L))));

        verify(inventoryService, never()).applyDelta(eq(20L), org.mockito.ArgumentMatchers.any());
    }
}
