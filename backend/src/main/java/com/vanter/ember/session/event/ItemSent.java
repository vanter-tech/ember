package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.Session;

import java.util.List;

public record ItemSent(
        String type,
        String sessionId,
        List<OrderItem> sessionItems
) {
    public ItemSent(Session session) {
        this("ITEMS_CONFIRMED", session.getId(), session.getItems());
    }
}
