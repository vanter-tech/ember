package com.vanter.ember.licensing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.licensing.model.HubActivation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class HubActivationRepositoryTest {

    @Autowired HubActivationRepository repository;

    @Test
    void findByRestaurantId_returnsEmptyWhenNoneExists() {
        assertThat(repository.findByRestaurantId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByRestaurantId_returnsSavedActivation() {
        UUID restaurantId = UUID.randomUUID();
        repository.save(HubActivation.builder()
                .restaurantId(restaurantId)
                .hardwareFingerprint("fp-1")
                .activatedAt(Instant.now())
                .build());

        assertThat(repository.findByRestaurantId(restaurantId))
                .isPresent()
                .get()
                .extracting(HubActivation::getHardwareFingerprint)
                .isEqualTo("fp-1");
    }
}
