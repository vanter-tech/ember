package com.vanter.ember.printing.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PrintJobResponse(
        UUID id, String role, String sourceType, String sourceId,
        String status, int attempts, String lastError, LocalDateTime createdAt) {}
