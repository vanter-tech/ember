package com.vanter.ember.session.event;

import com.vanter.ember.session.model.OrderItem;
import com.vanter.ember.session.model.OrderItemStatus;

import java.math.BigDecimal;
import java.util.List;

public record ItemAdded(
        String type,
        String sessionId,
        String itemName,
        BigDecimal price,
        String participantName,
        OrderItemStatus status,
        List<OrderItem> sessionItems
) {
    public ItemAdded (
            String sessionId,
            String itemName,
            BigDecimal price,
            String participantName,
            OrderItemStatus status,
            List<OrderItem> sessionItems
    ){
        this("ITEM_ADDED",sessionId,itemName,price,participantName,status,sessionItems);
    }
}
