package com.vanter.ember.restaurant.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeploymentModeTest {

    private static Restaurant restaurant(DeploymentMode mode) {
        return Restaurant.builder().id(UUID.randomUUID()).name("R").slug("r").deploymentMode(mode).build();
    }

    @Test
    void newRestaurantsDefaultToCloud() {
        assertThat(Restaurant.builder().build().getDeploymentMode()).isEqualTo(DeploymentMode.CLOUD);
    }

    @Test
    void aHubRestaurantIsClosedToWeb_whenEnforced() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.HUB), true)).isTrue();
    }

    @Test
    void aCloudRestaurantIsNeverClosed() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.CLOUD), true)).isFalse();
    }

    @Test
    void insideTheHubItself_nothingIsClosed() {
        assertThat(DeploymentMode.isClosedToWeb(restaurant(DeploymentMode.HUB), false)).isFalse();
    }

    @Test
    void aMissingRestaurantIsNotClosedByThisRule() {
        assertThat(DeploymentMode.isClosedToWeb(null, true)).isFalse();
    }
}
