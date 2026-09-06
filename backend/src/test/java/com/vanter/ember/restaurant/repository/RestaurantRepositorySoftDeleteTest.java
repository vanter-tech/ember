package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

// @DataJpaTest scans every @Entity project-wide; a @TenantId entity elsewhere needs
// TenantIdentifierResolver present for Hibernate's multi-tenant filter to resolve
// (see RestaurantRepositoryInsertWithIdTest / HubActivationRepositoryTest).
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantRepositorySoftDeleteTest {

    @Autowired RestaurantRepository restaurantRepository;

    private Restaurant persisted(String slug, RestaurantStatus status) {
        return restaurantRepository.save(Restaurant.builder().name(slug).slug(slug).status(status).build());
    }

    @Test
    void deletedColumnsRoundTrip() {
        Restaurant r = persisted("round-trip", RestaurantStatus.SUSPENDED);
        UUID operator = UUID.randomUUID();
        r.setStatus(RestaurantStatus.DELETED);
        r.setDeletedAt(Instant.parse("2026-09-06T12:00:00Z"));
        r.setDeletedBy(operator);
        restaurantRepository.saveAndFlush(r);
        restaurantRepository.findById(r.getId()).ifPresentOrElse(loaded -> {
            assertThat(loaded.getStatus()).isEqualTo(RestaurantStatus.DELETED);
            assertThat(loaded.getDeletedAt()).isEqualTo(Instant.parse("2026-09-06T12:00:00Z"));
            assertThat(loaded.getDeletedBy()).isEqualTo(operator);
        }, () -> { throw new AssertionError("restaurant not found"); });
    }

    @Test
    void findByStatusNotExcludesDeleted() {
        persisted("alive-1", RestaurantStatus.ACTIVE);
        persisted("alive-2", RestaurantStatus.SUSPENDED);
        persisted("gone", RestaurantStatus.DELETED);

        var page = restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Restaurant::getSlug)
                .containsExactlyInAnyOrder("alive-1", "alive-2");
    }
}
