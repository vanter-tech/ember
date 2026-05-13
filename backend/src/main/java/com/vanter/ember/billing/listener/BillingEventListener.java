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

@Component
@RequiredArgsConstructor
public class BillingEventListener {

    private final BillingService billingService;
    private final SimpMessagingTemplate messagingTemplate;

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
