package com.vanter.ember.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Admin-assigned quick-login PIN for a staff account. Unlike the retired self-service flow, the
 * admin never supplies the target user's password — the ADMIN role plus the tenant-scope guard in
 * {@code UserAdminService} is the authorization.
 */
public record AdminSetPinRequest(
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "^\\d{4,6}$", message = "PIN must be 4 to 6 digits")
        String pin) {}
