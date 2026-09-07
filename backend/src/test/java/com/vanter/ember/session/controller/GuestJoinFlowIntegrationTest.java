package com.vanter.ember.session.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.dto.CreateSessionRequest;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "ember.ratelimit.enabled=false")
class GuestJoinFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired DiningTableRepository diningTableRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private String waiterToken;
    private UUID tableId;

    @BeforeEach
    void setUp() throws Exception {
        sessionRepository.deleteAll();
        diningTableRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Guest Test").slug("guest-test-" + UUID.randomUUID())
                .build());
        TenantContextHolder.setTenantId(restaurant.getId());

        userRepository.save(User.builder()
                .name("Waiter").email("waiter@guest.test").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode("password123")).role(Role.WAITER).build());
        waiterToken = login("waiter@guest.test", "password123");

        tableId = diningTableRepository.save(DiningTables.builder()
                .restaurantId(restaurant.getId()).tableNumber(3).isActive(true).build())
                .getId();
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
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String openSessionAndGetCode() throws Exception {
        MvcResult result = mockMvc.perform(post("/sessions")
                        .header("Authorization", "Bearer " + waiterToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(tableId, 4, null))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("joinCode").asText();
    }

    @Test
    void joinAsGuest_byCode_createsGuestUser_andJoins() throws Exception {
        String joinCode = openSessionAndGetCode();
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"" + joinCode + "\",\"name\":\"Ana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.session.participants[?(@.name=='Ana')]").exists());

        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);
        assertThat(userRepository.findAll())
                .anySatisfy(u -> {
                    assertThat(u.getGuest()).isTrue();
                    assertThat(u.getName()).isEqualTo("Ana");
                    assertThat(u.getRole()).isEqualTo(Role.CUSTOMER);
                });
    }

    @Test
    void joinAsGuest_blankName_getsGeneratedName() throws Exception {
        String joinCode = openSessionAndGetCode();

        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"" + joinCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.participants[0].name").isNotEmpty());
    }

    @Test
    void joinAsGuest_badCode_returns4xx_andCreatesNoUser() throws Exception {
        long usersBefore = userRepository.count();

        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"00000\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @Test
    void joinAsGuest_neitherIdentifier_returns400() throws Exception {
        mockMvc.perform(post("/sessions/join-as-guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
