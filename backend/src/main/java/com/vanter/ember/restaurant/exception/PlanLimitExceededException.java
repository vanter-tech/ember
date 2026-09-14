package com.vanter.ember.restaurant.exception;

import com.vanter.ember.restaurant.model.RestaurantPlan;

/** Thrown by {@link com.vanter.ember.restaurant.service.PlanGateService} when the tenant's plan
 *  doesn't cover a gated feature — mapped to 402 by GlobalExceptionHandler. Two shapes: a tier
 *  gate (requiredPlan set, limit null) and a count gate (limit set, requiredPlan null). */
public class PlanLimitExceededException extends RuntimeException {

    private final String feature;
    private final RestaurantPlan requiredPlan;
    private final RestaurantPlan currentPlan;
    private final Integer limit;

    public PlanLimitExceededException(String feature, RestaurantPlan requiredPlan, RestaurantPlan currentPlan) {
        super("Feature '" + feature + "' requires plan " + requiredPlan
                + " or higher (current: " + currentPlan + ")");
        this.feature = feature;
        this.requiredPlan = requiredPlan;
        this.currentPlan = currentPlan;
        this.limit = null;
    }

    public PlanLimitExceededException(String feature, RestaurantPlan currentPlan, int limit) {
        super("Feature '" + feature + "' is limited to " + limit + " on plan " + currentPlan);
        this.feature = feature;
        this.requiredPlan = null;
        this.currentPlan = currentPlan;
        this.limit = limit;
    }

    public String getFeature() {
        return feature;
    }

    public RestaurantPlan getRequiredPlan() {
        return requiredPlan;
    }

    public RestaurantPlan getCurrentPlan() {
        return currentPlan;
    }

    public Integer getLimit() {
        return limit;
    }
}
