package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks RestaurantService restaurantService;

    private Restaurant restaurant(UUID id) {
        return Restaurant.builder().id(id).name("Acme").slug("acme")
                .plan(RestaurantPlan.FREE).status(RestaurantStatus.ACTIVE).build();
    }

    @Test
    void getCurrent_unknownId_throwsResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantService.getCurrent(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updatePlan_savesNewPlanOnCurrentRestaurant() {
        UUID id = UUID.randomUUID();
        Restaurant restaurant = restaurant(id);
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Restaurant updated = restaurantService.updatePlan(id, RestaurantPlan.PRO);

        assertThat(updated.getPlan()).isEqualTo(RestaurantPlan.PRO);
    }

    @Test
    void updateStatus_savesNewStatusOnCurrentRestaurant() {
        UUID id = UUID.randomUUID();
        Restaurant restaurant = restaurant(id);
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Restaurant updated = restaurantService.updateStatus(id, RestaurantStatus.SUSPENDED);

        assertThat(updated.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);
    }

    @Test
    void updateSubscription_savesDatesAndPeriod() {
        UUID id = UUID.randomUUID();
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant(id)));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        java.time.Instant start = java.time.Instant.parse("2026-01-10T00:00:00Z");
        java.time.Instant end = java.time.Instant.parse("2027-01-10T00:00:00Z");

        Restaurant updated = restaurantService.updateSubscription(
                id, start, com.vanter.ember.restaurant.model.BillingPeriod.ANNUAL, end);

        assertThat(updated.getPlanStartedAt()).isEqualTo(start);
        assertThat(updated.getBillingPeriod()).isEqualTo(com.vanter.ember.restaurant.model.BillingPeriod.ANNUAL);
        assertThat(updated.getPlanPeriodEnd()).isEqualTo(end);
    }

    @Test
    void updateSubscription_rejectsAPeriodEndNotAfterTheStart() {
        UUID id = UUID.randomUUID();
        java.time.Instant start = java.time.Instant.parse("2026-01-10T00:00:00Z");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> restaurantService.updateSubscription(
                        id, start, com.vanter.ember.restaurant.model.BillingPeriod.ANNUAL, start))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateSubscription_allowsClearingEverything() {
        UUID id = UUID.randomUUID();
        Restaurant restaurant = restaurant(id);
        restaurant.setPlanStartedAt(java.time.Instant.now());
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Restaurant updated = restaurantService.updateSubscription(id, null, null, null);

        assertThat(updated.getPlanStartedAt()).isNull();
        assertThat(updated.getBillingPeriod()).isNull();
        assertThat(updated.getPlanPeriodEnd()).isNull();
    }

    private Restaurant renewable(UUID id, java.time.Instant end) {
        Restaurant restaurant = restaurant(id);
        restaurant.setPlanStartedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        restaurant.setPlanPeriodEnd(end);
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return restaurant;
    }

    @Test
    void renewSubscription_monthlyExtendsThirtyDaysFromTheCurrentEnd() {
        UUID id = UUID.randomUUID();
        renewable(id, java.time.Instant.parse("2026-02-10T00:00:00Z"));

        Restaurant renewed = restaurantService.renewSubscription(
                id, com.vanter.ember.restaurant.model.BillingPeriod.MONTHLY,
                java.time.Instant.parse("2026-02-05T00:00:00Z"));

        assertThat(renewed.getPlanPeriodEnd()).isEqualTo(java.time.Instant.parse("2026-03-12T00:00:00Z"));
        assertThat(renewed.getBillingPeriod()).isEqualTo(com.vanter.ember.restaurant.model.BillingPeriod.MONTHLY);
    }

    @Test
    void renewSubscription_overdueCountsFromNow() {
        UUID id = UUID.randomUUID();
        renewable(id, java.time.Instant.parse("2026-02-10T00:00:00Z"));

        Restaurant renewed = restaurantService.renewSubscription(
                id, com.vanter.ember.restaurant.model.BillingPeriod.MONTHLY,
                java.time.Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(renewed.getPlanPeriodEnd()).isEqualTo(java.time.Instant.parse("2026-05-01T00:00:00Z"));
    }

    @Test
    void renewSubscription_semestralAndAnnualAddCalendarMonths() {
        UUID id = UUID.randomUUID();
        java.time.Instant end = java.time.Instant.parse("2026-02-10T00:00:00Z");
        java.time.Instant now = java.time.Instant.parse("2026-02-01T00:00:00Z");
        renewable(id, end);

        assertThat(restaurantService.renewSubscription(
                id, com.vanter.ember.restaurant.model.BillingPeriod.SEMESTRAL, now).getPlanPeriodEnd())
                .isEqualTo(java.time.Instant.parse("2026-08-10T00:00:00Z"));

        renewable(id, end);
        assertThat(restaurantService.renewSubscription(
                id, com.vanter.ember.restaurant.model.BillingPeriod.ANNUAL, now).getPlanPeriodEnd())
                .isEqualTo(java.time.Instant.parse("2027-02-10T00:00:00Z"));
    }

    @Test
    void renewSubscription_withNoRecordedDatesStartsFromAccountCreation() {
        UUID id = UUID.randomUUID();
        Restaurant restaurant = restaurant(id);
        restaurant.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(restaurantRepository.findById(id)).thenReturn(Optional.of(restaurant));
        when(restaurantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Default end is creation + 30d = 2026-01-31; still in the future at "now", so extend from it.
        Restaurant renewed = restaurantService.renewSubscription(
                id, com.vanter.ember.restaurant.model.BillingPeriod.MONTHLY,
                java.time.Instant.parse("2026-01-20T00:00:00Z"));

        assertThat(renewed.getPlanStartedAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(renewed.getPlanPeriodEnd()).isEqualTo(java.time.Instant.parse("2026-03-02T00:00:00Z"));
    }

    @Test
    void effectiveDates_defaultToCreationAndThirtyDaysLater() {
        Restaurant restaurant = restaurant(UUID.randomUUID());
        restaurant.setCreatedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(restaurant.effectivePlanStart()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(restaurant.effectiveBillingPeriod())
                .isEqualTo(com.vanter.ember.restaurant.model.BillingPeriod.MONTHLY);
        assertThat(restaurant.effectivePlanEnd()).isEqualTo(java.time.Instant.parse("2026-01-31T00:00:00Z"));
    }
}
