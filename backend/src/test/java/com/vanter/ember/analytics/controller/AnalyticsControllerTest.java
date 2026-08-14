package com.vanter.ember.analytics.controller;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    private static AnalyticsSummaryResponse summaryFixture() {
        return new AnalyticsSummaryResponse(
                new BigDecimal("1520.50"),
                3L,
                new BigDecimal("42.25"),
                36L,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 14, 23, 59, 59));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_passesTheParsedWindowAndTenantFromContext() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(analyticsService.getSummary(TENANT_ID, from, to)).thenReturn(summaryFixture());

        mockMvc.perform(get("/admin/analytics/summary")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-14T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(1520.50))
                .andExpect(jsonPath("$.activeSessions").value(3))
                .andExpect(jsonPath("$.averageOrderValue").value(42.25))
                .andExpect(jsonPath("$.paidBillCount").value(36));

        verify(analyticsService).getSummary(TENANT_ID, from, to);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_withoutWindowParams_leavesTheDefaultingToTheService() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getSummary(TENANT_ID, null, null)).thenReturn(summaryFixture());

        mockMvc.perform(get("/admin/analytics/summary")).andExpect(status().isOk());

        verify(analyticsService).getSummary(TENANT_ID, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_ignoresClientSuppliedRestaurantId() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getSummary(TENANT_ID, null, null)).thenReturn(summaryFixture());

        mockMvc.perform(get("/admin/analytics/summary")
                        .param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk());

        verify(analyticsService).getSummary(TENANT_ID, null, null);
        verify(analyticsService, never()).getSummary(eq(OTHER_TENANT_ID), any(), any());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void summary_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/analytics/summary")).andExpect(status().isForbidden());

        verify(analyticsService, never()).getSummary(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void summary_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/analytics/summary")).andExpect(status().isConflict());

        verify(analyticsService, never()).getSummary(any(), any(), any());
    }

    @Test
    void summary_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/analytics/summary")).andExpect(status().isUnauthorized());

        verify(analyticsService, never()).getSummary(any(), any(), any());
    }
}
