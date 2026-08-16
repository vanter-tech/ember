package com.vanter.ember.platform.model;

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
 * An immutable audit trail entry for platform-operator actions, deliberately outside the tenant
 * data model like {@link PlatformOperator}: no FK to {@code Restaurant} or {@code User}, and no
 * {@code @TenantId}. {@code operatorId}/{@code operatorEmail} are a snapshot at write time, not a
 * live reference, so the log stays readable even if the operator row is later changed or removed.
 */
@Entity
@Table(name = "platform_audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID operatorId;

    @Column(nullable = false)
    private String operatorEmail;

    private UUID restaurantId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "text")
    private String oldValue;

    @Column(columnDefinition = "text")
    private String newValue;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
