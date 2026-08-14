package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    /**
     * Joins an existing restaurant by slug, or creates a new one when no slug is given.
     */
    public Restaurant createOrJoin(String restaurantName, String restaurantSlug, String fallbackNameSeed) {
        if (StringUtils.hasText(restaurantSlug)) {
            return restaurantRepository.findBySlug(slugify(restaurantSlug))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No restaurant found for slug: " + restaurantSlug));
        }

        String name = StringUtils.hasText(restaurantName)
                ? restaurantName
                : fallbackNameSeed + "'s Restaurant";

        Restaurant restaurant = Restaurant.builder()
                .name(name)
                .slug(uniqueSlug(name))
                .build();

        return restaurantRepository.save(restaurant);
    }

    public Restaurant getCurrent(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
    }

    /**
     * Resolves the restaurant a customer is currently visiting (QR/table-code landing page,
     * carried through login/register as {@code restaurantSlug}) — customers are not bound to a
     * single restaurant, so this is looked up fresh on every login/register instead of read off
     * {@code User.restaurantId}.
     */
    public Restaurant getBySlug(String slug) {
        return restaurantRepository.findBySlug(slugify(slug))
                .orElseThrow(() -> new ResourceNotFoundException("No restaurant found for slug: " + slug));
    }

    /**
     * Self-service plan change (upgrade/downgrade) — safe for the tenant's own ADMIN to trigger.
     */
    public Restaurant updatePlan(UUID restaurantId, RestaurantPlan plan) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setPlan(plan);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Deliberately not exposed through any controller: status (e.g. SUSPENDED for non-payment)
     * must be set by a trusted billing system, not the tenant's own ADMIN — otherwise a suspended
     * tenant could just call this to unsuspend itself and defeat the check in
     * {@code SecurityConfig#jwtAuthFilter}. This is the hook a future billing webhook wires into.
     */
    public Restaurant updateStatus(UUID restaurantId, RestaurantStatus status) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setStatus(status);
        return restaurantRepository.save(restaurant);
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        while (restaurantRepository.existsBySlug(candidate)) {
            candidate = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private String slugify(String value) {
        String slug = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return StringUtils.hasText(slug) ? slug : "restaurant";
    }
}
