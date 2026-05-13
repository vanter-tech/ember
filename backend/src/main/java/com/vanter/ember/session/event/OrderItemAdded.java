package com.vanter.ember.session.event;

import java.math.BigDecimal;

public record OrderItemAdded(
        String sessionId,
        String orderItemId,
        int tableNumber,
        Long itemId,
        String itemName,
        BigDecimal price,
        String participantName
) {}
