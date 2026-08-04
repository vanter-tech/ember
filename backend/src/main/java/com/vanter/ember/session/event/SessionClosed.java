package com.vanter.ember.session.event;

import com.vanter.ember.session.model.SessionStatus;

import java.util.UUID;

public record SessionClosed(String sessionId, UUID tableId, SessionStatus status) {
}
