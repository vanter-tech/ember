package com.vanter.ember.config;

import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves a deactivated staff account's already-issued JWT is rejected on the very next request,
 * not just at login: {@code EmberUserDetailsService} now marks it disabled, and
 * {@code SecurityConfig}'s {@code jwtAuthFilter} skips setting authentication when that's the
 * case, falling through to the existing {@code anyRequest().authenticated()} 401 path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeactivatedUserAccessTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void deactivatedUsersToken_rejectedOnNextRequest() throws Exception {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-deactivated-user-test").build());
        User user = userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name("Ana")
                .email("ana-deactivated@test.com")
                .passwordHash(passwordEncoder.encode("Sup3r$ecret"))
                .role(Role.ADMIN)
                .active(false)
                .build());

        String token = jwtService.generateToken(user.getEmail(),
                Map.of("rid", restaurant.getId().toString(), "role", "ADMIN"));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeUsersToken_isAccepted() throws Exception {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-active-user-test").build());
        User user = userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name("Ana")
                .email("ana-active@test.com")
                .passwordHash(passwordEncoder.encode("Sup3r$ecret"))
                .role(Role.ADMIN)
                .active(true)
                .build());

        String token = jwtService.generateToken(user.getEmail(),
                Map.of("rid", restaurant.getId().toString(), "role", "ADMIN"));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
