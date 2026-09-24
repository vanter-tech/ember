package com.vanter.ember.printing.dto;

import java.util.UUID;

/**
 * Sent backend → agent over {@code /topic/print-agent/{agentId}}. {@code logo} tells the agent the
 * restaurant has a receipt logo to print above this ticket; agents that predate the field ignore
 * it (Spring's default Jackson mapper doesn't fail on unknown properties) and print text only.
 */
public record PrintJobMessage(UUID jobId, String role, String payload, boolean logo) {

    public PrintJobMessage(UUID jobId, String role, String payload) {
        this(jobId, role, payload, false);
    }
}
