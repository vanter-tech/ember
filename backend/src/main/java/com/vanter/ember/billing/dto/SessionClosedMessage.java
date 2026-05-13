package com.vanter.ember.billing.dto;

public record SessionClosedMessage(String type, String sessionId, Long billId) {

    public static SessionClosedMessage of(String sessionId, Long billId) {
        return new SessionClosedMessage("SESSION_CLOSED", sessionId, billId);
    }
}
