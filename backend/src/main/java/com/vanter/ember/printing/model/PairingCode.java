package com.vanter.ember.printing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A one-time, short-lived ({@code expiresAt}, ~15 min) code the admin hands to whoever installs
 * the agent. Redeeming it at {@code POST /printing/agents/pair} returns the agent's API key
 * (stored here in the clear precisely so it can be handed back once — see V10 design note) plus
 * the backend base URL. {@code consumedAt != null} means spent.
 *
 * <p>Deliberately NOT {@code @TenantId}: the redeem call is {@code permitAll} with no tenant
 * bound (the code IS the credential), same class of exception as {@link PrintAgent}.
 */
@Entity
@Table(name = "pairing_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingCode {

    @Id
    @Column(length = 12)
    private String code;

    @Column(name = "print_agent_id", nullable = false)
    private UUID printAgentId;

    @Column(name = "api_key_plaintext", nullable = false)
    private String apiKeyPlaintext;

    @Column(name = "backend_base_url", nullable = false)
    private String backendBaseUrl;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
