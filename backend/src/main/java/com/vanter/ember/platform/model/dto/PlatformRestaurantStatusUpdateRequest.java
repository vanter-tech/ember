package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.RestaurantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private RestaurantStatus status;
}
