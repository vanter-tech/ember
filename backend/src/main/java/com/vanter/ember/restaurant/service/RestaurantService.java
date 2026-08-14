package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;

    public Restaurant getCurrent(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
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

}
