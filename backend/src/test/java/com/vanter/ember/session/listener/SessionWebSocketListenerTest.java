package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.ParticipantJoined;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionWebSocketListenerTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks SessionWebSocketListener listener;

    @Test
    void onParticipantJoined_sendsToSessionTopic() {
        ParticipantJoined event = new ParticipantJoined("sess-1", "user-1", "Alice");

        listener.onParticipantJoined(event);

        verify(messagingTemplate).convertAndSend(
                "/topic/sessions/sess-1",
                event);
    }

    @Test
    void onParticipantJoined_topicContainsSessionId() {
        ParticipantJoined event = new ParticipantJoined("sess-42", "user-2", "Bob");

        listener.onParticipantJoined(event);

        verify(messagingTemplate).convertAndSend("/topic/sessions/sess-42", event);
    }
}
