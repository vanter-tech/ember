package com.vanter.ember.kitchen.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.kitchen.dto.KitchenDisplayEntry;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.OrderItemAdded;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitchenService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<KitchenOrder> findAll() {
        return kitchenOrderRepository.findAll();
    }

    public List<KitchenDisplayEntry> findDisplay() {
        return kitchenOrderRepository.findAll().stream()
                .collect(Collectors.groupingBy(KitchenOrder::getTableNumber))
                .entrySet().stream()
                .sorted(Comparator.comparingInt(java.util.Map.Entry::getKey))
                .map(e -> new KitchenDisplayEntry(
                        e.getKey(),
                        e.getValue().stream()
                                .sorted(Comparator.comparing(KitchenOrder::getCreatedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder())))
                                .toList()))
                .toList();
    }

    public KitchenOrder findBySessionId(String sessionId) {
        return kitchenOrderRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found for session: " + sessionId));
    }

    @EventListener
    public void handleOrderItemAdded(OrderItemAdded event) {
        KitchenOrder order = kitchenOrderRepository.findBySessionId(event.sessionId())
                .orElseGet(() -> KitchenOrder.builder()
                        .sessionId(event.sessionId())
                        .tableNumber(event.tableNumber())
                        .createdAt(LocalDateTime.now())
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

    public KitchenOrder updateItemStatus(String orderId, String itemId, OrderItemStatus newStatus) {
        KitchenOrder order = kitchenOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found: " + orderId));

        KitchenItem item = order.getItems().stream()
                .filter(i -> itemId.equals(i.getItemId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

        if (!isValidTransition(item.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    "Invalid status transition: " + item.getStatus() + " → " + newStatus);
        }

        item.setStatus(newStatus);
        item.setUpdatedAt(LocalDateTime.now());
        KitchenOrder saved = kitchenOrderRepository.save(order);
        eventPublisher.publishEvent(new KitchenItemUpdated(saved.getSessionId(), itemId, newStatus));
        return saved;
    }

    private boolean isValidTransition(OrderItemStatus current, OrderItemStatus next) {
        return switch (current) {
            case PENDING -> next == OrderItemStatus.PREPARING;
            case PREPARING -> next == OrderItemStatus.READY;
            case READY -> next == OrderItemStatus.DELIVERED;
            case DELIVERED -> false;
        };
    }
}
