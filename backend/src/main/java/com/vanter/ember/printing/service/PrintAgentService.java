package com.vanter.ember.printing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.CreatedPrintAgentResponse;
import com.vanter.ember.printing.dto.PrintAgentResponse;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrintAgentService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PrintAgentRepository printAgentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public CreatedPrintAgentResponse create(UUID tenantId, String name) {
        String apiKey = generateApiKey();
        PrintAgent agent = printAgentRepository.save(PrintAgent.builder()
                .id(UUID.randomUUID())
                .name(name)
                .apiKeyHash(passwordEncoder.encode(apiKey))
                .status(PrintAgentStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build());
        return new CreatedPrintAgentResponse(agent.getId(), agent.getName(), apiKey);
    }

    @Transactional
    public CreatedPrintAgentResponse regenerateKey(UUID tenantId, UUID agentId) {
        PrintAgent agent = getOwned(tenantId, agentId);
        String apiKey = generateApiKey();
        agent.setApiKeyHash(passwordEncoder.encode(apiKey));
        printAgentRepository.save(agent);
        return new CreatedPrintAgentResponse(agent.getId(), agent.getName(), apiKey);
    }

    @Transactional
    public void revoke(UUID tenantId, UUID agentId) {
        PrintAgent agent = getOwned(tenantId, agentId);
        agent.setStatus(PrintAgentStatus.REVOKED);
        printAgentRepository.save(agent);
    }

    @Transactional
    public PrintAgentResponse rename(UUID tenantId, UUID agentId, String name) {
        PrintAgent agent = getOwned(tenantId, agentId);
        agent.setName(name);
        return toResponse(printAgentRepository.save(agent), false);
    }

    public List<PrintAgentResponse> list(UUID tenantId) {
        return printAgentRepository.findByTenantId(tenantId).stream()
                .map(a -> toResponse(a, false))
                .toList();
    }

    /**
     * Scans {@code ACTIVE} agents and verifies the plaintext key against each stored BCrypt
     * hash. A salted hash cannot be looked up by re-hashing the input, so this can't be a
     * derived-query lookup — acceptable because expected agent counts per tenant are tiny
     * (single digits), same reasoning as other small-headcount scans in this codebase.
     */
    public PrintAgent authenticateByApiKey(String apiKey) {
        return printAgentRepository.findAll().stream()
                .filter(a -> a.getStatus() == PrintAgentStatus.ACTIVE)
                .filter(a -> passwordEncoder.matches(apiKey, a.getApiKeyHash()))
                .findFirst()
                .orElseThrow(() -> new BadCredentialsException("Invalid or revoked API key"));
    }

    @Transactional
    public void markSeen(PrintAgent agent) {
        agent.setLastSeenAt(LocalDateTime.now());
        printAgentRepository.save(agent);
    }

    private PrintAgent getOwned(UUID tenantId, UUID agentId) {
        PrintAgent agent = printAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Print agent not found: " + agentId));
        if (!agent.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("Print agent not found: " + agentId);
        }
        return agent;
    }

    private PrintAgentResponse toResponse(PrintAgent agent, boolean connected) {
        return new PrintAgentResponse(
                agent.getId(), agent.getName(), agent.getStatus().name(), agent.getLastSeenAt(), connected);
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
