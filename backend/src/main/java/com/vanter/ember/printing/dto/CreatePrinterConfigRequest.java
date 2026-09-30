package com.vanter.ember.printing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePrinterConfigRequest(
        @NotNull String role,
        @NotNull String connectionType,
        String host,
        Integer port,
        String comPort,
        String windowsQueueName,
        String renderMode,
        @NotBlank String label,
        Boolean cashDrawer) {

    public CreatePrinterConfigRequest(String role, String connectionType, String host, Integer port,
            String comPort, String windowsQueueName, String renderMode, String label) {
        this(role, connectionType, host, port, comPort, windowsQueueName, renderMode, label, null);
    }
}
