package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantRepositoryCountByStatusTest {

    @Autowired RestaurantRepository restaurantRepository;

    private void persist(String slug, RestaurantStatus status) {
        restaurantRepository.save(Restaurant.builder().name(slug).slug(slug).status(status).build());
    }

    @Test
    void countByStatus_countsOnlyThatStatus() {
        persist("a1", RestaurantStatus.ACTIVE);
        persist("a2", RestaurantStatus.ACTIVE);
        persist("s1", RestaurantStatus.SUSPENDED);
        persist("d1", RestaurantStatus.DELETED);

        assertThat(restaurantRepository.countByStatus(RestaurantStatus.ACTIVE)).isEqualTo(2);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED)).isEqualTo(1);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.DELETED)).isEqualTo(1);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.INACTIVE)).isZero();
    }
}
