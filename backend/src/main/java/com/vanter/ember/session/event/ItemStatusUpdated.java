package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItemStatus;

public record ItemStatusUpdated(
        String sessionId,
        String itemId,
        String itemName,
        String participantName,
        OrderItemStatus newStatus
) {}
