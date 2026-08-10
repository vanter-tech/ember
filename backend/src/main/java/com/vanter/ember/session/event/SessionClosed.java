package com.vanter.ember.session.event;

import com.vanter.ember.session.model.SessionStatus;

import java.util.UUID;

public record SessionClosed(String type,String sessionId, UUID tableId, SessionStatus status) {
    public SessionClosed(String sessionId, UUID tableId, SessionStatus status){
        this("SESSION_CLOSED",sessionId,tableId,status);
    }
}
