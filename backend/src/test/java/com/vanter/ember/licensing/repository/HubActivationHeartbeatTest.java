package com.vanter.ember.licensing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.licensing.model.HubActivation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

// @DataJpaTest scans every @Entity project-wide; a @TenantId entity elsewhere needs
// TenantIdentifierResolver present for Hibernate's multi-tenant filter to resolve.
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class HubActivationHeartbeatTest {

    @Autowired HubActivationRepository repository;

    private UUID activated(String fp) {
        UUID restaurantId = UUID.randomUUID();
        repository.save(HubActivation.builder()
                .restaurantId(restaurantId).hardwareFingerprint(fp).activatedAt(Instant.now()).build());
        return restaurantId;
    }

    @Test
    void recordHeartbeatUpdatesOnlyThatRow() {
        UUID a = activated("fp-a");
        UUID b = activated("fp-b");
        Instant at = Instant.parse("2026-09-06T12:00:00Z");

        int rows = repository.recordHeartbeat(a, at, "203.0.113.7");

        assertThat(rows).isEqualTo(1);
        assertThat(repository.findByRestaurantId(a)).get()
                .satisfies(h -> {
                    assertThat(h.getLastHeartbeatAt()).isEqualTo(at);
                    assertThat(h.getLastHeartbeatIp()).isEqualTo("203.0.113.7");
                });
        assertThat(repository.findByRestaurantId(b)).get()
                .satisfies(h -> assertThat(h.getLastHeartbeatAt()).isNull());
    }

    @Test
    void recordHeartbeatReturnsZeroForUnknownRestaurant() {
        assertThat(repository.recordHeartbeat(UUID.randomUUID(), Instant.now(), "203.0.113.7")).isZero();
    }

    @Test
    void findByRestaurantIdInReturnsMatches() {
        UUID a = activated("fp-a");
        activated("fp-b");
        List<HubActivation> found = repository.findByRestaurantIdIn(List.of(a));
        assertThat(found).extracting(HubActivation::getRestaurantId).containsExactly(a);
    }
}
