package com.vanter.ember.kitchen.service;

import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.OrderItemAdded;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitchenService {

    private final KitchenOrderRepository kitchenOrderRepository;

    @EventListener
    public void handleOrderItemAdded(OrderItemAdded event) {
        KitchenOrder order = kitchenOrderRepository.findBySessionId(event.sessionId())
                .orElseGet(() -> KitchenOrder.builder()
                        .sessionId(event.sessionId())
                        .tableNumber(event.tableNumber())
                        .items(new ArrayList<>())
                        .build());

        order.getItems().add(KitchenItem.builder()
                .itemId(event.orderItemId())
                .name(event.itemName())
                .participantName(event.participantName())
                .status(OrderItemStatus.PENDING)
                .updatedAt(LocalDateTime.now())
                .build());

        kitchenOrderRepository.save(order);
    }
}
