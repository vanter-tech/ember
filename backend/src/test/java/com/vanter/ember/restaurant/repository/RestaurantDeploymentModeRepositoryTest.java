package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantDeploymentModeRepositoryTest {

    @Autowired RestaurantRepository restaurantRepository;

    @Test
    void aNewRestaurantIsStoredAsCloudByDefault() {
        Restaurant saved = restaurantRepository.saveAndFlush(
                Restaurant.builder().name("Web Grill").slug("web-grill").build());

        assertThat(restaurantRepository.findById(saved.getId()).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.CLOUD);
    }

    @Test
    void hubModeRoundTrips() {
        Restaurant saved = restaurantRepository.saveAndFlush(Restaurant.builder()
                .name("Hub Grill").slug("hub-grill").deploymentMode(DeploymentMode.HUB).build());

        assertThat(restaurantRepository.findById(saved.getId()).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.HUB);
    }

    @Test
    void insertWithId_seedsTheHubsOwnRestaurantAsHub() {
        UUID id = UUID.randomUUID();

        restaurantRepository.insertWithId(id, "Local Hub", "local-hub");

        assertThat(restaurantRepository.findById(id).orElseThrow().getDeploymentMode())
                .isEqualTo(DeploymentMode.HUB);
    }
}
