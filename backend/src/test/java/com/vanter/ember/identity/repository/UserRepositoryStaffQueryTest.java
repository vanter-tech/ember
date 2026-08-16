package com.vanter.ember.identity.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserRepositoryStaffQueryTest extends AbstractTenantIsolationTest {

    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;

    @Override
    protected void deleteAll() {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void newStaffUser_defaultsActiveTrueAndZeroPendingHours() {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember A").slug("ember-a-staff-defaults").build());

        User saved = readAs(TENANT_A, () -> userRepository.save(
                User.builder()
                        .restaurantId(restaurant)
                        .name("Waiter A")
                        .email("waiter-a-defaults@example.com")
                        .passwordHash("hash")
                        .role(Role.WAITER)
                        .build()));

        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPendingHours()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getShift()).isNull();
        assertThat(saved.getJobTitle()).isNull();
    }

    @Test
    void findByRestaurantId_IdAndRoleNot_returnsOnlyNonCustomerStaffForThatTenant() {
        Restaurant restaurantA = restaurantRepository.save(
                Restaurant.builder().name("Ember A").slug("ember-a-staff-query").build());
        Restaurant restaurantB = restaurantRepository.save(
                Restaurant.builder().name("Ember B").slug("ember-b-staff-query").build());

        User waiterA = readAs(TENANT_A, () -> userRepository.save(User.builder()
                .restaurantId(restaurantA).name("Waiter A").email("waiter-a2@example.com")
                .passwordHash("hash").role(Role.WAITER).build()));
        readAs(TENANT_A, () -> userRepository.save(User.builder()
                .restaurantId(restaurantA).name("Customer A").email("customer-a@example.com")
                .passwordHash("hash").role(Role.CUSTOMER).build()));
        readAs(TENANT_B, () -> userRepository.save(User.builder()
                .restaurantId(restaurantB).name("Waiter B").email("waiter-b@example.com")
                .passwordHash("hash").role(Role.WAITER).build()));

        List<User> staffForA = readAs(TENANT_A, () ->
                userRepository.findByRestaurantId_IdAndRoleNot(restaurantA.getId(), Role.CUSTOMER));

        assertThat(staffForA).extracting(User::getId).containsExactly(waiterA.getId());
    }
}
