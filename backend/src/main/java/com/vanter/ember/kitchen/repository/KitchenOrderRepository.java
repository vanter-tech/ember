package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, String> {

    List<KitchenOrder> findByTenantId(UUID tenantId);

    List<KitchenOrder> findByTenantIdAndActiveTrue(UUID tenantId);

    Page<KitchenOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<KitchenOrder> findByIdAndTenantId(String id, UUID tenantId);

    Optional<KitchenOrder> findByTenantIdAndSessionId(UUID tenantId, String sessionId);

    /**
     * Filters the embedded {@code items} JSON in Java rather than in SQL — same portability
     * reasoning as {@link com.vanter.ember.session.repository.SessionRepository#findByTenantIdAndParticipants_UserId}.
     */
    default List<KitchenOrder> findByTenantIdAndItems_Status(UUID tenantId, OrderItemStatus status) {
        return findByTenantId(tenantId).stream()
                .filter(o -> o.getItems().stream().anyMatch(i -> status == i.getStatus()))
                .toList();
    }
}
