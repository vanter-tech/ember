package com.vanter.ember.loyalty.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyReward;
import com.vanter.ember.loyalty.model.LoyaltyTier;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LoyaltyAccountRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired LoyaltyAccountRepository loyaltyAccountRepository;
    @Autowired LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Autowired LoyaltyRewardRepository loyaltyRewardRepository;

    @Override
    protected void deleteAll() {
        loyaltyTransactionRepository.deleteAll();
        loyaltyAccountRepository.deleteAll();
        loyaltyRewardRepository.deleteAll();
    }

    private LoyaltyAccount accountFor(UUID tenantId, String userId) {
        return readAs(
                tenantId,
                () -> loyaltyAccountRepository.save(
                        LoyaltyAccount.builder()
                                .userId(userId)
                                .totalPoints(0)
                                .createdAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        LoyaltyAccount saved = accountFor(TENANT_A, "user-1");

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByTenantIdAndUserId_doesNotLeakAnotherTenantsAccount() {
        accountFor(TENANT_A, "user-1");

        assertThat(readAs(TENANT_B,
                () -> loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_B, "user-1")))
                .isEmpty();
        assertThat(readAs(TENANT_A,
                () -> loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_A, "user-1")))
                .isPresent();
    }

    @Test
    void findByLoyaltyAccountIdOrderByCreatedAtDesc_doesNotLeakAnotherTenantsTransactions() {
        LoyaltyAccount account = accountFor(TENANT_A, "user-1");
        LocalDateTime now = LocalDateTime.now();
        readAs(TENANT_A, () -> loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account).points(5).reason("BILL_SETTLED").billId(1L)
                .createdAt(now).build()));
        readAs(TENANT_A, () -> loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account).points(3).reason("BILL_SETTLED").billId(2L)
                .createdAt(now.plusMinutes(5)).build()));

        List<LoyaltyTransaction> transactions = readAs(TENANT_A,
                () -> loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId()));

        assertThat(transactions).extracting(LoyaltyTransaction::getBillId).containsExactly(2L, 1L);
        assertThat(readAs(TENANT_B,
                () -> loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId())))
                .isEmpty();
    }

    private LoyaltyReward rewardFor(UUID tenantId, String name, boolean active) {
        return readAs(
                tenantId,
                () -> loyaltyRewardRepository.save(
                        LoyaltyReward.builder()
                                .name(name)
                                .description("test reward")
                                .requiredTier(LoyaltyTier.PLATA)
                                .active(active)
                                .createdAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void findByTenantId_doesNotLeakAnotherTenantsRewards() {
        rewardFor(TENANT_A, "Free dessert", true);

        assertThat(readAs(TENANT_B, () -> loyaltyRewardRepository.findByTenantId(TENANT_B))).isEmpty();
        assertThat(readAs(TENANT_A, () -> loyaltyRewardRepository.findByTenantId(TENANT_A))).hasSize(1);
    }

    @Test
    void findByTenantIdAndActiveTrue_excludesInactiveRewards() {
        rewardFor(TENANT_A, "Active reward", true);
        rewardFor(TENANT_A, "Inactive reward", false);

        assertThat(readAs(TENANT_A, () -> loyaltyRewardRepository.findByTenantIdAndActiveTrue(TENANT_A)))
                .extracting(LoyaltyReward::getName)
                .containsExactly("Active reward");
    }
}
