package com.vanter.ember.platform.model.dto;

import com.vanter.ember.identity.model.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformRestaurantAdminResponse {
    private String id;
    private String name;
    private String email;

    public static PlatformRestaurantAdminResponse from(User user) {
        return PlatformRestaurantAdminResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
