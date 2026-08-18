package com.vanter.ember.loyalty.dto;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import java.time.LocalDateTime;

public record LoyaltyRewardResponse(
        Long id,
        String name,
        String description,
        LoyaltyTier requiredTier,
        boolean active,
        LocalDateTime createdAt) {}
