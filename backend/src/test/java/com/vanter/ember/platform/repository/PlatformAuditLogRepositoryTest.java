package com.vanter.ember.platform.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.platform.model.PlatformAuditLog;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * PlatformAuditLog has no tenant scoping (see {@link PlatformAuditLog}'s Javadoc), so this does
 * not extend {@code AbstractTenantIsolationTest} like the tenant-scoped repository tests do. The
 * {@link TenantIdentifierResolver} import is still required for the context to boot at all, same
 * reason documented on {@code PlatformOperatorRepositoryTest}.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class PlatformAuditLogRepositoryTest {

    @Autowired PlatformAuditLogRepository platformAuditLogRepository;

    private PlatformAuditLog save(UUID restaurantId) {
        return platformAuditLogRepository.saveAndFlush(
                PlatformAuditLog.builder()
                        .operatorId(UUID.randomUUID())
                        .operatorEmail("operator@ember.local")
                        .restaurantId(restaurantId)
                        .action("RESTAURANT_STATUS_CHANGED")
                        .oldValue("ACTIVE")
                        .newValue("SUSPENDED")
                        .build());
    }

    @Test
    void save_persistsAndGeneratesIdAndCreatedAt() {
        PlatformAuditLog entry = save(UUID.randomUUID());

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void save_allowsNullRestaurantId() {
        PlatformAuditLog entry = save(null);

        assertThat(entry.getId()).isNotNull();
        assertThat(entry.getRestaurantId()).isNull();
    }

    @Test
    void findById_returnsSavedEntry() {
        PlatformAuditLog saved = save(UUID.randomUUID());

        assertThat(platformAuditLogRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void findByRestaurantId_returnsOnlyMatchingEntries() {
        UUID restaurantId = UUID.randomUUID();
        PlatformAuditLog matching = save(restaurantId);
        save(UUID.randomUUID());

        var page = platformAuditLogRepository.findByRestaurantId(restaurantId, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(PlatformAuditLog::getId).containsExactly(matching.getId());
    }
}
