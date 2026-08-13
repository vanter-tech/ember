package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.dto.UpdateRestaurantPlanRequest;
import com.vanter.ember.restaurant.service.RestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "Tenant subscription plan management (ADMIN only)")
@RestController
@RequestMapping("/admin/restaurant")
@RequiredArgsConstructor
public class RestaurantAdminController {

    private final RestaurantService restaurantService;

    @Operation(summary = "Get the current tenant's subscription plan and account status (ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Restaurant get() {
        return restaurantService.getCurrent(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Change the current tenant's subscription plan (ADMIN)")
    @PatchMapping("/plan")
    @PreAuthorize("hasRole('ADMIN')")
    public Restaurant updatePlan(@Valid @RequestBody UpdateRestaurantPlanRequest request) {
        return restaurantService.updatePlan(TenantContextHolder.requireTenantId(), request.plan());
    }
}
