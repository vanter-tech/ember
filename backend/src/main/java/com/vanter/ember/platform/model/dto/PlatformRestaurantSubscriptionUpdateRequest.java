package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.BillingPeriod;
import java.time.Instant;
import lombok.Data;

/** Any field may be null to clear it (a tenant whose dates are not tracked yet). */
@Data
public class PlatformRestaurantSubscriptionUpdateRequest {

    private Instant planStartedAt;
    private BillingPeriod billingPeriod;
    private Instant planPeriodEnd;
}
