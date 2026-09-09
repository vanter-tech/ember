package com.vanter.ember.printing.dto;

import com.vanter.ember.printing.model.DiscoveredPrinter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PrintAgentResponse(
        UUID id,
        String name,
        String status,
        LocalDateTime lastSeenAt,
        boolean connected,
        boolean paired,
        List<DiscoveredPrinter> discoveredPrinters) {}
