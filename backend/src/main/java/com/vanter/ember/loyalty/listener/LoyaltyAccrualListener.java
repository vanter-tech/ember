package com.vanter.ember.loyalty.listener;

import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.loyalty.service.LoyaltyService;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Credits loyalty points per participant off the existing {@link PaymentCompleted} event (fired
 * only once every {@link BillSplit} on the bill is PAID — see {@code PaymentService}). Accrual is
 * per-participant off their own split amount, never once per table (design decision #8).
 */
@Component
@RequiredArgsConstructor
public class LoyaltyAccrualListener {

    private static final String REASON_BILL_SETTLED = "BILL_SETTLED";

    private final SettingService settingService;
    private final BillSplitRepository billSplitRepository;
    private final SessionService sessionService;
    private final LoyaltyAccountService loyaltyAccountService;
    private final LoyaltyService loyaltyService;

    @EventListener
    public void handlePaymentCompleted(PaymentCompleted event) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        SettingsPayload.LoyaltySettings settings =
                settingService.getSettings(tenantId).getPayload().getLoyalty();
        if (!settings.isEnabled()) {
            return;
        }

        Session session = sessionService.findById(event.sessionId());
        for (BillSplit split : billSplitRepository.findByBillId(event.billId())) {
            session.getParticipants().stream()
                    .filter(participant -> participant.getName().equals(split.getParticipantName()))
                    .findFirst()
                    .ifPresent(participant ->
                            accrue(tenantId, participant.getUserId(), split, settings, event.billId()));
        }
    }

    private void accrue(
            UUID tenantId,
            String userId,
            BillSplit split,
            SettingsPayload.LoyaltySettings settings,
            Long billId) {
        int points = loyaltyService.computeAccrualPoints(split.getAmount(), settings);
        LoyaltyAccount account = loyaltyAccountService.findOrCreate(tenantId, userId);
        loyaltyAccountService.credit(account, points, REASON_BILL_SETTLED, billId);
    }
}
