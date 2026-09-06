package com.vanter.ember.loyalty.repository;

import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, Long> {

    List<LoyaltyTransaction> findByLoyaltyAccountIdOrderByCreatedAtDesc(Long loyaltyAccountId);

    /** Every ledger row for one account on one bill — the accrual ({@code BILL_SETTLED}) row plus
     * any {@code BILL_REFUNDED} reversal rows already posted against it. */
    List<LoyaltyTransaction> findByLoyaltyAccountIdAndBillId(Long loyaltyAccountId, Long billId);
}
