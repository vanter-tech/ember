package com.vanter.ember.loyalty.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
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
}
