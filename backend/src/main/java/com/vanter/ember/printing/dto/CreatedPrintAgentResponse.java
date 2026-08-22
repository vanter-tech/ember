package com.vanter.ember.printing.dto;

import java.util.UUID;

/** {@code apiKey} is the plaintext key — present only in this one response, never again. */
public record CreatedPrintAgentResponse(UUID id, String name, String apiKey) {}
