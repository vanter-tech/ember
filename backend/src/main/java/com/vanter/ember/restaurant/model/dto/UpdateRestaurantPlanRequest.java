package com.vanter.ember.restaurant.model.dto;

import com.vanter.ember.restaurant.model.RestaurantPlan;
import jakarta.validation.constraints.NotNull;

public record UpdateRestaurantPlanRequest(@NotNull RestaurantPlan plan) {
}
