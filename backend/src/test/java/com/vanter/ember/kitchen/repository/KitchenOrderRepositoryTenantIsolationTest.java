package com.vanter.ember.kitchen.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Cross-tenant isolation regression tests for {@link KitchenOrderRepository}.
 *
 * <p>Both tenants get an order for the same session id, holding an item in the same status, so any
 * finder that drops the tenant from its query returns the other restaurant's kitchen queue — which
 * is exactly the leak {@code GET /kitchen/display} had before task-2.17. {@link KitchenOrder}
 * carries {@code @TenantId} (unlike {@code Session}, which has no untenanted lookup need), so
 * stamping/filtering is automatic — see {@link AbstractTenantIsolationTest} for why each save/read
 * below still runs wrapped in {@code asTenant}/{@code readAs}.
 */
class KitchenOrderRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    private String orderAId;
    private String orderBId;

    @Override
    protected void deleteAll() {
        kitchenOrderRepository.deleteAll();
    }

    private KitchenOrder newOrder() {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        return KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    private void seed() {
        orderAId = readAs(TENANT_A, () -> kitchenOrderRepository.save(newOrder())).getId();
        orderBId = readAs(TENANT_B, () -> kitchenOrderRepository.save(newOrder())).getId();
    }

    @Test
    void findByTenantId_returnsOnlyTheOwningTenantsOrders() {
        seed();

        assertThat(readAs(TENANT_A, () -> kitchenOrderRepository.findByTenantId(TENANT_A)))
                .extracting(KitchenOrder::getId).containsExactly(orderAId);
        // Bound context TENANT_B, but explicit param asks for TENANT_A's data -- the @TenantId
        // filter and the explicit param must both agree, so a mismatch returns nothing rather
        // than leaking tenant A's order to a request authenticated as tenant B.
        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantId(TENANT_A))).isEmpty();
    }

    @Test
    void findByTenantId_paginated_returnsOnlyTheOwningTenantsOrders() {
        seed();

        assertThat(readAs(TENANT_A,
                () -> kitchenOrderRepository.findByTenantId(TENANT_A, PageRequest.of(0, 10)).getContent()))
                .extracting(KitchenOrder::getId).containsExactly(orderAId);
        assertThat(readAs(TENANT_B,
                () -> kitchenOrderRepository.findByTenantId(TENANT_A, PageRequest.of(0, 10)).getContent()))
                .isEmpty();
    }

    @Test
    void findByIdAndTenantId_doesNotResolveAnotherTenantsOrder() {
        seed();

        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByIdAndTenantId(orderAId, TENANT_B)))
                .isEmpty();
        assertThat(readAs(TENANT_A, () -> kitchenOrderRepository.findByIdAndTenantId(orderAId, TENANT_A)))
                .isPresent();
    }

    @Test
    void findByTenantIdAndSessionId_doesNotResolveAnotherTenantsSession() {
        seed();

        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_B, "sess-1")))
                .hasValueSatisfying(o -> assertThat(o.getId()).isEqualTo(orderBId));
        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_A, "sess-1")))
                .isEmpty();
    }

    @Test
    void findByTenantIdAndItems_Status_returnsOnlyTheOwningTenantsOrders() {
        seed();

        List<KitchenOrder> result = readAs(TENANT_A,
                () -> kitchenOrderRepository.findByTenantIdAndItems_Status(TENANT_A, OrderItemStatus.PENDING));
        assertThat(result).extracting(KitchenOrder::getId).containsExactly(orderAId);

        assertThat(readAs(TENANT_B,
                () -> kitchenOrderRepository.findByTenantIdAndItems_Status(TENANT_A, OrderItemStatus.PENDING)))
                .isEmpty();
    }
}
