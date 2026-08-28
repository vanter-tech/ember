package com.vanter.ember.licensing.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HubHeartbeatRequest {

    @NotBlank(message = "licenseKey is required")
    private String licenseKey;

    @NotBlank(message = "hardwareFingerprint is required")
    private String hardwareFingerprint;
}
