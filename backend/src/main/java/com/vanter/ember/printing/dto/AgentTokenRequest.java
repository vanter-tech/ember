package com.vanter.ember.printing.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentTokenRequest(@NotBlank String apiKey) {}
