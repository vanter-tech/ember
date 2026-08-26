package com.vanter.ember.licensing.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubActivationResponse {
    private String name;
    private String slug;
    private String adminName;
    private String adminEmail;
    private String adminPasswordHash;
}
