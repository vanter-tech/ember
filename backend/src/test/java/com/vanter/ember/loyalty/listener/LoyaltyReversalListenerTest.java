package com.vanter.ember.loyalty.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.event.PaymentRefunded;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoyaltyReversalListenerTest {

    @Mock SessionService sessionService;
    @Mock LoyaltyAccountRepository loyaltyAccountRepository;
    @Mock LoyaltyTransactionRepository loyaltyTransactionRepository;
    @Mock LoyaltyAccountService loyaltyAccountService;
    @InjectMocks LoyaltyReversalListener listener;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String SESSION_ID = "sess-1";
    private static final Long BILL_ID = 42L;

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private void sessionHasAlice() {
        Session session = new Session();
        session.setParticipants(List.of(
                Participant.builder().userId("user-alice").name("Alice").build()));
        when(sessionService.findById(SESSION_ID)).thenReturn(session);
    }

    private LoyaltyAccount aliceAccount() {
        return LoyaltyAccount.builder().id(7L).userId("user-alice").totalPoints(30).build();
    }

    private LoyaltyTransaction accrual(int points, String amount) {
        return LoyaltyTransaction.builder()
                .reason("BILL_SETTLED").points(points).billId(BILL_ID)
                .amount(new BigDecimal(amount)).build();
    }

    private LoyaltyTransaction reversal(int points) {
        return LoyaltyTransaction.builder()
                .reason("BILL_REFUNDED").points(points).billId(BILL_ID)
                .amount(new BigDecimal("-1.00")).build();
    }

    @Test
    void fullRefund_reversesAllAccruedPoints() {
        sessionHasAlice();
        LoyaltyAccount account = aliceAccount();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, "user-alice"))
                .thenReturn(Optional.of(account));
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId(7L, BILL_ID))
                .thenReturn(List.of(accrual(30, "30.00")));

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("30.00")));

        verify(loyaltyAccountService).credit(
                eq(account), eq(-30), eq("BILL_REFUNDED"), eq(BILL_ID), eq(new BigDecimal("-30.00")));
    }

    @Test
    void partialRefund_reversesProportionally() {
        sessionHasAlice();
        LoyaltyAccount account = aliceAccount();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, "user-alice"))
                .thenReturn(Optional.of(account));
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId(7L, BILL_ID))
                .thenReturn(List.of(accrual(30, "30.00")));

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("10.00")));

        // 30 pts * 10.00 / 30.00 = 10
        verify(loyaltyAccountService).credit(
                eq(account), eq(-10), eq("BILL_REFUNDED"), eq(BILL_ID), eq(new BigDecimal("-10.00")));
    }

    @Test
    void repeatedPartialRefunds_neverReverseMoreThanAccrued() {
        sessionHasAlice();
        LoyaltyAccount account = aliceAccount();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, "user-alice"))
                .thenReturn(Optional.of(account));
        // 25 of the 30 accrued points already reversed by an earlier refund.
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId(7L, BILL_ID))
                .thenReturn(List.of(accrual(30, "30.00"), reversal(-25)));

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("30.00")));

        // proportional would be 30, but only 5 remain reversible.
        verify(loyaltyAccountService).credit(
                eq(account), eq(-5), eq("BILL_REFUNDED"), eq(BILL_ID), eq(new BigDecimal("-30.00")));
    }

    @Test
    void noAccrualRow_noOp() {
        sessionHasAlice();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, "user-alice"))
                .thenReturn(Optional.of(aliceAccount()));
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId(7L, BILL_ID))
                .thenReturn(List.of());

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("30.00")));

        verify(loyaltyAccountService, never()).credit(any(), anyInt(), any(), any(), any());
    }

    @Test
    void unresolvedParticipant_noOp() {
        Session session = new Session();
        session.setParticipants(List.of(
                Participant.builder().userId("user-bob").name("Bob").build()));
        when(sessionService.findById(SESSION_ID)).thenReturn(session);

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("30.00")));

        verifyNoInteractions(loyaltyAccountRepository, loyaltyTransactionRepository, loyaltyAccountService);
    }

    @Test
    void noLoyaltyAccount_noOp() {
        sessionHasAlice();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, "user-alice"))
                .thenReturn(Optional.empty());

        listener.handlePaymentRefunded(
                new PaymentRefunded(SESSION_ID, BILL_ID, "Alice", new BigDecimal("30.00")));

        verifyNoInteractions(loyaltyTransactionRepository, loyaltyAccountService);
    }
}
