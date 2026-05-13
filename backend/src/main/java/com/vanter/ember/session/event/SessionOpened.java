package com.vanter.ember.session.event;

public record SessionOpened(String sessionId, Long tableId, int tableNumber) {
}
