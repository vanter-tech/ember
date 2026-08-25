package com.vanter.ember.session.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Deliberately has NO {@code @TenantId}: {@link com.vanter.ember.session.repository.SessionRepository#findByJoinCodeAndStatus}
 * has to see across every tenant (a customer types a table code before any restaurant is bound to
 * their JWT), which Hibernate's tenant filter would silently break by forcing every query to the
 * {@code NO_TENANT} sentinel. Same reasoning as why {@code User} stays outside {@code @TenantId}.
 * Every finder therefore keeps an explicit {@code tenantId} parameter, exactly as it did on Mongo.
 */
@Entity
@Table(
        name = "sessions",
        indexes = {
                @Index(name = "idx_sessions_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_sessions_tenant_table_status", columnList = "tenant_id, table_id, status"),
                @Index(name = "idx_sessions_join_code_status", columnList = "join_code, status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @Column(updatable = false, nullable = false, length = 36)
    private String id;

    @Version
    private Long version;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "waiter_id")
    private String waiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    // No columnDefinition: a hardcoded "jsonb" makes the table uncreatable on the H2 test
    // schema. SqlTypes.JSON resolves to the dialect's own JSON-compatible type on both
    // PostgreSQL and H2 -- see RestaurantSettings.payload for the same pattern.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_log", nullable = false)
    @Builder.Default
    private List<SessionActivity> activityLog = new ArrayList<>();

    @Column(name = "join_code", length = 10)
    private String joinCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
