package com.vanter.ember.loyalty.service;

import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import java.time.LocalDateTime;
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

    public LoyaltyAccount findOrCreate(UUID tenantId, String userId) {
        return loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> loyaltyAccountRepository.save(LoyaltyAccount.builder()
                        .userId(userId)
                        .totalPoints(0)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }

    @Transactional
    public void credit(LoyaltyAccount account, int points, String reason, Long billId) {
        account.setTotalPoints(account.getTotalPoints() + points);
        loyaltyAccountRepository.save(account);
        loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .points(points)
                .reason(reason)
                .billId(billId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
