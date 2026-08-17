package com.vanter.ember.cashregister.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
 * A manual cash in/out entry on a shift (float top-up, drop to safe, petty cash). Holds {@code
 * cashShiftId} as a plain column rather than a {@code @ManyToOne} — nothing here needs to
 * navigate back to the parent {@link CashShift} in Java, only to filter by its id, the same shape
 * {@code Payment#bill} vs. {@code Bill#sessionId} already mixes in this codebase depending on
 * whether navigation is actually needed.
 */
@Entity
@Table(name = "cash_movements", indexes = @Index(name = "idx_cash_movements_shift", columnList = "cash_shift_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cash_shift_id", nullable = false)
    private Long cashShiftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashMovementType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
