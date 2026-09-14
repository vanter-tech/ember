package com.vanter.ember.restaurant.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanGateServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks PlanGateService planGateService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private Restaurant restaurantOnPlan(RestaurantPlan plan) {
        return Restaurant.builder().id(TENANT_ID).name("Acme").slug("acme").plan(plan).build();
    }

    @Test
    void requirePlanAtLeast_throwsWhenBelowMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        assertThatThrownBy(() ->
                planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose"))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void requirePlanAtLeast_allowsWhenAtMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.STARTER)));

        planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose");
    }

    @Test
    void requirePlanAtLeast_allowsWhenAboveMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.ENTERPRISE)));

        planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose");
    }

    @Test
    void requireTableCapacity_throwsWhenOverLimit() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        assertThatThrownBy(() -> planGateService.requireTableCapacity(TENANT_ID, 2))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void requireTableCapacity_allowsExactlyAtLimit() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        planGateService.requireTableCapacity(TENANT_ID, 1);
    }

    @Test
    void requireTableCapacity_unlimitedOnProAndEnterprise() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.PRO)));

        planGateService.requireTableCapacity(TENANT_ID, 500);
    }
}
