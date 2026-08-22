package com.vanter.ember.printing.dto;

public record UpdatePrinterConfigRequest(
        String host, Integer port, String comPort, String label, Boolean active) {}
