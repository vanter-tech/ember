package com.vanter.ember.numbering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Last number issued in one series of one tenant, plus the prefix frozen when the series was first
 * used. Deliberately not {@code @TenantId}: the numbering service picks the row by explicit tenant,
 * like {@code User}/{@code Session} (see CLAUDE.md on untenanted lookups).
 */
@Entity
@Table(name = "document_counters")
@IdClass(DocumentCounter.Key.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentCounter {

    @Id
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private DocumentSeries series;

    @Column(nullable = false, updatable = false, length = 8)
    private String prefix;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private UUID tenantId;
        private DocumentSeries series;
    }
}
