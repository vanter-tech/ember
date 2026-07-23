package com.vanter.ember.session.dto;

public record SessionCreatedResponse(
        String sessionId,
        String joinCode
) {
}
