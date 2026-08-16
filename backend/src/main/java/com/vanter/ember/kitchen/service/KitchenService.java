package com.vanter.ember.kitchen.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.kitchen.dto.KitchenDisplayEntry;
import com.vanter.ember.kitchen.event.KitchenItemRemoved;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.event.KitchenOrderRetired;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.DeleteItem;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.event.SessionClosed;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KitchenService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<KitchenOrder> findAll() {
        return kitchenOrderRepository.findByTenantId(TenantContextHolder.requireTenantId());
    }

    public Page<KitchenOrder> findAll(Pageable pageable) {
        return kitchenOrderRepository.findByTenantId(TenantContextHolder.requireTenantId(), pageable);
    }

    /**
     * Unpaginated: the kitchen display groups every open order by table, not a paged list.
     * Only {@code active} orders are shown — an order is retired (see
     * {@link #handleSessionClosed}) once its session closes, so tickets from long-ended
     * sessions don't linger on the live display.
     */
    public List<KitchenDisplayEntry> findDisplay() {
        return kitchenOrderRepository.findByTenantIdAndActiveTrue(TenantContextHolder.requireTenantId()).stream()
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
        return kitchenOrderRepository
                .findByTenantIdAndSessionId(TenantContextHolder.requireTenantId(), sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found for session: " + sessionId));
    }

    @EventListener
    public void handleOrderItemAdded(KitchenItemsConfirmed event) {
        KitchenOrder order = kitchenOrderRepository
                .findByTenantIdAndSessionId(event.tenantId(), event.sessionId())
                .orElseGet(() -> KitchenOrder.builder()
                        .tenantId(event.tenantId())
                        .sessionId(event.sessionId())
                        .tableNumber(event.tableNumber())
                        .createdAt(LocalDateTime.now())
                        .items(new ArrayList<>())
                        .active(true)
                        .build());

        order.setActive(true);
        event.confirmedItems().forEach(item -> {
            order.getItems().add(KitchenItem.builder()
                    .itemId(item.getId())
                    .name(item.getName())
                    .participantName(item.getParticipantName())
                    .status(OrderItemStatus.PENDING)
                    .updatedAt(LocalDateTime.now())
                    .build()
            );
        });

        kitchenOrderRepository.save(order);
    }

    public KitchenOrder updateItemStatus(String orderId, String itemId, OrderItemStatus newStatus) {
        KitchenOrder order = kitchenOrderRepository
                .findByIdAndTenantId(orderId, TenantContextHolder.requireTenantId())
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
        eventPublisher.publishEvent(new KitchenItemUpdated(
                TenantContextHolder.requireTenantId(), saved.getSessionId(), itemId, newStatus));
        return saved;
    }

    /**
     * Mirrors a waiter/customer item removal into the kitchen's own copy of the order — otherwise
     * a deleted item disappears from the session view but keeps showing on the live KDS forever.
     */
    @EventListener
    public void handleItemDeleted(DeleteItem event) {
        kitchenOrderRepository
                .findByTenantIdAndSessionId(TenantContextHolder.requireTenantId(), event.sessionId())
                .ifPresent(order -> {
                    boolean removed = order.getItems().removeIf(
                            item -> event.orderItemId().equals(item.getItemId()));
                    if (!removed) {
                        return;
                    }
                    if (order.getItems().isEmpty()) {
                        order.setActive(false);
                        KitchenOrder saved = kitchenOrderRepository.save(order);
                        eventPublisher.publishEvent(
                                new KitchenOrderRetired(saved.getTenantId(), event.sessionId()));
                    } else {
                        KitchenOrder saved = kitchenOrderRepository.save(order);
                        eventPublisher.publishEvent(new KitchenItemRemoved(
                                saved.getTenantId(), event.sessionId(), event.orderItemId()));
                    }
                });
    }

    /** Retires the order from the live display once its session closes; history is kept, not shown. */
    @EventListener
    public void handleSessionClosed(SessionClosed event) {
        kitchenOrderRepository
                .findByTenantIdAndSessionId(TenantContextHolder.requireTenantId(), event.sessionId())
                .ifPresent(order -> {
                    order.setActive(false);
                    KitchenOrder saved = kitchenOrderRepository.save(order);
                    eventPublisher.publishEvent(new KitchenOrderRetired(saved.getTenantId(), event.sessionId()));
                });
    }

    private boolean isValidTransition(OrderItemStatus current, OrderItemStatus next) {
        return switch (current) {
            case DRAFT -> next == OrderItemStatus.PENDING;
            case PENDING -> next == OrderItemStatus.PREPARING;
            case PREPARING -> next == OrderItemStatus.READY;
            case READY -> next == OrderItemStatus.DELIVERED;
            case DELIVERED -> false;
        };
    }
}
