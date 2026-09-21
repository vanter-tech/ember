package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.DeploymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantModeUpdateRequest {

    @NotNull(message = "Mode is required")
    private DeploymentMode mode;

    /** The restaurant's slug, typed by the operator as a deliberate confirmation. */
    @NotBlank(message = "Slug confirmation is required")
    private String confirmSlug;
}
