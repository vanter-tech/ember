package com.vanter.ember.session.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public record ActiveSessionSummary(
        String sessionId,
        String waiterName,
        int currentParticipant,
        LocalDateTime createdAt
) {
}
