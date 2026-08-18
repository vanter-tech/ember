package com.vanter.ember.loyalty.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * One row per (tenant, customer), created lazily the first time that customer joins a table at
 * this tenant — not at registration/login, since {@code User.restaurantId} is null for customers
 * by design. Stores only {@code totalPoints}; tier is always computed on read against the
 * tenant's current {@code LoyaltySettings} thresholds, never persisted here.
 */
@Entity
@Table(
        name = "loyalty_accounts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_loyalty_accounts_tenant_user",
                        columnNames = {"tenant_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** {@code users.id} of the account's owner — same plain-column, no-@ManyToOne-to-User
     * convention as {@code Payment#processedBy}. */
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private int totalPoints = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
