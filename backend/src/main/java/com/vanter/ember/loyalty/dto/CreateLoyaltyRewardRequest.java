package com.vanter.ember.loyalty.dto;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLoyaltyRewardRequest(
        @NotBlank(message = "Name is required") @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @NotNull(message = "Required tier is required") LoyaltyTier requiredTier) {}
