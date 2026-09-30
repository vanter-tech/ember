package com.vanter.ember.cashregister.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A cash receipt waiting for the accountant ({@code CASH_SALE}, created when a waiter confirms a
 * physical payment) or a manual drawer opening ({@code MANUAL}, reason required). {@code
 * printJobId} links the latest kick job; the drawer outcome is derived from it, never stored.
 * References ({@code paymentId}, {@code cashShiftId}, {@code createdBy}, ...) are plain columns,
 * the same convention as {@link CashMovement}.
 */
@Entity
@Table(name = "cash_drawer_events", indexes = {
        @Index(name = "idx_cash_drawer_events_tenant_status", columnList = "tenant_id,status"),
        @Index(name = "idx_cash_drawer_events_shift", columnList = "cash_shift_id")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashDrawerEvent {

    @Id
    private UUID id;

    @Version
    private Long version;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashDrawerEventType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashDrawerEventStatus status;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "cash_shift_id")
    private Long cashShiftId;

    @Column(name = "table_number")
    private Integer tableNumber;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    private String reason;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "received_by")
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(name = "drawer_skipped_at")
    private LocalDateTime drawerSkippedAt;

    @Column(name = "drawer_skipped_by")
    private String drawerSkippedBy;

    @Column(name = "print_job_id")
    private UUID printJobId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
