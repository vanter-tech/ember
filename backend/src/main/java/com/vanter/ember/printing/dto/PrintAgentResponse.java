package com.vanter.ember.printing.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PrintAgentResponse(
        UUID id, String name, String status, LocalDateTime lastSeenAt, boolean connected) {}
