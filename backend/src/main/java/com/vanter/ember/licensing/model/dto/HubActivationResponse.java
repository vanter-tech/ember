package com.vanter.ember.licensing.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * F-15: never carries the admin's real password hash — the Hub generates its own local admin
 * password on first activation instead of receiving one from the cloud.
 */
@Data
@Builder
public class HubActivationResponse {
    private String name;
    private String slug;
    private String adminName;
    private String adminEmail;
}
