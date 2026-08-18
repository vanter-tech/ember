package com.vanter.ember.loyalty.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.loyalty.dto.CreateLoyaltyRewardRequest;
import com.vanter.ember.loyalty.dto.LoyaltyRewardResponse;
import com.vanter.ember.loyalty.dto.UpdateLoyaltyRewardRequest;
import com.vanter.ember.loyalty.model.LoyaltyReward;
import com.vanter.ember.loyalty.repository.LoyaltyRewardRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Admin CRUD over the tier-gated reward catalog. Catalog-display only in v1 — no points cost, no
 * redemption. Tenant scoping on {@link #update} relies on {@link LoyaltyReward}'s {@code
 * @TenantId} filter, same as {@code CategoryService}/{@code MenuItemService}; no manual re-check
 * needed.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyRewardService {

    private final LoyaltyRewardRepository loyaltyRewardRepository;

    public LoyaltyRewardResponse create(CreateLoyaltyRewardRequest request) {
        LoyaltyReward reward = loyaltyRewardRepository.save(LoyaltyReward.builder()
                .name(request.name())
                .description(request.description())
                .requiredTier(request.requiredTier())
                .createdAt(LocalDateTime.now())
                .build());
        return toResponse(reward);
    }

    public List<LoyaltyRewardResponse> list(UUID tenantId) {
        return loyaltyRewardRepository.findByTenantId(tenantId).stream()
                .map(LoyaltyRewardService::toResponse)
                .toList();
    }

    public LoyaltyRewardResponse update(Long id, UpdateLoyaltyRewardRequest request) {
        LoyaltyReward reward = loyaltyRewardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reward not found: " + id));

        if (request.name() != null) reward.setName(request.name());
        if (request.description() != null) reward.setDescription(request.description());
        if (request.requiredTier() != null) reward.setRequiredTier(request.requiredTier());
        if (request.active() != null) reward.setActive(request.active());

        return toResponse(loyaltyRewardRepository.save(reward));
    }

    private static LoyaltyRewardResponse toResponse(LoyaltyReward reward) {
        return new LoyaltyRewardResponse(
                reward.getId(),
                reward.getName(),
                reward.getDescription(),
                reward.getRequiredTier(),
                reward.isActive(),
                reward.getCreatedAt());
    }
}
