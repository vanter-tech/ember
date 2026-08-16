package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRestaurantDetailResponse {
    private UUID id;
    private String name;
    private String slug;
    private RestaurantPlan plan;
    private RestaurantStatus status;
    private Instant createdAt;
    private List<PlatformRestaurantAdminResponse> admins;

    public static PlatformRestaurantDetailResponse from(Restaurant restaurant, List<PlatformRestaurantAdminResponse> admins) {
        return PlatformRestaurantDetailResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .plan(restaurant.getPlan())
                .status(restaurant.getStatus())
                .createdAt(restaurant.getCreatedAt())
                .admins(admins)
                .build();
    }
}
