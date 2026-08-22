package com.vanter.ember.printing.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks which {@link com.vanter.ember.printing.model.PrintAgent}s currently hold a live
 * {@code /ws/print-agent} STOMP session, so {@code PrintDispatchService} knows whether to
 * push a job immediately or leave it {@code PENDING} for the next reconnect (spec §3.3).
 */
@Component
public class PrintAgentConnectionRegistry {

    private final Map<UUID, String> agentToSession = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionToAgent = new ConcurrentHashMap<>();

    public void markConnected(UUID agentId, String sessionId) {
        agentToSession.put(agentId, sessionId);
        sessionToAgent.put(sessionId, agentId);
    }

    public void markDisconnected(String sessionId) {
        UUID agentId = sessionToAgent.remove(sessionId);
        if (agentId != null) {
            agentToSession.remove(agentId, sessionId);
        }
    }

    public boolean isConnected(UUID agentId) {
        return agentToSession.containsKey(agentId);
    }
}
