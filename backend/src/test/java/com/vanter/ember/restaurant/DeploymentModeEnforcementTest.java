package com.vanter.ember.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeploymentModeEnforcementTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private Restaurant restaurant;
    private String waiterToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Mode Test Restaurant").slug("mode-test-" + UUID.randomUUID()).build());
        TenantContextHolder.setTenantId(restaurant.getId());
        User waiter = userRepository.save(User.builder()
                .name("Waiter").email("waiter@mode-test.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.WAITER).build());
        // Minted directly: /auth/login is rate limited per IP across the whole shared Spring
        // context, and a login per test here would starve the other integration tests.
        waiterToken = jwtService.generateToken("waiter@mode-test.com",
                Map.of("role", "WAITER", "userId", waiter.getId(), "rid", restaurant.getId()));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private MockHttpServletResponse login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn().getResponse();
    }

    private void switchTo(DeploymentMode mode) {
        restaurant.setDeploymentMode(mode);
        restaurantRepository.save(restaurant);
    }

    @Test
    void cloudTenant_requestSucceeds() throws Exception {
        mockMvc.perform(get("/dashboard/status").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
    }

    @Test
    void hubTenant_anExistingTokenStopsWorkingTheMomentTheModeChanges() throws Exception {
        switchTo(DeploymentMode.HUB);

        mockMvc.perform(get("/dashboard/status").header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void hubTenant_cannotLogIn_andTheAnswerIsTheSameAsForAWrongPassword() throws Exception {
        switchTo(DeploymentMode.HUB);

        MockHttpServletResponse hub = login("waiter@mode-test.com", PASSWORD);
        MockHttpServletResponse wrong = login("waiter@mode-test.com", "not-the-password");

        assertThat(hub.getStatus()).isEqualTo(wrong.getStatus()).isNotEqualTo(200);
        assertThat(hub.getContentAsString()).isEqualTo(wrong.getContentAsString());
    }

    @Test
    void hubTenant_publicBrandingIsNotFound() throws Exception {
        switchTo(DeploymentMode.HUB);

        mockMvc.perform(get("/public/restaurants/" + restaurant.getSlug() + "/branding"))
                .andExpect(status().isNotFound());
    }
}
