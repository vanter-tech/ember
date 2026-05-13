package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.dto.SessionClosedMessage;
import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.catalog.service.RestaurantTableService;
import com.vanter.ember.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompletedListener {

    private final SessionService sessionService;
    private final RestaurantTableService tableService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handlePaymentCompleted(PaymentCompleted event) {
        sessionService.closeSession(event.sessionId());
        tableService.setAvailable(event.tableId());
        messagingTemplate.convertAndSend(
                "/topic/session/" + event.sessionId(),
                SessionClosedMessage.of(event.sessionId(), event.billId()));
    }
}
