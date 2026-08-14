package com.vanter.ember.analytics.controller;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AnalyticsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AnalyticsService analyticsService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void range_usesTenantFromContext() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getRange(TENANT_ID))
                .thenReturn(new AnalyticsRangeResponse(
                        LocalDateTime.of(2026, 1, 4, 19, 12), LocalDateTime.of(2026, 8, 14, 21, 40), 1284L));

        mockMvc.perform(get("/admin/analytics/range"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billCount").value(1284));

        verify(analyticsService).getRange(TENANT_ID);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void range_ignoresClientSuppliedRestaurantId() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getRange(TENANT_ID)).thenReturn(new AnalyticsRangeResponse(null, null, 0L));

        mockMvc.perform(get("/admin/analytics/range").param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billCount").value(0));

        verify(analyticsService).getRange(TENANT_ID);
        verify(analyticsService, never()).getRange(OTHER_TENANT_ID);
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void range_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/analytics/range"))
                .andExpect(status().isForbidden());

        verify(analyticsService, never()).getRange(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void range_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/analytics/range"))
                .andExpect(status().isConflict());

        verify(analyticsService, never()).getRange(any());
    }

    @Test
    void range_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/analytics/range"))
                .andExpect(status().isUnauthorized());

        verify(analyticsService, never()).getRange(any());
    }
}
