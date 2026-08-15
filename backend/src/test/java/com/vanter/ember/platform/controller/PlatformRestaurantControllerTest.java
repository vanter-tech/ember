package com.vanter.ember.platform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.platform.config.PlatformSecurityConfig;
import com.vanter.ember.platform.model.dto.PlatformRestaurantAdminResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import com.vanter.ember.platform.service.PlatformRestaurantService;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformRestaurantController.class)
@Import({PlatformSecurityConfig.class, CorsConfig.class})
class PlatformRestaurantControllerTest {

    private static final String OPERATOR_EMAIL = "operator@ember.local";
    private static final String TOKEN = "valid-token";

    @Autowired MockMvc mockMvc;

    @MockBean PlatformRestaurantService platformRestaurantService;
    @MockBean PlatformJwtService platformJwtService;
    @MockBean PlatformOperatorDetailsService platformOperatorDetailsService;

    private void authenticate() {
        when(platformJwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(platformJwtService.extractSubject(TOKEN)).thenReturn(OPERATOR_EMAIL);
        UserDetails userDetails = User.builder()
                .username(OPERATOR_EMAIL)
                .password("ignored")
                .roles("PLATFORM_ADMIN")
                .build();
        when(platformOperatorDetailsService.loadUserByUsername(OPERATOR_EMAIL)).thenReturn(userDetails);
    }

    @Test
    void getAll_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(get("/platform/restaurants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returns200WithPageOfSummaries() throws Exception {
        authenticate();
        PlatformRestaurantSummaryResponse summary = PlatformRestaurantSummaryResponse.builder()
                .id(UUID.randomUUID())
                .name("Tenant Grill")
                .slug("tenant-grill")
                .plan(RestaurantPlan.PRO)
                .status(RestaurantStatus.ACTIVE)
                .createdAt(Instant.now())
                .build();
        when(platformRestaurantService.getAll(any())).thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/platform/restaurants").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("tenant-grill"));
    }

    @Test
    void getById_returns200WithAdmins() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        PlatformRestaurantDetailResponse detail = PlatformRestaurantDetailResponse.builder()
                .id(id)
                .name("Tenant Grill")
                .slug("tenant-grill")
                .plan(RestaurantPlan.PRO)
                .status(RestaurantStatus.ACTIVE)
                .createdAt(Instant.now())
                .admins(List.of(PlatformRestaurantAdminResponse.builder()
                        .id("u-1")
                        .name("Owner Admin")
                        .email("owner@tenant-grill.local")
                        .build()))
                .build();
        when(platformRestaurantService.getById(id)).thenReturn(detail);

        mockMvc.perform(get("/platform/restaurants/" + id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admins[0].email").value("owner@tenant-grill.local"));
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.getById(id))
                .thenThrow(new ResourceNotFoundException("Restaurant not found: " + id));

        mockMvc.perform(get("/platform/restaurants/" + id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_returns401WithoutAuthHeader() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(patch("/platform/restaurants/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateStatus_returns400OnMissingStatus() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();

        mockMvc.perform(patch("/platform/restaurants/" + id + "/status")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_returns200WithUpdatedSummary() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        PlatformRestaurantSummaryResponse summary = PlatformRestaurantSummaryResponse.builder()
                .id(id)
                .name("Tenant Grill")
                .slug("tenant-grill")
                .plan(RestaurantPlan.PRO)
                .status(RestaurantStatus.SUSPENDED)
                .createdAt(Instant.now())
                .build();
        when(platformRestaurantService.updateStatus(eq(id), eq(RestaurantStatus.SUSPENDED), eq(OPERATOR_EMAIL)))
                .thenReturn(summary);

        mockMvc.perform(patch("/platform/restaurants/" + id + "/status")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void updateStatus_returns404WhenRestaurantNotFound() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.updateStatus(eq(id), eq(RestaurantStatus.SUSPENDED), eq(OPERATOR_EMAIL)))
                .thenThrow(new ResourceNotFoundException("Restaurant not found: " + id));

        mockMvc.perform(patch("/platform/restaurants/" + id + "/status")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNotFound());
    }
}
