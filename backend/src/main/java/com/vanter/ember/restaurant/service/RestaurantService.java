package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.model.BillingPeriod;
import com.vanter.ember.restaurant.model.DeploymentMode;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
     * Applies a plan change. Only reachable via the platform-operator path
     * ({@link com.vanter.ember.platform.service.PlatformRestaurantService#updatePlan}) — the
     * tenant-facing self-service endpoint was removed once plan gating had real consequences.
     */
    public Restaurant updatePlan(UUID restaurantId, RestaurantPlan plan) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setPlan(plan);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Records the subscription dates. Only reachable via the platform-operator path
     * ({@link com.vanter.ember.platform.service.PlatformRestaurantService#updateSubscription}).
     */
    public Restaurant updateSubscription(
            UUID restaurantId, Instant planStartedAt, BillingPeriod billingPeriod, Instant planPeriodEnd) {
        if (planStartedAt != null && planPeriodEnd != null && !planPeriodEnd.isAfter(planStartedAt)) {
            throw new IllegalArgumentException("The period end must be after the plan start");
        }
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setPlanStartedAt(planStartedAt);
        restaurant.setBillingPeriod(billingPeriod);
        restaurant.setPlanPeriodEnd(planPeriodEnd);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Extends the plan by one {@code period}, counted from the current period end, or from
     * {@code now} when the plan is already overdue. Only reachable via the platform-operator path
     * ({@link com.vanter.ember.platform.service.PlatformRestaurantService#renewSubscription}).
     */
    public Restaurant renewSubscription(UUID restaurantId, BillingPeriod period, Instant now) {
        Restaurant restaurant = getCurrent(restaurantId);
        Instant currentEnd = restaurant.effectivePlanEnd();
        Instant base = currentEnd != null && currentEnd.isAfter(now) ? currentEnd : now.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        if (restaurant.getPlanStartedAt() == null) {
            restaurant.setPlanStartedAt(restaurant.effectivePlanStart());
        }
        restaurant.setBillingPeriod(period);
        restaurant.setPlanPeriodEnd(period.endFrom(base));
        return restaurantRepository.save(restaurant);
    }

    /**
     * Only reachable via the platform-operator path
     * ({@link com.vanter.ember.platform.service.PlatformRestaurantService#updateDeploymentMode}):
     * the mode decides who may sign in where, so the tenant never chooses it.
     */
    public Restaurant updateDeploymentMode(UUID restaurantId, DeploymentMode mode) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setDeploymentMode(mode);
        return restaurantRepository.save(restaurant);
    }

    /**
     * Deliberately never exposed through the tenant-facing API: status (e.g. SUSPENDED for
     * non-payment) must be set by a trusted operator, not the tenant's own ADMIN — otherwise a
     * suspended tenant could just call this to unsuspend itself and defeat the check in
     * {@code SecurityConfig#jwtAuthFilter}. Wired to {@code PATCH /platform/restaurants/{id}/status}
     * (EMB-PC-07), which never touches {@code TenantContextHolder} and audits the change.
     */
    public Restaurant updateStatus(UUID restaurantId, RestaurantStatus status) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setStatus(status);
        return restaurantRepository.save(restaurant);
    }

}
