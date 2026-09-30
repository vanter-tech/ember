package com.vanter.ember.printing.dto;

import java.util.UUID;

/**
 * Sent backend → agent over {@code /topic/print-agent/{agentId}}. {@code logo} tells the agent the
 * restaurant has a ticket logo to print above this ticket (bill receipt and kitchen ticket alike); agents that predate the field ignore
 * it (Spring's default Jackson mapper doesn't fail on unknown properties) and print text only.
 * {@code sourceType} lets the agent tell a cash-drawer kick from a printable ticket; older agents
 * ignore it.
 */
public record PrintJobMessage(UUID jobId, String role, String payload, boolean logo, String sourceType) {

    public PrintJobMessage(UUID jobId, String role, String payload, boolean logo) {
        this(jobId, role, payload, logo, null);
    }

    public PrintJobMessage(UUID jobId, String role, String payload) {
        this(jobId, role, payload, false, null);
    }
}
