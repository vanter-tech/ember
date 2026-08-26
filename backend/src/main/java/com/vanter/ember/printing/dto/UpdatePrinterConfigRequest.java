package com.vanter.ember.printing.dto;

public record UpdatePrinterConfigRequest(
        String host, Integer port, String comPort, String windowsQueueName, String renderMode,
        String label, Boolean active) {}
