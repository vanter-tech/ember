package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {

    Optional<Session> findByIdAndTenantId(String id, UUID tenantId);

    List<Session> findByTenantIdAndTableIdAndStatus(UUID tenantId, UUID tableId, SessionStatus status);

    List<Session> findByTenantId(UUID tenantId);

    /**
     * Filters the embedded {@code participants} JSON in Java rather than in SQL: Postgres's jsonb
     * containment operators aren't portable to H2 (the test datasource), and this method has no
     * production caller today. A tenant-first fetch is cheap at real-world scale (a few thousand
     * sessions per tenant), so a portable in-memory filter beats a Postgres-only native query here.
     */
    default List<Session> findByTenantIdAndParticipants_UserId(UUID tenantId, String userId) {
        return findByTenantId(tenantId).stream()
                .filter(s -> s.getParticipants().stream().anyMatch(p -> userId.equals(p.getUserId())))
                .toList();
    }

    List<Session> findByTenantIdAndTableIdInAndStatus(
            UUID tenantId, List<UUID> tableIds, SessionStatus status);

    Optional<Session> findByTenantIdAndJoinCodeAndStatus(
            UUID tenantId, String joinCode, SessionStatus status);

    /**
     * Deliberately untenanted: a customer types a table code before any restaurant is bound to
     * their token, so this is the one lookup that has to span tenants. Returns a list because
     * join codes are only random, not globally unique — see SessionService#joinSessionCode.
     */
    List<Session> findByJoinCodeAndStatus(String joinCode, SessionStatus status);

    /** How many sessions the tenant currently has in the given status — the analytics live count. */
    long countByTenantIdAndStatus(UUID tenantId, SessionStatus status);

    /**
     * Bulk tenant-first fetch used by product analytics to pull the line items of the sessions whose
     * bills settled inside the reporting window.
     */
    List<Session> findByTenantIdAndIdIn(UUID tenantId, Collection<String> ids);
}
