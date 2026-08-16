package com.vanter.ember.session.event;

import com.vanter.ember.session.model.SessionStatus;

import java.util.UUID;

public record SessionClosed(String type, UUID tenantId, String sessionId, UUID tableId, SessionStatus status) {
    public SessionClosed(UUID tenantId, String sessionId, UUID tableId, SessionStatus status){
        this("SESSION_CLOSED", tenantId, sessionId, tableId, status);
    }
}
