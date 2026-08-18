package com.vanter.ember.loyalty.repository;

import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByLoyaltyAccountIdOrderByCreatedAtDesc(Long loyaltyAccountId);
}
