package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItem;

import java.util.List;
import java.util.UUID;

public record KitchenItemsConfirmed(
        UUID tenantId,
        String sessionId,
        int tableNumber,
        List<OrderItem> confirmedItems,
        List<Integer> linkedTableNumbers
) {

    /** Individual table: no linked tables. */
    public KitchenItemsConfirmed(UUID tenantId, String sessionId, int tableNumber, List<OrderItem> confirmedItems) {
        this(tenantId, sessionId, tableNumber, confirmedItems, List.of());
    }
}
