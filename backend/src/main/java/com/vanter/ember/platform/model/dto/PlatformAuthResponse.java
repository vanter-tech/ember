package com.vanter.ember.platform.model.dto;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlatformAuthResponse {
    private String token;
    private UUID operatorId;
    private String name;
    private String email;
}
