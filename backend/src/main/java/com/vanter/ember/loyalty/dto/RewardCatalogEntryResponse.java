package com.vanter.ember.loyalty.dto;

import com.vanter.ember.loyalty.model.LoyaltyTier;

public record RewardCatalogEntryResponse(
        Long id,
        String name,
        String description,
        LoyaltyTier requiredTier,
        boolean unlocked) {}
