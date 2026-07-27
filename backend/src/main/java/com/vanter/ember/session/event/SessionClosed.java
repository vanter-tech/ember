package com.vanter.ember.session.event;

import java.util.UUID;

public record SessionClosed(String sessionId, UUID tableId, int tableNumber) {
}
