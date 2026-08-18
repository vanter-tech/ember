package com.vanter.ember.loyalty.repository;

import com.vanter.ember.loyalty.model.LoyaltyReward;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyRewardRepository extends JpaRepository<LoyaltyReward, Long> {

    List<LoyaltyReward> findByTenantId(UUID tenantId);

    List<LoyaltyReward> findByTenantIdAndActiveTrue(UUID tenantId);
}
