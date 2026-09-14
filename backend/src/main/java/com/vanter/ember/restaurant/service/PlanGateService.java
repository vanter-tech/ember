package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanGateService {

    private static final Map<RestaurantPlan, Integer> TABLE_LIMITS = Map.of(
            RestaurantPlan.FREE, 1,
            RestaurantPlan.STARTER, 10,
            RestaurantPlan.PRO, Integer.MAX_VALUE,
            RestaurantPlan.ENTERPRISE, Integer.MAX_VALUE);

    private final RestaurantRepository restaurantRepository;

    public RestaurantPlan currentPlan(UUID tenantId) {
        return restaurantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + tenantId))
                .getPlan();
    }

    /** Declaration order (FREE &lt; STARTER &lt; PRO &lt; ENTERPRISE) makes compareTo a correct
     *  tier comparison — never reorder the RestaurantPlan enum. */
    public void requirePlanAtLeast(UUID tenantId, RestaurantPlan minimum, String feature) {
        RestaurantPlan plan = currentPlan(tenantId);
        if (plan.compareTo(minimum) < 0) {
            throw new PlanLimitExceededException(feature, minimum, plan);
        }
    }

    public void requireTableCapacity(UUID tenantId, int requestedCount) {
        RestaurantPlan plan = currentPlan(tenantId);
        int limit = TABLE_LIMITS.get(plan);
        if (requestedCount > limit) {
            throw new PlanLimitExceededException("tables", plan, limit);
        }
    }
}
