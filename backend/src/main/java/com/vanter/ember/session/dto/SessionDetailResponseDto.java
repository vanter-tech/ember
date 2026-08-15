package com.vanter.ember.session.dto;

import com.vanter.ember.session.model.SessionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record SessionDetailResponseDto(
        String id,
        UUID tableId,
        Integer tableNumber,
        boolean isOccupied,
        String waiterId,
        SessionStatus status,
        int maxParticipants,
        List<ParticipantDto> participants,
        List<OrderItemDto> items,
        List<SessionActivityDto> activityLog,
        LocalDateTime createdAt
) {
}
