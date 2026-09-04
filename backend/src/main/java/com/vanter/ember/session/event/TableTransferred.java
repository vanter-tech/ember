package com.vanter.ember.session.event;

import java.util.UUID;

public record TableTransferred(
        String type, UUID tenantId, String sessionId, UUID tableId,
        String fromWaiterId, String toWaiterId, String toWaiterName) {

    public TableTransferred(UUID tenantId, String sessionId, UUID tableId,
                            String fromWaiterId, String toWaiterId, String toWaiterName) {
        this("TABLE_TRANSFERRED", tenantId, sessionId, tableId,
                fromWaiterId, toWaiterId, toWaiterName);
    }
}
