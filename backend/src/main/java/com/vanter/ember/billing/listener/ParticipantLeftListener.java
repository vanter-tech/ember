package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.billing.repository.BillSplitRepository;
import com.vanter.ember.billing.service.PaymentService;
import com.vanter.ember.session.event.ParticipantLeft;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * When a diner abandons a table that already has a bill, their still-unpaid share is spread
 * across the participants still present (same operation as the waiter's manual "remove diner").
 * Best-effort: if nobody is left to absorb it, the split stays unpaid for the waiter to settle
 * or void — the leave itself must not fail because of the billing side.
 */
@Component
@RequiredArgsConstructor
public class ParticipantLeftListener {

    private static final Logger log = LoggerFactory.getLogger(ParticipantLeftListener.class);

    private final BillRepository billRepository;
    private final BillSplitRepository billSplitRepository;
    private final PaymentService paymentService;

    @EventListener
    public void onParticipantLeft(ParticipantLeft event) {
        Bill bill = billRepository
                .findBySessionIdAndStatusNot(event.sessionId(), BillStatus.VOIDED)
                .orElse(null);
        if (bill == null) {
            return;
        }

        boolean hasUnpaidShare = billSplitRepository
                .findByBillIdAndParticipantName(bill.getId(), event.userName())
                .map(s -> s.getStatus() == BillSplitStatus.UNPAID)
                .orElse(false);
        if (!hasUnpaidShare) {
            return;
        }

        try {
            paymentService.redistributeSplit(bill.getId(), event.userName());
        } catch (RuntimeException ex) {
            log.warn("Could not redistribute the share of departed participant '{}' on bill {}: {}",
                    event.userName(), bill.getId(), ex.getMessage());
        }
    }
}
