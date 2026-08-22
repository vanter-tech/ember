package com.vanter.ember.printing.dto;

import java.util.UUID;

public record PrinterConfigResponse(
        UUID id, UUID agentId, String role, String connectionType,
        String host, Integer port, String comPort, String label, boolean active) {}
