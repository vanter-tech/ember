package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface KitchenOrderRepository extends MongoRepository<KitchenOrder, String> {

    Optional<KitchenOrder> findBySessionId(String sessionId);

    List<KitchenOrder> findByItems_Status(OrderItemStatus status);
}
