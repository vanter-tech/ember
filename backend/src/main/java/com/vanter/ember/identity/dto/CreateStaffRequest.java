package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Email is required")
                @Email(message = "Email must be valid")
                String email,
        @NotBlank(message = "Password is required")
                @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
                @Pattern(
                        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
                        message = "Password must contain at least one uppercase letter, one lowercase letter, "
                                + "one digit, and one special character")
                String password,
        @NotNull(message = "Role is required") Role role) {}
