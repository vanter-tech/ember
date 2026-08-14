package com.vanter.ember.kitchen.event;

import com.vanter.ember.session.model.OrderItemStatus;

import java.util.UUID;

public record KitchenItemUpdated(
        UUID tenantId,
        String sessionId,
        String itemId,
        OrderItemStatus newStatus
) {}
