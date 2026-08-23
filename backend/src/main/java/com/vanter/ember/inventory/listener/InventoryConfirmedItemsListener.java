package com.vanter.ember.inventory.listener;

import com.vanter.ember.inventory.repository.InventoryItemRepository;
import com.vanter.ember.inventory.service.InventoryService;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.model.OrderItem;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryConfirmedItemsListener {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryService inventoryService;

    @EventListener
    public void onKitchenItemsConfirmed(KitchenItemsConfirmed event) {
        Map<Long, Long> quantityByMenuItemId = event.confirmedItems().stream()
                .collect(Collectors.groupingBy(OrderItem::getItemId, Collectors.counting()));

        quantityByMenuItemId.forEach((menuItemId, quantity) ->
                inventoryItemRepository.findByMenuItemId(menuItemId).ifPresent(item ->
                        inventoryService.applyDelta(item.getId(), BigDecimal.valueOf(-quantity))));
    }
}
