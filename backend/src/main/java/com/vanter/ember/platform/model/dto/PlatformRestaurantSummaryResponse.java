package com.vanter.ember.platform.model.dto;

import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRestaurantSummaryResponse {
    private UUID id;
    private String name;
    private String slug;
    private RestaurantPlan plan;
    private RestaurantStatus status;
    private Instant createdAt;
    private HubStatus hubStatus;

    public static PlatformRestaurantSummaryResponse from(Restaurant restaurant) {
        return from(restaurant, null);
    }

    public static PlatformRestaurantSummaryResponse from(Restaurant restaurant, HubActivation activation) {
        Instant lastBeat = activation == null ? null : activation.getLastHeartbeatAt();
        return PlatformRestaurantSummaryResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .plan(restaurant.getPlan())
                .status(restaurant.getStatus())
                .createdAt(restaurant.getCreatedAt())
                .hubStatus(HubStatus.from(lastBeat, Instant.now()))
                .build();
    }
}
