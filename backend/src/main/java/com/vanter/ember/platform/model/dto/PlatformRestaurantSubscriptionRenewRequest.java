package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.BillingPeriod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantSubscriptionRenewRequest {

    @NotNull(message = "Billing period is required")
    private BillingPeriod billingPeriod;
}
