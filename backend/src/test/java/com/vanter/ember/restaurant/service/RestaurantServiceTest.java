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
}
