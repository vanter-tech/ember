package com.vanter.ember.loyalty.dto;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import jakarta.validation.constraints.Size;

/** Every field optional — a PATCH only applies the ones the caller actually sent. */
public record UpdateLoyaltyRewardRequest(
        @Size(max = 255) String name,
        @Size(max = 1000) String description,
        LoyaltyTier requiredTier,
        Boolean active) {}
