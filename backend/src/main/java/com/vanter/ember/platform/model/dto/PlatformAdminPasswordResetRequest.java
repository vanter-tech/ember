package com.vanter.ember.platform.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for {@code POST /platform/restaurants/{id}/reset-admin-password}. Same password-strength
 *  rule as every other password field in the app. */
public record PlatformAdminPasswordResetRequest(
        @NotBlank(message = "User id is required") String userId,
        @NotBlank(message = "New password is required")
                @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                        message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character")
                String newPassword) {}
