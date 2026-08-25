package com.vanter.ember.session.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * {@code @Import(TenantIdentifierResolver.class)} is required even though {@link Session} itself
 * has no {@code @TenantId}: {@code @DataJpaTest}'s entity scan isn't scoped to this package, so it
 * picks up every {@code @TenantId} entity project-wide (MenuItem, Category, ...) and Hibernate
 * configures the whole SessionFactory for multi-tenancy as a result — it then requires a resolver
 * bean to exist, regardless of whether the entity under test needs tenancy. Every other
 * {@code @DataJpaTest} in this codebase already carries this same import for the same reason.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class SessionEntityTest {

    @Autowired EntityManager entityManager;

    @Test
    void persist_assignsIdAndRoundTripsEmbeddedLists() {
        Session session = Session.builder()
                .tenantId(UUID.randomUUID())
                .tableId(UUID.randomUUID())
                .waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(4)
                .participants(List.of(Participant.builder().userId("u1").name("Alice").build()))
                .createdAt(LocalDateTime.now())
                .build();

        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session reloaded = entityManager.find(Session.class, session.getId());

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(reloaded.getParticipants()).hasSize(1);
        assertThat(reloaded.getParticipants().get(0).getUserId()).isEqualTo("u1");
    }
}
