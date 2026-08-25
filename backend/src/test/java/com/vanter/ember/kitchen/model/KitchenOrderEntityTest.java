package com.vanter.ember.kitchen.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.kitchen.repository.KitchenOrderRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Transactional(NOT_SUPPORTED)}: Hibernate resolves the {@code @TenantId} identifier once,
 * when the session opens — {@code @DataJpaTest}'s default shared transaction opens that session
 * before any test code runs, so {@link TenantContextHolder#setTenantId} inside the test body would
 * be too late. Same reasoning as {@link com.vanter.ember.config.AbstractTenantIsolationTest}. This
 * also means the raw {@code EntityManager} can't be used directly (it needs an ambient
 * transaction) — go through {@link KitchenOrderRepository}, whose {@code save}/{@code findById}
 * carry their own transactional boundary, exactly like every other {@code @TenantId} repository
 * test in this codebase.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class KitchenOrderEntityTest {

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void persist_stampsBoundTenantAndAssignsId() {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        KitchenOrder order = KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);

        kitchenOrderRepository.delete(saved);
    }
}
