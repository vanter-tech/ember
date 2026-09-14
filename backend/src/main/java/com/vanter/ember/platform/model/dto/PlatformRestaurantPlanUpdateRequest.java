package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.RestaurantPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantPlanUpdateRequest {

    @NotNull(message = "Plan is required")
    private RestaurantPlan plan;
}
