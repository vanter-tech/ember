package com.vanter.ember.identity.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    /** Slug of the restaurant being visited; required for CUSTOMER logins, ignored otherwise. */
    private String restaurantSlug;
}
