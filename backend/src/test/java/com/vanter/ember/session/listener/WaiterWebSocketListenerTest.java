package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.ParticipantJoined;
import com.vanter.ember.session.event.SessionClosed;
import com.vanter.ember.session.event.SessionOpened;
import com.vanter.ember.session.event.TableTransferred;
import com.vanter.ember.session.model.SessionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaiterWebSocketListenerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks WaiterWebSocketListener listener;

    @Test
    void onSessionOpened_sendsToTenantWaiterTopic() {
        SessionOpened event = new SessionOpened(TENANT_ID, "sess-1", UUID.randomUUID(), 5);

        listener.onSessionOpened(event);

        verify(messagingTemplate).convertAndSend("/topic/waiter/" + TENANT_ID, event);
    }

    @Test
    void onParticipantJoined_sendsToTenantWaiterTopic() {
        ParticipantJoined event = new ParticipantJoined(TENANT_ID, "sess-1", "user-1", "Alice");

        listener.onParticipantJoined(event);

        verify(messagingTemplate).convertAndSend("/topic/waiter/" + TENANT_ID, event);
    }

    @Test
    void onSessionClosed_sendsToTenantWaiterTopic() {
        SessionClosed event =
                new SessionClosed(TENANT_ID, "sess-1", UUID.randomUUID(), SessionStatus.CLOSED);

        listener.onSessionClosed(event);

        verify(messagingTemplate).convertAndSend("/topic/waiter/" + TENANT_ID, event);
    }

    @Test
    void onTableTransferred_sendsToTenantWaiterTopic() {
        TableTransferred event = new TableTransferred(
                TENANT_ID, "sess-1", UUID.randomUUID(),
                "old@test.com", "new@test.com", "Nueva");

        listener.onTableTransferred(event);

        verify(messagingTemplate).convertAndSend("/topic/waiter/" + TENANT_ID, event);
    }
}
