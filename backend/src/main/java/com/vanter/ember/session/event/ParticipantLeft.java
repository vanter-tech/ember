package com.vanter.ember.session.event;

import java.util.UUID;

/**
 * A customer abandoned an open session. Their DRAFT items are discarded; anything already sent
 * to the kitchen stays on the table bill. Consumed by the websocket listeners (per-session +
 * floor topics) and by the billing side, which redistributes an unpaid split if one exists.
 */
public record ParticipantLeft(String type, UUID tenantId, String sessionId, String userId, String userName) {
    public ParticipantLeft(UUID tenantId, String sessionId, String userId, String userName) {
        this("PARTICIPANT_LEFT", tenantId, sessionId, userId, userName);
    }
}
