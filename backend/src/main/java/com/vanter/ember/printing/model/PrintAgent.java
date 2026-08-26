package com.vanter.ember.printing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One local Hardware Bridge process registered for a restaurant. A tenant may register more
 * than one (e.g. one PC at the register, one in the kitchen) since a USB printer is only
 * reachable from the PC it is physically plugged into (spec §2.5). {@code apiKeyHash} is a
 * BCrypt hash, same encoder as {@code User.password} — the plaintext key is shown exactly
 * once, at creation/regeneration time, and never persisted.
 *
 * <p>Deliberately NOT {@code @TenantId} — same reason as {@code User}/{@code Session}:
 * {@link com.vanter.ember.printing.service.PrintAgentService#authenticateByApiKey} must scan
 * every {@code ACTIVE} agent across all tenants, since the caller (a print agent presenting its
 * API key at {@code POST /printing/agents/token}, a {@code permitAll} route) has no tenant bound
 * yet — that's the whole point of the call. Every tenant-scoped method
 * ({@code create}/{@code list}/{@code getOwned}) filters by {@code tenantId} explicitly instead.
 */
@Entity
@Table(name = "print_agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintAgent {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrintAgentStatus status;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
