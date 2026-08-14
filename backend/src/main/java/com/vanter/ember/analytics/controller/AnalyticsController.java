package com.vanter.ember.analytics.controller;

import com.vanter.ember.analytics.dto.AnalyticsRangeResponse;
import com.vanter.ember.analytics.service.AnalyticsService;
import com.vanter.ember.config.TenantContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
