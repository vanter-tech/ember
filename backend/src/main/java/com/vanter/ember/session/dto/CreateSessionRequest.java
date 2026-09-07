package com.vanter.ember.session.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateSessionRequest(
        @NotNull UUID tableId,
        @Min(1) int maxParticipants,
        List<@Size(max = 50) String> seatNames
) {}
