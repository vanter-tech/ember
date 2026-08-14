package com.vanter.ember.kitchen.listener;

import com.vanter.ember.kitchen.event.KitchenItemUpdated;
import com.vanter.ember.kitchen.event.KitchenOrderRetired;
import com.vanter.ember.session.event.KitchenItemsConfirmed;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KitchenWebSocketListenerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks KitchenWebSocketListener listener;

    @Test
    void onKitchenItemsConfirmed_sendsToTenantKitchenTopic() {
        KitchenItemsConfirmed event = new KitchenItemsConfirmed(TENANT_ID, "sess-1", 5, List.of());

        listener.onKitchenItemsConfirmed(event);

        verify(messagingTemplate).convertAndSend("/topic/kitchen/" + TENANT_ID, event);
    }

    @Test
    void onKitchenItemUpdated_sendsToTenantKitchenTopic() {
        KitchenItemUpdated event =
                new KitchenItemUpdated(TENANT_ID, "sess-1", "order-item-1", OrderItemStatus.PREPARING);

        listener.onKitchenItemUpdated(event);

        verify(messagingTemplate).convertAndSend("/topic/kitchen/" + TENANT_ID, event);
    }

    @Test
    void onKitchenOrderRetired_sendsToTenantKitchenTopic() {
        KitchenOrderRetired event = new KitchenOrderRetired(TENANT_ID, "sess-1");

        listener.onKitchenOrderRetired(event);

        verify(messagingTemplate).convertAndSend("/topic/kitchen/" + TENANT_ID, event);
    }
}
