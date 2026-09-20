package com.vanter.ember.printing.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Tracks which {@link com.vanter.ember.printing.model.PrintAgent}s currently hold a live
 * {@code /ws/print-agent} STOMP session, so {@code PrintDispatchService} knows whether to
 * push a job immediately or leave it {@code PENDING} for the next reconnect (spec §3.3). It also
 * remembers the (normalized) address each agent connected from, so an on-premise Hub can route a
 * receipt to the agent running on the PC that asked for it ({@link PrintTargetResolver}).
 */
@Component
public class PrintAgentConnectionRegistry {

    private final Map<UUID, String> agentToSession = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionToAgent = new ConcurrentHashMap<>();
    private final Map<UUID, String> agentToAddress = new ConcurrentHashMap<>();

    public void markConnected(UUID agentId, String sessionId) {
        markConnected(agentId, sessionId, null);
    }

    public void markConnected(UUID agentId, String sessionId, String remoteAddress) {
        agentToSession.put(agentId, sessionId);
        sessionToAgent.put(sessionId, agentId);
        String address = SourceIps.normalize(remoteAddress);
        if (address != null) {
            agentToAddress.put(agentId, address);
        } else {
            agentToAddress.remove(agentId);
        }
    }

    public void markDisconnected(String sessionId) {
        UUID agentId = sessionToAgent.remove(sessionId);
        if (agentId != null && agentToSession.remove(agentId, sessionId)) {
            agentToAddress.remove(agentId);
        }
    }

    public boolean isConnected(UUID agentId) {
        return agentToSession.containsKey(agentId);
    }

    /** Connected agents whose session came from {@code normalizedAddress} (see {@link SourceIps}). */
    public List<UUID> connectedAgentsAt(String normalizedAddress) {
        if (normalizedAddress == null) {
            return List.of();
        }
        return agentToAddress.entrySet().stream()
                .filter(e -> normalizedAddress.equals(e.getValue()) && agentToSession.containsKey(e.getKey()))
                .map(Map.Entry::getKey)
                .toList();
    }
}
