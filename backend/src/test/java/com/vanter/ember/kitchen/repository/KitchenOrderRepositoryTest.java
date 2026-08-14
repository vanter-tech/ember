package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class KitchenOrderRepositoryTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    @BeforeEach
    void setUp() {
        kitchenOrderRepository.deleteAll();
    }

    @Test
    void save_persistsKitchenOrder() {
        KitchenOrder order = KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getTableNumber()).isEqualTo(5);
    }

    @Test
    void findByTenantIdAndSessionId_returnsMatchingOrder() {
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-2").tableNumber(3).items(new ArrayList<>()).build());

        Optional<KitchenOrder> result = kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1");

        assertThat(result).isPresent();
        assertThat(result.get().getSessionId()).isEqualTo("sess-1");
        assertThat(result.get().getTableNumber()).isEqualTo(5);
    }

    @Test
    void findByTenantId_paginated_returnsOnePageAtATime() {
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-1").tableNumber(1).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-2").tableNumber(2).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-3").tableNumber(3).items(new ArrayList<>()).build());

        Page<KitchenOrder> firstPage = kitchenOrderRepository.findByTenantId(TENANT_ID, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByTenantIdAndItems_Status_returnsOrdersContainingItemWithGivenStatus() {
        KitchenItem pendingItem = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        KitchenItem preparingItem = KitchenItem.builder()
                .itemId("order-item-2").name("Burger").participantName("Bob")
                .status(OrderItemStatus.PREPARING).updatedAt(LocalDateTime.now()).build();

        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(pendingItem))).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-2").tableNumber(3)
                .items(new ArrayList<>(List.of(preparingItem))).build());

        List<KitchenOrder> result = kitchenOrderRepository.findByTenantIdAndItems_Status(
                TENANT_ID, OrderItemStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo("sess-1");
    }
}
