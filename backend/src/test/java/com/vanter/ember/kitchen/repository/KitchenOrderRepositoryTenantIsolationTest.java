package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation regression tests for {@link KitchenOrderRepository}.
 *
 * <p>Both tenants get an order for the same session id, holding an item in the same status, so any
 * finder that drops the tenant from its query returns the other restaurant's kitchen queue —
 * which is exactly the leak {@code GET /kitchen/display} had before task-2.17.
 */
@DataMongoTest
class KitchenOrderRepositoryTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    private KitchenOrder orderA;
    private KitchenOrder orderB;

    @BeforeEach
    void setUp() {
        kitchenOrderRepository.deleteAll();
        orderA = save(TENANT_A);
        orderB = save(TENANT_B);
    }

    private KitchenOrder save(UUID tenantId) {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        return kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(tenantId).sessionId("sess-1").tableNumber(5)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build());
    }

    @Test
    void findByTenantId_returnsOnlyTheOwningTenantsOrders() {
        assertThat(kitchenOrderRepository.findByTenantId(TENANT_A))
                .extracting(KitchenOrder::getId).containsExactly(orderA.getId());
        assertThat(kitchenOrderRepository.findByTenantId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByTenantId_paginated_returnsOnlyTheOwningTenantsOrders() {
        assertThat(kitchenOrderRepository.findByTenantId(TENANT_A, PageRequest.of(0, 10)).getContent())
                .extracting(KitchenOrder::getId).containsExactly(orderA.getId());
        assertThat(kitchenOrderRepository.findByTenantId(UUID.randomUUID(), PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }

    @Test
    void findByIdAndTenantId_doesNotResolveAnotherTenantsOrder() {
        assertThat(kitchenOrderRepository.findByIdAndTenantId(orderA.getId(), TENANT_B)).isEmpty();
        assertThat(kitchenOrderRepository.findByIdAndTenantId(orderA.getId(), TENANT_A)).isPresent();
    }

    @Test
    void findByTenantIdAndSessionId_doesNotResolveAnotherTenantsSession() {
        assertThat(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_B, "sess-1"))
                .hasValueSatisfying(o -> assertThat(o.getId()).isEqualTo(orderB.getId()));
        assertThat(kitchenOrderRepository.findByTenantIdAndSessionId(UUID.randomUUID(), "sess-1"))
                .isEmpty();
    }

    @Test
    void findByTenantIdAndItems_Status_returnsOnlyTheOwningTenantsOrders() {
        List<KitchenOrder> result = kitchenOrderRepository.findByTenantIdAndItems_Status(
                TENANT_A, OrderItemStatus.PENDING);

        assertThat(result).extracting(KitchenOrder::getId).containsExactly(orderA.getId());
    }
}
