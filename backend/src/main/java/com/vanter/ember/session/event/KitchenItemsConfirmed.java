package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItem;

import java.util.List;
import java.util.UUID;

public record KitchenItemsConfirmed(
        UUID tenantId,
        String sessionId,
        int tableNumber,
        List<OrderItem> confirmedItems
) {
}
