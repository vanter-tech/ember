package com.vanter.ember.kitchen.service;

import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.OrderItemAdded;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    @Mock KitchenOrderRepository kitchenOrderRepository;
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
}
