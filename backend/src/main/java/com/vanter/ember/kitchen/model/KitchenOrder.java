package com.vanter.ember.kitchen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "kitchen_orders",
        indexes = {
                @Index(name = "idx_kitchen_orders_tenant", columnList = "tenant_id"),
                @Index(name = "idx_kitchen_orders_tenant_active", columnList = "tenant_id, active"),
                @Index(name = "idx_kitchen_orders_tenant_session", columnList = "tenant_id, session_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrder {

    @Id
    @Column(updatable = false, nullable = false, length = 36)
    private String id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "table_number", nullable = false)
    private int tableNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // No columnDefinition -- see Session.java's comment on the same pattern.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<KitchenItem> items = new ArrayList<>();

    /** Whether this order still belongs to a live session; false once its session closes. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
