package com.vanter.ember.session.repository;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @Import(TenantIdentifierResolver.class)}: {@code @DataJpaTest}'s entity scan isn't
 * scoped to this package, so it also picks up every {@code @TenantId} entity project-wide
 * (MenuItem, Category, ...) and Hibernate configures the whole SessionFactory for multi-tenancy
 * as a result — it then requires a resolver bean regardless of whether {@link Session} itself
 * uses {@code @TenantId} (it doesn't).
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class SessionRepositoryTest {

    private static final UUID TABLE_1_ID = UUID.randomUUID();
    private static final UUID TABLE_2_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void save_persistsSession() {
        Session session = Session.builder()
                .tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(4)
                .createdAt(LocalDateTime.now())
                .build();

        Session saved = sessionRepository.save(session);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByTableIdAndStatus_returnsMatchingSessions() {
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.CLOSED).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_2_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByTenantIdAndTableIdAndStatus(
                TENANT_ID, TABLE_1_ID, SessionStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableId()).isEqualTo(TABLE_1_ID);
        assertThat(result.get(0).getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByParticipants_UserId_returnsSessionsForUser() {
        Participant alice = Participant.builder().userId("user-1").name("Alice").build();
        Participant bob = Participant.builder().userId("user-2").name("Bob").build();

        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(alice))
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_2_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(bob))
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByTenantIdAndParticipants_UserId(TENANT_ID, "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParticipants().get(0).getUserId()).isEqualTo("user-1");
    }
}
