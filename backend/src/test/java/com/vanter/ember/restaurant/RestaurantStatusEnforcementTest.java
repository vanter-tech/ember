package com.vanter.ember.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RestaurantStatusEnforcementTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private Restaurant restaurant;
    private String waiterToken;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        String password = "password123";
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Status Test Restaurant").slug("status-test-" + UUID.randomUUID())
                .build());

        TenantContextHolder.setTenantId(restaurant.getId());
        userRepository.save(User.builder()
                .name("Waiter").email("waiter@status-test.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(password)).role(Role.WAITER).build());

        waiterToken = login("waiter@status-test.com", password);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private String login(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    @Test
    void activeTenant_requestSucceeds() throws Exception {
        mockMvc.perform(get("/dashboard/status")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isOk());
    }

    @Test
    void suspendedTenant_requestIsForbidden() throws Exception {
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        restaurantRepository.save(restaurant);

        mockMvc.perform(get("/dashboard/status")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveTenant_requestIsForbidden() throws Exception {
        restaurant.setStatus(RestaurantStatus.INACTIVE);
        restaurantRepository.save(restaurant);

        mockMvc.perform(get("/dashboard/status")
                        .header("Authorization", "Bearer " + waiterToken))
                .andExpect(status().isForbidden());
    }
}
