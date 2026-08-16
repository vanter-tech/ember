package com.vanter.ember.cashregister.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * A single shared till's lifecycle for one tenant: {@code OPEN} while trading, {@code CLOSED}
 * once its blind-count arqueo has run. At most one {@code OPEN} row may exist per tenant — see
 * {@code uk_cash_shifts_tenant_open} in {@code V7__cash_shifts.sql}. The financial columns below
 * {@code openedAt} are written exactly once, at close, and never revisited afterward — a {@code
 * CLOSED} row is this module's immutable Z-record; there is no separate report table.
 */
@Entity
@Table(name = "cash_shifts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "shift_number", nullable = false)
    private int shiftNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashShiftStatus status;

    @Column(name = "opening_float", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "expected_cash", precision = 10, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 10, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 10, scale = 2)
    private BigDecimal variance;

    @Column(name = "total_cash_sales", precision = 10, scale = 2)
    private BigDecimal totalCashSales;

    @Column(name = "total_digital_sales", precision = 10, scale = 2)
    private BigDecimal totalDigitalSales;

    @Column(name = "total_cash_in", precision = 10, scale = 2)
    private BigDecimal totalCashIn;

    @Column(name = "total_cash_out", precision = 10, scale = 2)
    private BigDecimal totalCashOut;
}
