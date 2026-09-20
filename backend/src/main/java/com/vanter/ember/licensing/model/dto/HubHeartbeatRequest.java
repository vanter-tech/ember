package com.vanter.ember.licensing.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HubHeartbeatRequest {

    @NotBlank(message = "licenseKey is required")
    private String licenseKey;

    @NotBlank(message = "hardwareFingerprint is required")
    private String hardwareFingerprint;

    /** Per-request random value the answer is signed over; absent on Hubs older than the signed heartbeat. */
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9-]*", message = "nonce must be alphanumeric")
    private String nonce;
}
