package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KitchenOrderRepository extends MongoRepository<KitchenOrder, String> {

    List<KitchenOrder> findByTenantId(UUID tenantId);

    List<KitchenOrder> findByTenantIdAndActiveTrue(UUID tenantId);

    Page<KitchenOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<KitchenOrder> findByIdAndTenantId(String id, UUID tenantId);

    Optional<KitchenOrder> findByTenantIdAndSessionId(UUID tenantId, String sessionId);

    List<KitchenOrder> findByTenantIdAndItems_Status(UUID tenantId, OrderItemStatus status);
}
