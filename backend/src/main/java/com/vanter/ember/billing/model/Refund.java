package com.vanter.ember.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A reversal of some or all of one {@link Payment}'s amount. Never mutates the {@code Payment} it
 * refunds — {@code Payment.status} stays {@code CONFIRMED} forever, an honest record of what was
 * actually collected; how much of it was later given back is answered by summing this table's
 * rows for that payment. Multiple partial refunds against one payment are multiple rows here, each
 * independently who/when/why-attributed — this row IS the audit trail, no separate audit module.
 */
@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_payment", columnList = "payment_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    /** {@code users.id} of whoever issued this refund — same plain-column pattern as {@code Payment#processedBy}. */
    @Column(name = "refunded_by", nullable = false)
    private String refundedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
