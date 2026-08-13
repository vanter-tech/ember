package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

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

    @Test
    void tenantScopedQueries_doNotResolveAnotherTenantsOrders() {
        KitchenOrder saved = kitchenOrderRepository.save(KitchenOrder.builder()
                .tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>()).build());
        UUID otherTenant = UUID.randomUUID();

        assertThat(kitchenOrderRepository.findByTenantId(otherTenant)).isEmpty();
        assertThat(kitchenOrderRepository.findByIdAndTenantId(saved.getId(), otherTenant)).isEmpty();
        assertThat(kitchenOrderRepository.findByTenantIdAndSessionId(otherTenant, "sess-1")).isEmpty();

        assertThat(kitchenOrderRepository.findByTenantId(TENANT_ID)).hasSize(1);
        assertThat(kitchenOrderRepository.findByIdAndTenantId(saved.getId(), TENANT_ID)).isPresent();
    }
}
