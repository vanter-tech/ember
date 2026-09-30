package com.vanter.ember.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack check (real JWT, real Hibernate session lifecycle, real JSON serialization — no mocked
 * service) of what the admin's "edit staff" modal does when it saves: PATCH the profile, then PATCH
 * the role. The unit/controller tests mock {@code UserAdminService}, so they never see what the
 * controller does with the entity the service returns once the persistence session is closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminStaffRoleChangeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired UserRepository userRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "password123";

    private Restaurant restaurant;
    private String adminToken;
    private String waiterId;
    private String suffix;

    @BeforeEach
    void setUp() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Role Change " + suffix).slug("role-change-" + suffix)
                .plan(RestaurantPlan.STARTER).build());
        TenantContextHolder.setTenantId(restaurant.getId());

        User admin = userRepository.save(User.builder()
                .name("Admin").email("admin-" + suffix + "@rc.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).build());
        waiterId = userRepository.save(User.builder()
                .name("Waiter").email("waiter-" + suffix + "@rc.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.WAITER).build()).getId();

        adminToken = tokenFor(admin);
    }

    /**
     * Same claims AuthService puts in a real login token. Minted directly instead of POST /auth/login:
     * that endpoint has a per-IP sliding-window limit shared by the whole test JVM, and enough logins
     * across integration classes made other tests (E2EOrderFlowTest) receive 429.
     */
    private String tokenFor(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId());
        claims.put("ver", user.getTokenVersion());
        claims.put("rid", restaurant.getId());
        return jwtService.generateToken(user.getEmail(), claims);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void changingAStaffRole_answers200_andPersistsIt() throws Exception {
        mockMvc.perform(patch("/admin/users/" + waiterId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"KITCHEN\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(waiterId).orElseThrow().getRole()).isEqualTo(Role.KITCHEN);
    }

    @Test
    void savingTheEditModal_profileThenRole_bothAnswer200() throws Exception {
        // Exactly what EditStaffModal sends: every profile field (empty strings included), then the role.
        mockMvc.perform(patch("/admin/staff/" + waiterId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Waiter\",\"email\":\"waiter-" + suffix + "@rc.com\","
                                + "\"shift\":\"\",\"contractType\":\"\",\"location\":\"\",\"active\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/admin/users/" + waiterId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ACCOUNTANT\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(waiterId).orElseThrow().getRole()).isEqualTo(Role.ACCOUNTANT);
    }
}
