package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.dto.PublicBrandingResponse;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.settings.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Public", description = "Unauthenticated, tenant-onboarding-facing endpoints")
@RestController
@RequestMapping("/public/restaurants")
@RequiredArgsConstructor
public class PublicRestaurantController {

    private final RestaurantRepository restaurantRepository;
    private final SettingService settingService;

    @Operation(summary = "Pre-login branding for a tenant landing page, by slug")
    @GetMapping("/{slug}/branding")
    public PublicBrandingResponse getBranding(@PathVariable String slug) {
        Restaurant restaurant = restaurantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("No restaurant found for slug: " + slug));

        // Slug -> id came from our own lookup above, not from client input, so binding the
        // tenant context here for this one settings read is as safe as the JWT-driven bind in
        // jwtAuthFilter (see config/TenantContextHolder javadoc).
        TenantContextHolder.setTenantId(restaurant.getId());
        try {
            var payload = settingService.getSettings(restaurant.getId()).getPayload();
            return PublicBrandingResponse.from(restaurant.getSlug(), restaurant.getName(), payload.getBranding());
        } finally {
            TenantContextHolder.clear();
        }
    }
}
