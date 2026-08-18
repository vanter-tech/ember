package com.vanter.ember.loyalty.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.loyalty.service.LoyaltyService;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoyaltyAccrualListenerTest {

    @Mock SettingService settingService;
    @Mock BillSplitRepository billSplitRepository;
    @Mock SessionService sessionService;
    @Mock LoyaltyAccountService loyaltyAccountService;
    @Mock LoyaltyService loyaltyService;
    @InjectMocks LoyaltyAccrualListener listener;

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

    private RestaurantSettings settingsWith(SettingsPayload.LoyaltySettings loyalty) {
        SettingsPayload payload = new SettingsPayload();
        payload.setLoyalty(loyalty);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(payload);
        return settings;
    }

    private SettingsPayload.LoyaltySettings enabledSettings() {
        SettingsPayload.LoyaltySettings loyalty = new SettingsPayload().getLoyalty();
        loyalty.setEnabled(true);
        return loyalty;
    }

    private Session sessionWithParticipants(Participant... participants) {
        Session session = new Session();
        session.setParticipants(List.of(participants));
        return session;
    }

    private BillSplit splitFor(String participantName, String amount) {
        return BillSplit.builder()
                .bill(Bill.builder().id(BILL_ID).build())
                .participantName(participantName)
                .amount(new BigDecimal(amount))
                .status(BillSplitStatus.PAID)
                .build();
    }

    @Test
    void handlePaymentCompleted_disabledLoyalty_noOp() {
        SettingsPayload.LoyaltySettings disabled = new SettingsPayload().getLoyalty();
        disabled.setEnabled(false);
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(disabled));

        listener.handlePaymentCompleted(new PaymentCompleted(SESSION_ID, UUID.randomUUID(), BILL_ID));

        verifyNoInteractions(sessionService, billSplitRepository, loyaltyAccountService, loyaltyService);
    }

    @Test
    void handlePaymentCompleted_multiParticipant_creditsEachOnceOffTheirOwnSplit() {
        SettingsPayload.LoyaltySettings settings = enabledSettings();
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(settings));

        Participant alice = Participant.builder().userId("user-alice").name("Alice").build();
        Participant bob = Participant.builder().userId("user-bob").name("Bob").build();
        when(sessionService.findById(SESSION_ID)).thenReturn(sessionWithParticipants(alice, bob));

        BillSplit aliceSplit = splitFor("Alice", "30.00");
        BillSplit bobSplit = splitFor("Bob", "20.00");
        when(billSplitRepository.findByBillId(BILL_ID)).thenReturn(List.of(aliceSplit, bobSplit));

        when(loyaltyService.computeAccrualPoints(aliceSplit.getAmount(), settings)).thenReturn(30);
        when(loyaltyService.computeAccrualPoints(bobSplit.getAmount(), settings)).thenReturn(20);

        LoyaltyAccount aliceAccount = LoyaltyAccount.builder().userId("user-alice").totalPoints(0).build();
        LoyaltyAccount bobAccount = LoyaltyAccount.builder().userId("user-bob").totalPoints(0).build();
        when(loyaltyAccountService.findOrCreate(TENANT_ID, "user-alice")).thenReturn(aliceAccount);
        when(loyaltyAccountService.findOrCreate(TENANT_ID, "user-bob")).thenReturn(bobAccount);

        listener.handlePaymentCompleted(new PaymentCompleted(SESSION_ID, UUID.randomUUID(), BILL_ID));

        verify(loyaltyAccountService).credit(eq(aliceAccount), eq(30), eq("BILL_SETTLED"), eq(BILL_ID));
        verify(loyaltyAccountService).credit(eq(bobAccount), eq(20), eq("BILL_SETTLED"), eq(BILL_ID));
    }

    @Test
    void handlePaymentCompleted_unresolvedParticipant_skippedWithoutCrediting() {
        SettingsPayload.LoyaltySettings settings = enabledSettings();
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(settings));

        Participant alice = Participant.builder().userId("user-alice").name("Alice").build();
        when(sessionService.findById(SESSION_ID)).thenReturn(sessionWithParticipants(alice));

        BillSplit ghostSplit = splitFor("Ghost", "15.00");
        when(billSplitRepository.findByBillId(BILL_ID)).thenReturn(List.of(ghostSplit));

        listener.handlePaymentCompleted(new PaymentCompleted(SESSION_ID, UUID.randomUUID(), BILL_ID));

        verify(loyaltyAccountService, never()).findOrCreate(any(), any());
        verify(loyaltyAccountService, never()).credit(any(), anyInt(), any(), any());
    }
}
