package com.vanter.ember.session.event;

import java.util.UUID;

public record ParticipantJoined(
        String type, UUID tenantId, String sessionId, String userId, String userName, boolean guest) {

    public ParticipantJoined(UUID tenantId, String sessionId, String userId, String userName) {
        this("PARTICIPANT_JOINED", tenantId, sessionId, userId, userName, false);
    }

    public ParticipantJoined(UUID tenantId, String sessionId, String userId, String userName, boolean guest) {
        this("PARTICIPANT_JOINED", tenantId, sessionId, userId, userName, guest);
    }
}
