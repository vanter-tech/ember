package com.vanter.ember.session.controller;


import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard", description = "Dashboard management")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/status")
    @Operation(summary = "Get live status of all tables for the authenticated tenant")
    public List<TableStatusResponse> getLiveTableStatus() {
        return dashboardService.getLiveStatus(TenantContextHolder.requireTenantId());
    }
}
