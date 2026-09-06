package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.dto.BillReadyMessage;
import com.vanter.ember.billing.event.BillingRequested;
import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplit;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.billing.service.BillingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class BillingEventListener {

    private final BillingService billingService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * {@code calculateBill} and the split step each open their own {@code @Transactional} and commit
     * independently. Without a transaction spanning the whole handler, a split failure (e.g.
     * {@code participantCount} greater than the real participant count) left the {@code Bill} row
     * committed by {@code calculateBill} behind as an orphan — every later "calculate bill" for that
     * session then threw "Session already billed" until a waiter manually voided it. Wrapping the
     * handler joins both inner {@code @Transactional} calls into one unit of work, so a split failure
     * rolls the {@code Bill} back too and the waiter can simply retry. The broadcast is the last
     * statement, so it only fires once everything has committed.
     */
    @Transactional
    @EventListener
    public void handleBillingRequested(BillingRequested event) {
        Bill bill = billingService.calculateBill(event.sessionId(), event.splitMethod());

        List<BillSplit> splits = event.splitMethod() == SplitMethod.BY_CONSUMPTION
                ? billingService.splitByConsumption(bill.getId())
                : billingService.splitEqually(bill.getId(), event.participantCount());

        messagingTemplate.convertAndSend(
                "/topic/session/" + event.sessionId(),
                BillReadyMessage.of(bill.getId(), bill.getTotal(), splits));
    }
}
