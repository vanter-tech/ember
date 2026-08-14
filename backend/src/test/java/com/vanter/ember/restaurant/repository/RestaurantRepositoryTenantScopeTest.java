package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.restaurant.model.Restaurant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code Restaurant} is the tenant registry itself, so it carries no {@code @TenantId}: slugs are
 * resolved during registration and login, before any tenant is bound. These tests pin that — if
 * the registry ever became discriminator-filtered, sign-up and sign-in would break.
 */
class RestaurantRepositoryTenantScopeTest extends AbstractTenantIsolationTest {

    @Autowired RestaurantRepository restaurantRepository;

    @Override
    protected void deleteAll() {
        restaurantRepository.deleteAll();
    }

    @Test
    void findBySlug_resolvesWithNoTenantBound() {
        restaurantRepository.save(Restaurant.builder().name("Ember A").slug("ember-a").build());

        assertThat(restaurantRepository.findBySlug("ember-a")).isPresent();
        assertThat(restaurantRepository.existsBySlug("ember-a")).isTrue();
    }

    @Test
    void findBySlug_isNotFilteredByTheBoundTenant() {
        asTenant(
                TENANT_A,
                () -> restaurantRepository.save(Restaurant.builder().name("Ember A").slug("ember-a").build()));

        assertThat(readAs(TENANT_B, () -> restaurantRepository.findBySlug("ember-a"))).isPresent();
    }

    @Test
    void findById_isNotFilteredByTheBoundTenant() {
        Restaurant saved =
                restaurantRepository.save(Restaurant.builder().name("Ember A").slug("ember-a").build());

        assertThat(readAs(TENANT_B, () -> restaurantRepository.findById(saved.getId()))).isPresent();
    }
}
