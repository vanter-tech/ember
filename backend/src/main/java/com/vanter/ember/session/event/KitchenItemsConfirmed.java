package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItem;

import java.util.List;

public record KitchenItemsConfirmed(
        String sessionId,
        int tableNumber,
        List<OrderItem> confirmedItems
) {
}
