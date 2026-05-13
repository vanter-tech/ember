package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateSessionRequest(
        @NotNull Long tableId,
        @Min(1) int maxParticipants
) {}
