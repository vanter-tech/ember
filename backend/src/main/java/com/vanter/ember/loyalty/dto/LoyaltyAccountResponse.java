package com.vanter.ember.loyalty.dto;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import java.util.List;

public record LoyaltyAccountResponse(
        int totalPoints,
        LoyaltyTier tier,
        LoyaltyTier nextTier,
        Integer pointsToNextTier,
        List<RewardCatalogEntryResponse> rewards) {}
