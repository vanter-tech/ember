package com.vanter.ember.session.event;

import java.util.UUID;

public record SessionOpened(String sessionId, UUID tableId, int tableNumber) {
}
