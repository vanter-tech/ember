package com.vanter.ember.loyalty.service;

import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Owns {@link LoyaltyAccount} persistence. {@link #findOrCreate} is the one creation path — called
 * from the table-join hook as the primary trigger, and again (idempotently) from the accrual
 * listener as a safety net, per design decision #2.
 */
@Service
@RequiredArgsConstructor
public class LoyaltyAccountService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;

    public LoyaltyAccount findOrCreate(UUID tenantId, String userId) {
        return loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseGet(() -> loyaltyAccountRepository.save(LoyaltyAccount.builder()
                        .userId(userId)
                        .totalPoints(0)
                        .createdAt(LocalDateTime.now())
                        .build()));
    }
}
