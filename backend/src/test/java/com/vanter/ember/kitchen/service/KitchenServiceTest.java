package com.vanter.ember.kitchen.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.kitchen.dto.KitchenDisplayEntry;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.event.KitchenOrderRetired;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.event.SessionClosed;
import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.model.OrderItemStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock KitchenOrderRepository kitchenOrderRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks KitchenService kitchenService;

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private OrderItem confirmedItem(String id, String name, String participantName) {
        return OrderItem.builder()
                .id(id).itemId(1L).name(name).price(new BigDecimal("12.50"))
                .participantId("p-1").participantName(participantName)
                .status(OrderItemStatus.PENDING).addedAt(LocalDateTime.now())
                .build();
    }

    private KitchenItemsConfirmed sampleEvent() {
        return new KitchenItemsConfirmed(
                TENANT_ID, "sess-1", 5, List.of(confirmedItem("order-item-1", "Tacos", "Alice")));
    }

    @Test
    void handleOrderItemAdded_createsNewKitchenOrderWhenNoneExists() {
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.empty());
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
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void updateItemStatus_throwsWhenOrderBelongsToAnotherTenant() {
        // the order exists, but under another tenant, so the scoped lookup returns nothing
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findBySessionId_scopesLookupToCurrentTenant() {
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.findBySessionId("sess-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void handleOrderItemAdded_addsItemToExistingKitchenOrder() {
        KitchenOrder existing = KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.of(existing));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.handleOrderItemAdded(sampleEvent());

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("ko-1");
        assertThat(captor.getValue().getItems()).hasSize(1);
    }

    @Test
    void handleOrderItemAdded_copiesTableNumberAndParticipantNameFromEvent() {
        KitchenItemsConfirmed event = new KitchenItemsConfirmed(
                TENANT_ID, "sess-2", 12, List.of(confirmedItem("order-item-5", "Burger", "Bob")));
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-2"))
                .thenReturn(Optional.empty());
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
                .id("ko-1").tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(item))).build();
    }

    @Test
    void updateItemStatus_persistsNewStatusAndUpdatedAt() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING);

        assertThat(result.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PREPARING);
        assertThat(result.getItems().get(0).getUpdatedAt()).isNotNull();
    }

    @Test
    void updateItemStatus_throwsOnInvalidTransition() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(orderWithItem(OrderItemStatus.DELIVERED)));

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PENDING))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void updateItemStatus_throwsWhenItemNotFound() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-1", "nonexistent-item", OrderItemStatus.PREPARING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemStatus_throwsWhenOrderNotFound() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-999", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.updateItemStatus("ko-999", "order-item-1", OrderItemStatus.PREPARING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemStatus_publishesKitchenItemUpdatedEvent() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(orderWithItem(OrderItemStatus.PENDING)));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.updateItemStatus("ko-1", "order-item-1", OrderItemStatus.PREPARING);

        ArgumentCaptor<KitchenItemUpdated> captor = ArgumentCaptor.forClass(KitchenItemUpdated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(captor.getValue().itemId()).isEqualTo("order-item-1");
        assertThat(captor.getValue().newStatus()).isEqualTo(OrderItemStatus.PREPARING);
    }

    // --- handleSessionClosed tests ---

    @Test
    void handleSessionClosed_retiresTheOrderForThatSession() {
        KitchenOrder order = orderWithItem(OrderItemStatus.DELIVERED);
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1"))
                .thenReturn(Optional.of(order));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.handleSessionClosed(new SessionClosed("sess-1", UUID.randomUUID(), SessionStatus.CLOSED));

        ArgumentCaptor<KitchenOrder> captor = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();

        ArgumentCaptor<KitchenOrderRetired> eventCaptor = ArgumentCaptor.forClass(KitchenOrderRetired.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(eventCaptor.getValue().sessionId()).isEqualTo("sess-1");
    }

    @Test
    void handleSessionClosed_doesNothingWhenNoOrderExistsForThatSession() {
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1"))
                .thenReturn(Optional.empty());

        kitchenService.handleSessionClosed(new SessionClosed("sess-1", UUID.randomUUID(), SessionStatus.CLOSED));

        verify(kitchenOrderRepository, org.mockito.Mockito.never()).save(any());
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(KitchenOrderRetired.class));
    }

    // --- findAll(Pageable) tests ---

    @Test
    void findAllPaged_scopesLookupToCurrentTenant() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<KitchenOrder> page = new PageImpl<>(List.of(orderWithItem(OrderItemStatus.PENDING)));
        when(kitchenOrderRepository.findByTenantId(TENANT_ID, pageable)).thenReturn(page);

        assertThat(kitchenService.findAll(pageable).getContent()).hasSize(1);
    }

    // --- findDisplay tests ---

    @Test
    void findDisplay_groupsOrdersByTableNumber() {
        LocalDateTime now = LocalDateTime.now();
        KitchenOrder orderTable1 = KitchenOrder.builder()
                .id("ko-2").sessionId("sess-2").tableNumber(1)
                .createdAt(now.minusMinutes(10)).items(new ArrayList<>()).build();
        KitchenOrder orderTable3a = KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(3)
                .createdAt(now.minusMinutes(5)).items(new ArrayList<>()).build();
        KitchenOrder orderTable3b = KitchenOrder.builder()
                .id("ko-3").sessionId("sess-3").tableNumber(3)
                .createdAt(now.minusMinutes(2)).items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(List.of(orderTable3a, orderTable1, orderTable3b));

        List<KitchenDisplayEntry> display = kitchenService.findDisplay();

        assertThat(display).hasSize(2);
        assertThat(display.get(0).tableNumber()).isEqualTo(1);
        assertThat(display.get(0).orders()).hasSize(1);
        assertThat(display.get(1).tableNumber()).isEqualTo(3);
        assertThat(display.get(1).orders()).hasSize(2);
    }

    @Test
    void findDisplay_sortsOrdersWithinGroupByCreatedAtAscending() {
        LocalDateTime now = LocalDateTime.now();
        KitchenOrder older = KitchenOrder.builder()
                .id("ko-1").sessionId("sess-1").tableNumber(3)
                .createdAt(now.minusMinutes(5)).items(new ArrayList<>()).build();
        KitchenOrder newer = KitchenOrder.builder()
                .id("ko-3").sessionId("sess-3").tableNumber(3)
                .createdAt(now.minusMinutes(2)).items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(List.of(newer, older));

        List<KitchenDisplayEntry> display = kitchenService.findDisplay();

        List<KitchenOrder> orders = display.get(0).orders();
        assertThat(orders.get(0).getId()).isEqualTo("ko-1");
        assertThat(orders.get(1).getId()).isEqualTo("ko-3");
    }

    @Test
    void findDisplay_sortsGroupsByTableNumberAscending() {
        LocalDateTime now = LocalDateTime.now();
        KitchenOrder table5 = KitchenOrder.builder()
                .id("ko-5").sessionId("sess-5").tableNumber(5)
                .createdAt(now).items(new ArrayList<>()).build();
        KitchenOrder table2 = KitchenOrder.builder()
                .id("ko-2").sessionId("sess-2").tableNumber(2)
                .createdAt(now).items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(List.of(table5, table2));

        List<KitchenDisplayEntry> display = kitchenService.findDisplay();

        assertThat(display.get(0).tableNumber()).isEqualTo(2);
        assertThat(display.get(1).tableNumber()).isEqualTo(5);
    }
}
