package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.ItemAdded;
import com.vanter.ember.session.event.ParticipantJoined;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionWebSocketListenerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks SessionWebSocketListener listener;

    @Test
    void onParticipantJoined_sendsToSessionTopic() {
        ParticipantJoined event = new ParticipantJoined(TENANT_ID, "sess-1", "user-1", "Alice");

        listener.onParticipantJoined(event);

        verify(messagingTemplate).convertAndSend(
                "/topic/session/sess-1",
                event);
    }

    @Test
    void onParticipantJoined_topicContainsSessionId() {
        ParticipantJoined event = new ParticipantJoined(TENANT_ID, "sess-42", "user-2", "Bob");

        listener.onParticipantJoined(event);

        verify(messagingTemplate).convertAndSend("/topic/session/sess-42", event);
    }

    @Test
    void onItemAdded_sendsToSessionTopic() {
        ItemAdded event = new ItemAdded(
                "sess-1", "Tacos", new BigDecimal("12.50"),
                "Alice", OrderItemStatus.PENDING, List.of());

        listener.onItemAdded(event);

        verify(messagingTemplate).convertAndSend("/topic/session/sess-1", event);
    }

    @Test
    void onItemAdded_topicContainsSessionId() {
        ItemAdded event = new ItemAdded(
                "sess-99", "Burger", new BigDecimal("9.00"),
                "Bob", OrderItemStatus.PENDING, List.of());

        listener.onItemAdded(event);

        verify(messagingTemplate).convertAndSend("/topic/session/sess-99", event);
    }
}
