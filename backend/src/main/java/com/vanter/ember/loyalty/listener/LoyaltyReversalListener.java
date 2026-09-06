package com.vanter.ember.loyalty.listener;

import com.vanter.ember.billing.event.PaymentRefunded;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.loyalty.model.LoyaltyAccount;
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyAccountRepository;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.SessionService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Claws back loyalty points when a payment is refunded. {@link com.vanter.ember.loyalty.listener
 * .LoyaltyAccrualListener} credits points once per participant at {@code BILL_SETTLED} off their
 * {@code BillSplit.amount}; a refund undoes that in proportion to the refunded amount.
 *
 * <p>Gated on the accrual ledger row actually existing (not on the current {@code loyalty.enabled}
 * flag): a bill refunded before it ever settled, or settled while loyalty was off, has nothing to
 * reverse. Reversal is capped so repeated partial refunds never claw back more than was accrued,
 * and {@link LoyaltyAccountService#credit} keeps the account balance and the ledger in lockstep.
 */
@Component
@RequiredArgsConstructor
public class LoyaltyReversalListener {

    private static final String REASON_BILL_SETTLED = "BILL_SETTLED";
    private static final String REASON_BILL_REFUNDED = "BILL_REFUNDED";

    private final SessionService sessionService;
    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final LoyaltyTransactionRepository loyaltyTransactionRepository;
    private final LoyaltyAccountService loyaltyAccountService;

    @EventListener
    public void handlePaymentRefunded(PaymentRefunded event) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        Session session = sessionService.findById(event.sessionId());
        String userId = session.getParticipants().stream()
                .filter(participant -> participant.getName().equals(event.participantName()))
                .map(participant -> participant.getUserId())
                .findFirst()
                .orElse(null);
        if (userId == null) {
            return;
        }

        LoyaltyAccount account = loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElse(null);
        if (account == null) {
            return;
        }

        List<LoyaltyTransaction> ledger =
                loyaltyTransactionRepository.findByLoyaltyAccountIdAndBillId(account.getId(), event.billId());

        LoyaltyTransaction accrual = ledger.stream()
                .filter(tx -> REASON_BILL_SETTLED.equals(tx.getReason()))
                .findFirst()
                .orElse(null);
        if (accrual == null || accrual.getPoints() <= 0
                || accrual.getAmount() == null || accrual.getAmount().signum() <= 0) {
            return;
        }

        // Reversal rows carry negative points; flip the sum to get how much has already been clawed
        // back so repeated partial refunds can't over-reverse.
        int alreadyReversed = -ledger.stream()
                .filter(tx -> REASON_BILL_REFUNDED.equals(tx.getReason()))
                .mapToInt(LoyaltyTransaction::getPoints)
                .sum();
        int reversible = accrual.getPoints() - alreadyReversed;
        if (reversible <= 0) {
            return;
        }

        int proportional = BigDecimal.valueOf(accrual.getPoints())
                .multiply(event.refundAmount())
                .divide(accrual.getAmount(), 0, RoundingMode.HALF_UP)
                .intValueExact();
        int reversal = Math.min(proportional, reversible);
        if (reversal <= 0) {
            return;
        }

        loyaltyAccountService.credit(
                account, -reversal, REASON_BILL_REFUNDED, event.billId(), event.refundAmount().negate());
    }
}
