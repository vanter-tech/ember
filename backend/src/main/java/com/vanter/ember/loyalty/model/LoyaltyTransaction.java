package com.vanter.ember.loyalty.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * Append-only points ledger entry, same immutable-fact pattern as {@code Refund}/{@code
 * CashMovement} — never mutated, each row a fact. {@code points} is signed (always positive
 * today, no redemption yet) so a future redemption phase can post negative entries without a
 * schema change. {@code billId} links to {@code Bill.id} rather than a specific {@code
 * Payment.id} — accrual fires once per settled bill, and crediting per-payment would double-count
 * on a bill with multiple payments.
 */
@Entity
@Table(
        name = "loyalty_transactions",
        indexes = @Index(name = "idx_loyalty_transactions_account", columnList = "loyalty_account_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoyaltyTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "loyalty_account_id", nullable = false)
    private LoyaltyAccount loyaltyAccount;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private String reason;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
