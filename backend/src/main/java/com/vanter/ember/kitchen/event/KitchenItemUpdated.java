package com.vanter.ember.kitchen.event;

import com.vanter.ember.session.model.OrderItemStatus;

public record KitchenItemUpdated(
        String sessionId,
        String itemId,
        OrderItemStatus newStatus
) {}
