package com.vanter.ember.session.event;

public record ParticipantJoined(String type, String sessionId, String userId, String userName) {
    public ParticipantJoined(String sessionId, String userId, String userName) {
        this("PARTICIPANT_JOINED", sessionId,userId,userName);
    }
}
