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
        @NotBlank String label) {}
