package com.vanter.ember.session.event;

import java.util.UUID;

/**
 * A waiter renamed a name-only Hub seat ({@link ParticipantJoined} with a null {@code userId}).
 * Broadcast to the per-session topic and the tenant-wide waiter floor topic so every open view
 * of the table re-reads its participant list. Emitted only by {@code SessionService.renameSeat}.
 */
public record ParticipantRenamed(
        String type, UUID tenantId, String sessionId, String oldName, String newName) {

    public ParticipantRenamed(UUID tenantId, String sessionId, String oldName, String newName) {
        this("PARTICIPANT_RENAMED", tenantId, sessionId, oldName, newName);
    }
}
