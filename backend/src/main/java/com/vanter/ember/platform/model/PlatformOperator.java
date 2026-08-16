package com.vanter.ember.platform.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A platform-side super-admin operator, deliberately outside the tenant data model: no FK to
 * {@code Restaurant} or {@code User}, and no {@code @TenantId}. Authenticated separately via a
 * dedicated {@code platform.jwt.secret} (EMB-PC-03/04) — mutual exclusion from tenant auth comes
 * from the different signing key, not a claim check, so this table must never be joined against
 * tenant-scoped tables.
 */
@Entity
@Table(name = "platform_operators")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformOperator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
