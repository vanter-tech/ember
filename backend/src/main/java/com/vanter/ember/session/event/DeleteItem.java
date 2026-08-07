package com.vanter.ember.session.event;

public record DeleteItem(
        String type,
        String sessionId,
        String orderItemId
) {
    public DeleteItem (
        String sessionId,
        String orderItemId
    ){
        this("ITEM_DELETED",sessionId,orderItemId);
    }
}
