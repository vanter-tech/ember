package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A QR session token is signed with the same {@code jwt.secret} as a user token but its subject
 * is a session id, not an email. Presenting it as {@code Authorization: Bearer} used to make
 * {@code jwtAuthFilter} call {@code loadUserByUsername(sessionId)}, which threw an unhandled
 * {@code UsernameNotFoundException} inside the filter chain — outside {@code
 * GlobalExceptionHandler}'s reach — surfacing as a raw 500 instead of a clean 401
 * (AUDIT_BLUEPRINT.md F-13 / QA_SIMULATION_REPORT_v2.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class QrTokenAsBearerRejectionTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired RestaurantRepository restaurantRepository;

    @Test
    void qrTokenPresentedAsBearer_rejectedWithUnauthorized_notServerError() throws Exception {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Ember Test").slug("ember-qr-bearer-test").build());

        String qrToken = jwtService.generateToken(
                "session-" + java.util.UUID.randomUUID(),
                Map.of("rid", restaurant.getId().toString(), "typ", "session-qr"));

        mockMvc.perform(get("/admin/staff").header("Authorization", "Bearer " + qrToken))
                .andExpect(status().isUnauthorized());
    }
}
