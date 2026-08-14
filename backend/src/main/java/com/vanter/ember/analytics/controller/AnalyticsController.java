package com.vanter.ember.analytics.controller;

import com.vanter.ember.analytics.dto.AnalyticsProductsResponse;
import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.dto.AnalyticsSalesResponse;
import com.vanter.ember.analytics.dto.AnalyticsSummaryResponse;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.config.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics", description = "Business analytics for the current tenant (ADMIN only)")
@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @Operation(summary = "Get the tenant's billing-activity window, to bound dashboard date pickers")
    @GetMapping("/range")
    public AnalyticsRangeResponse getRange() {
        return analyticsService.getRange(TenantContextHolder.requireTenantId());
    }

    @Operation(
            summary = "Summary cards: total revenue, live active sessions and average order value",
            description =
                    "'from'/'to' are optional inclusive ISO date-times bounding the revenue and "
                            + "average-order-value figures; they default to the tenant's whole history "
                            + "up to now. The active-session count is always live and ignores them.")
    @GetMapping("/summary")
    public AnalyticsSummaryResponse getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        return analyticsService.getSummary(TenantContextHolder.requireTenantId(), from, to);
    }

    @Operation(
            summary = "Temporal sales series: revenue and settled orders bucketed over time",
            description =
                    "'granularity' is one of day|week|month|year (case-insensitive, defaults to day) "
                            + "and 'from'/'to' are the same optional inclusive window the summary uses. "
                            + "The returned series is gap-free, with quiet buckets reported as zeros.")
    @GetMapping("/sales")
    public AnalyticsSalesResponse getSales(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        return analyticsService.getSales(
                TenantContextHolder.requireTenantId(), granularity, from, to);
    }

    @Operation(
            summary = "Product performance: each menu item's and category's share of settled sales",
            description =
                    "'from'/'to' are the same optional inclusive window the summary uses. Products "
                            + "and categories come back ordered by revenue, with a running "
                            + "cumulative share for Pareto charts. 'limit' trims the product list to "
                            + "the top N; the totals and every share still cover the whole window.")
    @GetMapping("/products")
    public AnalyticsProductsResponse getProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to,
            @RequestParam(required = false) Integer limit) {
        return analyticsService.getProducts(TenantContextHolder.requireTenantId(), from, to, limit);
    }
}
