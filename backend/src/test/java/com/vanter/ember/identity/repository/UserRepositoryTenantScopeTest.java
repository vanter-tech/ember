package com.vanter.ember.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code User} is deliberately excluded from {@code @TenantId}: it is looked up by email before
 * any tenant is bound (login and {@code jwtAuthFilter}), so discriminator filtering there would
 * reject every authenticated request. These tests pin that exclusion — tenant scoping for users
 * is enforced above the repository, never by Hibernate.
 */
class UserRepositoryTenantScopeTest extends AbstractTenantIsolationTest {

    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;

    @Override
    protected void deleteAll() {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    private User userSavedFor(UUID tenantId, String email) {
        Restaurant restaurant =
                restaurantRepository.save(
                        Restaurant.builder().name("Ember " + tenantId).slug("ember-" + tenantId).build());

        return readAs(
                tenantId,
                () ->
                        userRepository.save(
                                User.builder()
                                        .restaurantId(restaurant)
                                        .name("Owner")
                                        .email(email)
                                        .passwordHash("hash")
                                        .role(Role.ADMIN)
                                        .build()));
    }

    @Test
    void findByEmail_resolvesWithNoTenantBound() {
        userSavedFor(TENANT_A, "isolation-a@example.com");

        assertThat(userRepository.findByEmail("isolation-a@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("isolation-a@example.com")).isTrue();
    }

    @Test
    void findByEmail_isNotFilteredByTheBoundTenant() {
        userSavedFor(TENANT_A, "isolation-a@example.com");

        assertThat(readAs(TENANT_B, () -> userRepository.findByEmail("isolation-a@example.com")))
                .isPresent();
    }

    @Test
    void savedUser_keepsItsOwnRestaurantRegardlessOfTheBoundTenant() {
        Restaurant restaurant =
                restaurantRepository.save(Restaurant.builder().name("Ember B").slug("ember-b").build());

        User saved =
                readAs(
                        TENANT_A,
                        () ->
                                userRepository.save(
                                        User.builder()
                                                .restaurantId(restaurant)
                                                .name("Owner")
                                                .email("isolation-b@example.com")
                                                .passwordHash("hash")
                                                .role(Role.ADMIN)
                                                .build()));

        assertThat(saved.getRestaurantId().getId()).isEqualTo(restaurant.getId());
    }
}
