package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SessionRepository extends MongoRepository<Session, String> {

    Optional<Session> findByIdAndTenantId(String id, UUID tenantId);

    List<Session> findByTenantIdAndTableIdAndStatus(UUID tenantId, UUID tableId, SessionStatus status);

    List<Session> findByTenantIdAndParticipants_UserId(UUID tenantId, String userId);

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
}
