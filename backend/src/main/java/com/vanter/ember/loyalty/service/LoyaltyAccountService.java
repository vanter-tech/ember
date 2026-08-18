package com.vanter.ember.loyalty.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.loyalty.dto.LoyaltyAccountResponse;
import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;
import com.vanter.ember.loyalty.dto.RewardCatalogEntryResponse;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyTier;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import com.vanter.ember.loyalty.repository.LoyaltyRewardRepository;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns {@link LoyaltyAccount} persistence. {@link #findOrCreate} is the one creation path — called
 * from the table-join hook as the primary trigger, and again (idempotently) from the accrual
 * listener as a safety net, per design decision #2. {@link #credit} is the one path that bumps
 * {@code totalPoints} — always paired with a {@link LoyaltyTransaction} ledger row so the two can
 * never drift out of sync.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyAccountService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyRewardRepository loyaltyRewardRepository;
    private final SettingService settingService;
    private final LoyaltyService loyaltyService;

    /**
     * Caller's own account for {@code /loyalty/accounts/me} — 404s if the customer has never
     * joined a table at this tenant (accounts are created lazily on table-join, decision #2).
     */
    public LoyaltyAccountResponse getMyAccount(UUID tenantId, String userId) {
        LoyaltyAccount account = loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No loyalty account for this restaurant yet"));

        SettingsPayload.LoyaltySettings settings =
                settingService.getSettings(tenantId).getPayload().getLoyalty();
        LoyaltyTier tier = loyaltyService.computeTier(account.getTotalPoints(), settings);
        LoyaltyTier nextTier = loyaltyService.nextTier(tier);
        Integer pointsToNextTier =
                loyaltyService.pointsToNextTier(account.getTotalPoints(), nextTier, settings);

        var rewards = loyaltyRewardRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(reward -> new RewardCatalogEntryResponse(
                        reward.getId(),
                        reward.getName(),
                        reward.getDescription(),
                        reward.getRequiredTier(),
                        tier.ordinal() >= reward.getRequiredTier().ordinal()))
                .toList();

        return new LoyaltyAccountResponse(
                account.getTotalPoints(), tier, nextTier, pointsToNextTier, rewards);
    }

    /**
     * Caller's most recent visits (up to 20, newest first) for {@code
     * /loyalty/accounts/me/visits} — 404s the same way {@link #getMyAccount} does if the
     * customer has never joined a table at this tenant.
     */
    public List<LoyaltyVisitResponse> getMyVisits(UUID tenantId, String userId) {
        LoyaltyAccount account = loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No loyalty account for this restaurant yet"));

        return loyaltyTransactionRepository
                .findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .limit(20)
                .map(tx -> new LoyaltyVisitResponse(tx.getCreatedAt(), tx.getAmount(), tx.getPoints()))
                .toList();
    }

    public LoyaltyAccount findOrCreate(UUID tenantId, String userId) {
        return loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> loyaltyAccountRepository.save(LoyaltyAccount.builder()
                        .userId(userId)
                        .totalPoints(0)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    @Transactional
    public void credit(LoyaltyAccount account, int points, String reason, Long billId, BigDecimal amount) {
        account.setTotalPoints(account.getTotalPoints() + points);
        loyaltyAccountRepository.save(account);
        loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .points(points)
                .reason(reason)
                .billId(billId)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
