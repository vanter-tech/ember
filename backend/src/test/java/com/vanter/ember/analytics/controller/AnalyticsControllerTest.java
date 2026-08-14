package com.vanter.ember.analytics.controller;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.dto.CategoryPerformance;
import com.vanter.ember.analytics.dto.ProductPerformance;
import com.vanter.ember.analytics.dto.SalesBucket;
import com.vanter.ember.analytics.dto.SalesGranularity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private static AnalyticsSalesResponse salesFixture() {
        return new AnalyticsSalesResponse(
                SalesGranularity.DAY,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 14, 23, 59, 59),
                new BigDecimal("150.50"),
                3L,
                List.of(
                        new SalesBucket(
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 1),
                                new BigDecimal("100.00"),
                                2L),
                        new SalesBucket(
                                LocalDate.of(2026, 8, 2),
                                LocalDate.of(2026, 8, 2),
                                new BigDecimal("50.50"),
                                1L)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_passesTheGranularityAndParsedWindowFromTheRequest() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(analyticsService.getSales(TENANT_ID, "week", from, to)).thenReturn(salesFixture());

        mockMvc.perform(get("/admin/analytics/sales")
                        .param("granularity", "week")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-14T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.totalRevenue").value(150.50))
                .andExpect(jsonPath("$.paidBillCount").value(3))
                .andExpect(jsonPath("$.buckets.length()").value(2))
                .andExpect(jsonPath("$.buckets[0].bucketStart").value("2026-08-01"))
                .andExpect(jsonPath("$.buckets[0].bucketEnd").value("2026-08-01"))
                .andExpect(jsonPath("$.buckets[0].revenue").value(100.00))
                .andExpect(jsonPath("$.buckets[0].paidBillCount").value(2));

        verify(analyticsService).getSales(TENANT_ID, "week", from, to);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_withoutParams_leavesTheDefaultingToTheService() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getSales(TENANT_ID, null, null, null)).thenReturn(salesFixture());

        mockMvc.perform(get("/admin/analytics/sales")).andExpect(status().isOk());

        verify(analyticsService).getSales(TENANT_ID, null, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_ignoresClientSuppliedRestaurantId() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getSales(TENANT_ID, null, null, null)).thenReturn(salesFixture());

        mockMvc.perform(get("/admin/analytics/sales")
                        .param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk());

        verify(analyticsService).getSales(TENANT_ID, null, null, null);
        verify(analyticsService, never()).getSales(eq(OTHER_TENANT_ID), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void sales_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/analytics/sales")).andExpect(status().isForbidden());

        verify(analyticsService, never()).getSales(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/analytics/sales")).andExpect(status().isConflict());

        verify(analyticsService, never()).getSales(any(), any(), any(), any());
    }

    @Test
    void sales_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/analytics/sales")).andExpect(status().isUnauthorized());

        verify(analyticsService, never()).getSales(any(), any(), any(), any());
    }

    private static AnalyticsProductsResponse productsFixture() {
        return new AnalyticsProductsResponse(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 14, 23, 59, 59),
                new BigDecimal("150.00"),
                7L,
                2,
                List.of(
                        new ProductPerformance(
                                4L,
                                "Lomo saltado",
                                2L,
                                "Fondos",
                                5L,
                                new BigDecimal("100.00"),
                                new BigDecimal("66.67"),
                                new BigDecimal("66.67")),
                        new ProductPerformance(
                                9L,
                                "Chicha morada",
                                3L,
                                "Bebidas",
                                2L,
                                new BigDecimal("50.00"),
                                new BigDecimal("33.33"),
                                new BigDecimal("100.00"))),
                List.of(
                        new CategoryPerformance(
                                2L, "Fondos", 5L, new BigDecimal("100.00"), new BigDecimal("66.67"))));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void products_passesTheParsedWindowAndLimitFromTheRequest() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 14, 23, 59, 59);
        when(analyticsService.getProducts(TENANT_ID, from, to, 10)).thenReturn(productsFixture());

        mockMvc.perform(get("/admin/analytics/products")
                        .param("from", "2026-08-01T00:00:00")
                        .param("to", "2026-08-14T23:59:59")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").value(150.00))
                .andExpect(jsonPath("$.totalQuantity").value(7))
                .andExpect(jsonPath("$.productCount").value(2))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].name").value("Lomo saltado"))
                .andExpect(jsonPath("$.products[0].categoryName").value("Fondos"))
                .andExpect(jsonPath("$.products[0].quantitySold").value(5))
                .andExpect(jsonPath("$.products[0].revenueShare").value(66.67))
                .andExpect(jsonPath("$.products[1].cumulativeShare").value(100.00))
                .andExpect(jsonPath("$.categories[0].name").value("Fondos"));

        verify(analyticsService).getProducts(TENANT_ID, from, to, 10);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void products_withoutParams_leavesTheDefaultingToTheService() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getProducts(TENANT_ID, null, null, null)).thenReturn(productsFixture());

        mockMvc.perform(get("/admin/analytics/products")).andExpect(status().isOk());

        verify(analyticsService).getProducts(TENANT_ID, null, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void products_ignoresClientSuppliedRestaurantId() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getProducts(TENANT_ID, null, null, null)).thenReturn(productsFixture());

        mockMvc.perform(get("/admin/analytics/products")
                        .param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk());

        verify(analyticsService).getProducts(TENANT_ID, null, null, null);
        verify(analyticsService, never()).getProducts(eq(OTHER_TENANT_ID), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void products_forbiddenForNonAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/analytics/products")).andExpect(status().isForbidden());

        verify(analyticsService, never()).getProducts(any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void products_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/admin/analytics/products")).andExpect(status().isConflict());

        verify(analyticsService, never()).getProducts(any(), any(), any(), any());
    }

    @Test
    void products_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/analytics/products")).andExpect(status().isUnauthorized());

        verify(analyticsService, never()).getProducts(any(), any(), any(), any());
    }
}
