package com.vanter.ember.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.platform.model.PlatformOperator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * PlatformOperator has no tenant scoping (see {@link PlatformOperator}'s Javadoc), so this does
 * not extend {@code AbstractTenantIsolationTest} like the tenant-scoped repository tests do. The
 * {@link TenantIdentifierResolver} import is still required for the context to boot at all — the
 * SessionFactory is configured for multi-tenancy globally, so every other repository bean in the
 * slice (not just this one) needs a resolver present, even though this test never sets a tenant.
 *
 * <p>Tests run against H2 with {@code spring.flyway.enabled=false} (migrations are
 * PostgreSQL-specific), so V4's seed row never lands here — these tests exercise the repository
 * against rows they insert themselves, not the migration's seed.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class PlatformOperatorRepositoryTest {

    @Autowired PlatformOperatorRepository platformOperatorRepository;

    private PlatformOperator save(String email) {
        return platformOperatorRepository.saveAndFlush(
                PlatformOperator.builder()
                        .name("Operator")
                        .email(email)
                        .passwordHash("$2a$10$fakehashfakehashfakehashfakehashfakehashfakehash")
                        .build());
    }

    @Test
    void save_persistsAndGeneratesIdAndCreatedAt() {
        PlatformOperator operator = save("operator-" + UUID.randomUUID() + "@ember.local");

        assertThat(operator.getId()).isNotNull();
        assertThat(operator.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void findByEmail_returnsSavedOperator() {
        String email = "findme-" + UUID.randomUUID() + "@ember.local";
        save(email);

        assertThat(platformOperatorRepository.findByEmail(email)).isPresent();
    }

    @Test
    void findByEmail_returnsEmptyWhenNotFound() {
        assertThat(platformOperatorRepository.findByEmail("nobody@ember.local")).isEmpty();
    }

    @Test
    void save_rejectsDuplicateEmail() {
        String email = "dup-" + UUID.randomUUID() + "@ember.local";
        save(email);

        assertThatThrownBy(() -> save(email))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
