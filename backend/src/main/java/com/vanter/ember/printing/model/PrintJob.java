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
import org.hibernate.annotations.TenantId;

/**
 * One print request, in structured/plain-text form — never raw ESC/POS bytes (spec §2.7).
 * {@code sourceId} is a plain reference (billId or sessionId), not a FK, same convention as
 * {@code cash_shifts.opened_by}. A job with no matching {@code PrinterConfig} stays {@code
 * PENDING} forever without ever becoming {@code ERROR} — that state is a configuration gap,
 * not a failure (spec §3.3 step 2).
 */
@Entity
@Table(name = "print_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintJob {

    @Id
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private PrintJobSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrintJobStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
