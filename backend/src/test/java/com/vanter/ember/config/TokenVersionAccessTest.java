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
 * Proves a JWT's {@code ver} claim is checked against the live {@code User.tokenVersion} on every
 * request — the F-17 mitigation: deactivation already killed a stolen token next-request
 * ({@link DeactivatedUserAccessTest}), but until now nothing could revoke a token for an account
 * that stays active (PIN change, an explicit "revoke sessions" action). Mirrors
 * {@code DeactivatedUserAccessTest}'s shape.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TokenVersionAccessTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private User activeAdmin(Restaurant restaurant, String email, int tokenVersion) {
        return userRepository.save(User.builder()
                .restaurantId(restaurant)
                .name("Ana")
                .email(email)
                .passwordHash(passwordEncoder.encode("Sup3r$ecret"))
                .role(Role.ADMIN)
                .active(true)
                .tokenVersion(tokenVersion)
                .build());
    }

    @Test
    void tokenWithStaleVersion_rejectedOnNextRequest() throws Exception {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-stale-ver-test").build());
        User user = activeAdmin(restaurant, "ana-stale-ver@test.com", 1);

        // Issued before the revoke (still carries the old ver=0).
        String token = jwtService.generateToken(user.getEmail(),
                Map.of("rid", restaurant.getId().toString(), "role", "ADMIN", "ver", 0));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithCurrentVersion_isAccepted() throws Exception {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-current-ver-test").build());
        User user = activeAdmin(restaurant, "ana-current-ver@test.com", 2);

        String token = jwtService.generateToken(user.getEmail(),
                Map.of("rid", restaurant.getId().toString(), "role", "ADMIN", "ver", 2));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void tokenWithNoVerClaim_isAcceptedWhenTheUsersVersionIsStillZero() throws Exception {
        // A token minted before this feature shipped, and a user never revoked since — both
        // default to 0, so deploying this change must not force a mass logout.
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-legacy-token-test").build());
        User user = activeAdmin(restaurant, "ana-legacy-token@test.com", 0);

        String token = jwtService.generateToken(user.getEmail(),
                Map.of("rid", restaurant.getId().toString(), "role", "ADMIN"));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
