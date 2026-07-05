package com.vanter.ember.identity.model.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AuthResponse {
    private String token;
    private String userId;
    private UUID restaurantId;
    private String name;
    private String role;
}
