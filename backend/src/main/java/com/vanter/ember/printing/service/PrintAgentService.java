package com.vanter.ember.printing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.printing.dto.CreatedPrintAgentResponse;
import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.dto.PairingCodeResponse;
import com.vanter.ember.printing.dto.PrintAgentResponse;
import com.vanter.ember.printing.model.DiscoveredPrinter;
import com.vanter.ember.printing.model.PairingCode;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PairingCodeRepository;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrintAgentService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration PAIRING_CODE_TTL = Duration.ofMinutes(15);

    private final PrintAgentRepository printAgentRepository;
    private final PasswordEncoder passwordEncoder;
    private final PrintAgentConnectionRegistry connectionRegistry;
    private final PairingCodeRepository pairingCodeRepository;

    @Value("${ember.agent.backend-base-url:https://api.ember.vanter.net/v1}")
    private String agentBackendBaseUrl;

    @Transactional
    public CreatedPrintAgentResponse create(UUID tenantId, String name) {
        String apiKey = generateApiKey();
        PrintAgent agent = printAgentRepository.save(PrintAgent.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
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

    /**
     * Mints a fresh API key for the agent (rotating {@code apiKeyHash}) and stores it, in the
     * clear, on a single-use {@link PairingCode} with a 15-minute TTL. The admin reads the code
     * back out and hands it to whoever installs the agent; {@link #redeemPairingCode} returns
     * the key once. Minting a new code is the explicit, rare rotation action (spec §6).
     */
    @Transactional
    public PairingCodeResponse createPairingCode(UUID tenantId, UUID agentId) {
        PrintAgent agent = getOwned(tenantId, agentId);
        String apiKey = generateApiKey();
        agent.setApiKeyHash(passwordEncoder.encode(apiKey));
        printAgentRepository.save(agent);

        LocalDateTime now = LocalDateTime.now();
        PairingCode code = pairingCodeRepository.save(PairingCode.builder()
                .code(generatePairingCode())
                .printAgentId(agent.getId())
                .apiKeyPlaintext(apiKey)
                .backendBaseUrl(agentBackendBaseUrl)
                .expiresAt(now.plus(PAIRING_CODE_TTL))
                .createdAt(now)
                .build());
        return new PairingCodeResponse(code.getCode(), code.getExpiresAt());
    }

    /**
     * Agent-facing: exchanges a valid, unconsumed, unexpired pairing code for the agent's API
     * key + backend base URL, stamping {@code consumedAt} and {@code pairedAt}. Bad/used/expired
     * codes and non-ACTIVE agents throw {@link BadCredentialsException} (→ 401).
     */
    @Transactional
    public PairResponse redeemPairingCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        PairingCode pairing = pairingCodeRepository.findByCode(code)
                .filter(c -> c.getConsumedAt() == null)
                .filter(c -> c.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BadCredentialsException("Invalid, used or expired pairing code"));

        PrintAgent agent = printAgentRepository.findById(pairing.getPrintAgentId())
                .filter(a -> a.getStatus() == PrintAgentStatus.ACTIVE)
                .orElseThrow(() -> new BadCredentialsException("Agent is not active"));

        LocalDateTime now = LocalDateTime.now();
        pairing.setConsumedAt(now);
        pairingCodeRepository.save(pairing);
        agent.setPairedAt(now);
        printAgentRepository.save(agent);

        return new PairResponse(
                pairing.getApiKeyPlaintext(), pairing.getBackendBaseUrl(), agent.getId(), agent.getName());
    }

    /** Agent-facing: overwrites the agent's discovered-printers snapshot (no history, spec §6). */
    @Transactional
    public void saveDiscoveredPrinters(UUID agentId, List<DiscoveredPrinter> printers) {
        printAgentRepository.findById(agentId).ifPresent(agent -> {
            agent.setDiscoveredPrinters(printers == null ? List.of() : printers);
            printAgentRepository.save(agent);
        });
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
                .map(a -> toResponse(a, connectionRegistry.isConnected(a.getId())))
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
                agent.getId(), agent.getName(), agent.getStatus().name(), agent.getLastSeenAt(),
                connected, agent.getPairedAt() != null,
                agent.getDiscoveredPrinters() == null ? List.of() : agent.getDiscoveredPrinters());
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String generatePairingCode() {
        // 10 chars from Crockford-ish base32 (no I/O/0/1) — readable over the phone, ~50 bits.
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
