package com.vanter.ember.kitchen.listener;

import com.vanter.ember.kitchen.event.KitchenItemRemoved;
import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.event.KitchenOrderRetired;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KitchenWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onKitchenItemsConfirmed(KitchenItemsConfirmed event) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + event.tenantId(), event);
    }

    @EventListener
    public void onKitchenItemUpdated(KitchenItemUpdated event) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + event.tenantId(), event);
    }

    @EventListener
    public void onKitchenOrderRetired(KitchenOrderRetired event) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + event.tenantId(), event);
    }

    @EventListener
    public void onKitchenItemRemoved(KitchenItemRemoved event) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + event.tenantId(), event);
    }
}
