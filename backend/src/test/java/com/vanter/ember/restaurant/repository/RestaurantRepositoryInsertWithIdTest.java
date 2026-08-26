package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.Restaurant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Real-JPA regression test for {@link RestaurantRepository#insertWithId}, added after a
 * mocked-repository test failed to catch that {@code Restaurant.builder().id(...).build()} +
 * {@code save()} throws against a real Hibernate session (see
 * {@code HubProvisioningRunnerTest}, which mocks this repository and so cannot exercise actual
 * persistence). {@code @DataJpaTest} scans every {@code @Entity} project-wide, not just this
 * package, so any {@code @TenantId} entity elsewhere requires {@link TenantIdentifierResolver} to
 * be present for the multi-tenant Hibernate filter to resolve (see
 * {@code HubActivationRepositoryTest} for the same gotcha).
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantRepositoryInsertWithIdTest {

    @Autowired RestaurantRepository restaurantRepository;

    @Test
    void insertWithId_thenFindById_returnsRestaurantWithExactId() {
        UUID id = UUID.randomUUID();

        restaurantRepository.insertWithId(id, "Tenant Grill", "tenant-grill");

        Restaurant found = restaurantRepository.findById(id).orElseThrow();
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getName()).isEqualTo("Tenant Grill");
        assertThat(found.getSlug()).isEqualTo("tenant-grill");
    }
}
