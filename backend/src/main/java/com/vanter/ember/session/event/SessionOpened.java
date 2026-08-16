package com.vanter.ember.session.event;

import java.util.UUID;

public record SessionOpened(UUID tenantId, String sessionId, UUID tableId, int tableNumber) {
}
