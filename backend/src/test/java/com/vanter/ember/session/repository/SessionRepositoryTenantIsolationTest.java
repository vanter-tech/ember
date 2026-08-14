package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation regression tests for {@link SessionRepository}.
 *
 * <p>Mongo has no equivalent of Hibernate's {@code @TenantId} filter, so isolation here is only as
 * good as the finder signatures: every query must carry the tenant itself. Each fixture below is
 * duplicated across two tenants with otherwise identical data — same table id, same participant,
 * same join code — so a query that forgets the tenant returns both documents and fails.
 */
@DataMongoTest
class SessionRepositoryTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TABLE_ID = UUID.randomUUID();

    @Autowired SessionRepository sessionRepository;

    private Session sessionA;
    private Session sessionB;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        sessionA = save(TENANT_A);
        sessionB = save(TENANT_B);
    }

    private Session save(UUID tenantId) {
        return sessionRepository.save(Session.builder()
                .tenantId(tenantId).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(Participant.builder().userId("user-1").name("Alice").build()))
                .joinCode("AB3CD")
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void findByIdAndTenantId_doesNotResolveAnotherTenantsSession() {
        assertThat(sessionRepository.findByIdAndTenantId(sessionA.getId(), TENANT_B)).isEmpty();
        assertThat(sessionRepository.findByIdAndTenantId(sessionA.getId(), TENANT_A)).isPresent();
    }

    @Test
    void findByTenantIdAndTableIdAndStatus_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndTableIdAndStatus(
                TENANT_A, TABLE_ID, SessionStatus.OPEN);

        assertThat(result).extracting(Session::getId).containsExactly(sessionA.getId());
    }

    @Test
    void findByTenantIdAndTableIdInAndStatus_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndTableIdInAndStatus(
                TENANT_B, List.of(TABLE_ID), SessionStatus.OPEN);

        assertThat(result).extracting(Session::getId).containsExactly(sessionB.getId());
    }

    @Test
    void findByTenantIdAndParticipants_UserId_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndParticipants_UserId(
                TENANT_A, "user-1");

        assertThat(result).extracting(Session::getId).containsExactly(sessionA.getId());
    }

    @Test
    void findByTenantIdAndJoinCodeAndStatus_doesNotResolveAnotherTenantsJoinCode() {
        assertThat(sessionRepository.findByTenantIdAndJoinCodeAndStatus(
                TENANT_B, "AB3CD", SessionStatus.OPEN))
                .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(sessionB.getId()));
        assertThat(sessionRepository.findByTenantIdAndJoinCodeAndStatus(
                UUID.randomUUID(), "AB3CD", SessionStatus.OPEN)).isEmpty();
    }
}
