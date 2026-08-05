package com.vanter.ember.session.event;

import java.math.BigDecimal;

public record OrderItemAdded(
        String type,
        String sessionId,
        String orderItemId,
        int tableNumber,
        Long itemId,
        String itemName,
        BigDecimal price,
        String participantName
) {
    public OrderItemAdded (
            String sessionId,
            String orderItemId,
            int tableNumber,
            Long itemId,
            String itemName,
            BigDecimal price,
            String participantName
    ){
        this("ITEM_ADDED",sessionId,orderItemId,tableNumber,itemId,itemName,price,participantName);
    }
}
