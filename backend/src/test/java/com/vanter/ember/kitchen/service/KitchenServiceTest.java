package com.vanter.ember.kitchen.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.OrderItemAdded;
import com.vanter.ember.session.model.OrderItemStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    @Mock KitchenOrderRepository kitchenOrderRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks KitchenService kitchenService;

    private OrderItemAdded sampleEvent() {
        return new OrderItemAdded(
                "sess-1", "order-item-1", 5, 10L,
                "Tacos", new BigDecimal("12.50"), "Alice");
    }

    @Test
    void handleOrderItemAdded_createsNewKitchenOrderWhenNoneExists() {
        when(kitchenOrderRepository.findBySessionId("sess-1")).thenReturn(Optional.empty());
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.handleOrderItemAdded(sampleEvent());

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());
        KitchenOrder saved = captor.getValue();
        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getTableNumber()).isEqualTo(5);
        assertThat(saved.getItems()).hasSize(1);
        KitchenItem item = saved.getItems().get(0);
        assertThat(item.getItemId()).isEqualTo("order-item-1");
        assertThat(item.getName()).isEqualTo("Tacos");
        assertThat(item.getParticipantName()).isEqualTo("Alice");
        assertThat(item.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    void handleOrderItemAdded_addsItemToExistingKitchenOrder() {
        KitchenOrder existing = KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findBySessionId("sess-1")).thenReturn(Optional.of(existing));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.handleOrderItemAdded(sampleEvent());

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("ko-1");
        assertThat(captor.getValue().getItems()).hasSize(1);
    }

    @Test
    void handleOrderItemAdded_copiesTableNumberAndParticipantNameFromEvent() {
        OrderItemAdded event = new OrderItemAdded(
                "sess-2", "order-item-5", 12, 20L,
                "Burger", new BigDecimal("9.00"), "Bob");
        when(kitchenOrderRepository.findBySessionId("sess-2")).thenReturn(Optional.empty());
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.handleOrderItemAdded(event);

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getTableNumber()).isEqualTo(12);
        assertThat(captor.getValue().getItems().get(0).getParticipantName()).isEqualTo("Bob");
    }

    // --- updateItemStatus tests ---

    private KitchenOrder orderWithItem(OrderItemStatus status) {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(status).updatedAt(LocalDateTime.now()).build();
        return KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(item))).build();
    }

    @Test
    void updateItemStatus_persistsNewStatusAndUpdatedAt() {
        when(kitchenOrderRepository.findById("ko-1")).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING);

        assertThat(result.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PREPARING);
        assertThat(result.getItems().get(0).getUpdatedAt()).isNotNull();
    }

    @Test
    void updateItemStatus_throwsOnInvalidTransition() {
        when(kitchenOrderRepository.findById("ko-1")).thenReturn(Optional.of(orderWithItem(OrderItemStatus.DELIVERED)));

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateItemStatus_throwsWhenItemNotFound() {
        when(kitchenOrderRepository.findById("ko-1")).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-1", "nonexistent-item", OrderItemStatus.PREPARING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemStatus_throwsWhenOrderNotFound() {
        when(kitchenOrderRepository.findById("ko-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-999", "order-item-1", OrderItemStatus.PREPARING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemStatus_publishesKitchenItemUpdatedEvent() {
        when(kitchenOrderRepository.findById("ko-1")).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING);

        ArgumentCaptor<KitchenItemUpdated> captor = ArgumentCaptor.forClass(KitchenItemUpdated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().itemId()).isEqualTo("order-item-1");
        assertThat(captor.getValue().newStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }
}
