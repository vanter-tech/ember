package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/restaurant")
@RequiredArgsConstructor
public class RestaurantAdminController {

    private final RestaurantService restaurantService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Restaurant get() {
        return restaurantService.getCurrent(TenantContextHolder.requireTenantId());
    }
}
