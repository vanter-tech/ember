package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.RestaurantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantAdminController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class RestaurantAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RestaurantService restaurantService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private Restaurant restaurant(RestaurantPlan plan, RestaurantStatus status) {
        return Restaurant.builder().id(TENANT_ID).name("Acme").slug("acme")
                .plan(plan).status(status).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void get_returnsCurrentTenantPlanAndStatus() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(restaurantService.getCurrent(TENANT_ID))
                .thenReturn(restaurant(RestaurantPlan.PRO, RestaurantStatus.ACTIVE));

        mockMvc.perform(get("/admin/restaurant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void get_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/restaurant"))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/restaurant"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void subscription_returnsPlanAndDatesButNoPrices() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        Restaurant restaurant = restaurant(RestaurantPlan.STARTER, RestaurantStatus.ACTIVE);
        restaurant.setPlanStartedAt(java.time.Instant.parse("2026-01-10T00:00:00Z"));
        restaurant.setBillingPeriod(com.vanter.ember.restaurant.model.BillingPeriod.SEMESTRAL);
        restaurant.setPlanPeriodEnd(java.time.Instant.parse("2026-07-10T00:00:00Z"));
        when(restaurantService.getCurrent(TENANT_ID)).thenReturn(restaurant);

        mockMvc.perform(get("/admin/restaurant/subscription"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("STARTER"))
                .andExpect(jsonPath("$.billingPeriod").value("SEMESTRAL"))
                .andExpect(jsonPath("$.planPeriodEnd").exists())
                .andExpect(jsonPath("$.price").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void subscription_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/restaurant/subscription")).andExpect(status().isForbidden());
    }
}
