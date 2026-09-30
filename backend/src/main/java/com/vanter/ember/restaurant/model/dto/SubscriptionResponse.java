package com.vanter.ember.restaurant.model.dto;

import com.vanter.ember.restaurant.model.BillingPeriod;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;

/**
 * What a restaurant admin sees under Settings &gt; Plan. Deliberately carries no prices: the
 * operator sets plan and dates by hand and price lists (and founder discounts) are not public.
 * Dates default to the account creation day and one month later until the operator records
 * or renews them.
 */
public record SubscriptionResponse(
        RestaurantPlan plan, RestaurantStatus status, Instant planStartedAt,
        BillingPeriod billingPeriod, Instant planPeriodEnd) {

    public static SubscriptionResponse from(Restaurant restaurant) {
        return new SubscriptionResponse(
                restaurant.getPlan(), restaurant.getStatus(), restaurant.effectivePlanStart(),
                restaurant.effectiveBillingPeriod(), restaurant.effectivePlanEnd());
    }
}
