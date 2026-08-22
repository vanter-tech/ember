package com.vanter.ember.printing.dto;

import java.util.UUID;

/** Sent backend → agent over {@code /topic/print-agent/{agentId}}. */
public record PrintJobMessage(UUID jobId, String role, String payload) {}
