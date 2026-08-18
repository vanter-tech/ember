package com.vanter.ember.loyalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoyaltyAccountServiceTest {

    @Mock LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock LoyaltyTransactionRepository loyaltyTransactionRepository;
    @InjectMocks LoyaltyAccountService loyaltyAccountService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String USER_ID = "user-1";

    @Test
    void findOrCreate_noExistingAccount_createsOneWithZeroPoints() {
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(loyaltyAccountRepository.save(org.mockito.ArgumentMatchers.any(LoyaltyAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoyaltyAccount created = loyaltyAccountService.findOrCreate(TENANT_ID, USER_ID);

        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(loyaltyAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTotalPoints()).isZero();
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
        assertThat(created.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void findOrCreate_existingAccount_returnsItWithoutSaving() {
        LoyaltyAccount existing = LoyaltyAccount.builder()
                .id(1L)
                .userId(USER_ID)
                .totalPoints(42)
                .build();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(existing));

        LoyaltyAccount result = loyaltyAccountService.findOrCreate(TENANT_ID, USER_ID);

        assertThat(result).isSameAs(existing);
        verify(loyaltyAccountRepository).findByTenantIdAndUserId(TENANT_ID, USER_ID);
        verifyNoMoreInteractions(loyaltyAccountRepository);
    }

    @Test
    void credit_persistsAmountOnTheLedgerRow() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(1L).userId(USER_ID).totalPoints(10).build();

        loyaltyAccountService.credit(account, 5, "BILL_SETTLED", 42L, new BigDecimal("30.00"));

        assertThat(account.getTotalPoints()).isEqualTo(15);
        verify(loyaltyAccountRepository).save(account);
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(loyaltyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("30.00");
        assertThat(captor.getValue().getBillId()).isEqualTo(42L);
        assertThat(captor.getValue().getPoints()).isEqualTo(5);
        assertThat(captor.getValue().getReason()).isEqualTo("BILL_SETTLED");
    }

    @Test
    void getMyVisits_returnsMostRecentTwentyNewestFirst() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(7L).userId(USER_ID).totalPoints(500).build();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(account));

        LocalDateTime newest = LocalDateTime.of(2026, 8, 17, 12, 0);
        List<LoyaltyTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            transactions.add(LoyaltyTransaction.builder()
                    .id((long) i)
                    .loyaltyAccount(account)
                    .points(10)
                    .reason("BILL_SETTLED")
                    .billId((long) i)
                    .amount(new BigDecimal("15.00"))
                    .createdAt(newest.minusDays(i))
                    .build());
        }
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(7L))
                .thenReturn(transactions);

        List<LoyaltyVisitResponse> visits = loyaltyAccountService.getMyVisits(TENANT_ID, USER_ID);

        assertThat(visits).hasSize(20);
        assertThat(visits.get(0).visitedAt()).isEqualTo(newest);
        assertThat(visits.get(0).amountPaid()).isEqualByComparingTo("15.00");
        assertThat(visits.get(0).pointsEarned()).isEqualTo(10);
    }

    @Test
    void getMyVisits_noAccountForTenant_throwsResourceNotFound() {
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyAccountService.getMyVisits(TENANT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
