package com.vanter.ember.session.event;

import java.math.BigDecimal;

public record OrderItemAdded(
        String sessionId,
        int tableNumber,
        Long itemId,
        String itemName,
        BigDecimal price,
        String participantName
) {}
