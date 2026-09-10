package com.vanter.ember.printing.dto;

import java.util.UUID;

public record PairResponse(String apiKey, String backendBaseUrl, UUID agentId, String agentName) {}
