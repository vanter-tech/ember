package com.vanter.ember.loyalty.repository;

import com.vanter.ember.loyalty.model.LoyaltyAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, Long> {

    Optional<LoyaltyAccount> findByTenantIdAndUserId(UUID tenantId, String userId);
}
