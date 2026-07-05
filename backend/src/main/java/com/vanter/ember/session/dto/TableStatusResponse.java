package com.vanter.ember.session.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TableStatusResponse(
        UUID tableId,
        int tableNumber,
        boolean isOccupied,
        ActiveSessionSummary currentSession
) {

}
