package com.vanter.ember.billing.listener;

import com.vanter.ember.billing.dto.SessionClosedMessage;
import com.vanter.ember.billing.event.PaymentCompleted;
import com.vanter.ember.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompletedListenerTest {

    @Mock SessionService sessionService;
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks PaymentCompletedListener listener;

    private PaymentCompleted event() {
        return new PaymentCompleted("sess-1", UUID.randomUUID(), 10L);
    }

    @Test
    void handlePaymentCompleted_closesSession() {
        listener.handlePaymentCompleted(event());

        verify(sessionService).closeSession("sess-1");
    }

    @Test
    void handlePaymentCompleted_broadcastsSessionClosedMessage() {
        listener.handlePaymentCompleted(event());

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SessionClosedMessage> msgCaptor = ArgumentCaptor.forClass(SessionClosedMessage.class);
        verify(messagingTemplate).convertAndSend(destCaptor.capture(), msgCaptor.capture());

        assertThat(destCaptor.getValue()).isEqualTo("/topic/session/sess-1");
        assertThat(msgCaptor.getValue().type()).isEqualTo("SESSION_CLOSED");
        assertThat(msgCaptor.getValue().sessionId()).isEqualTo("sess-1");
        assertThat(msgCaptor.getValue().billId()).isEqualTo(10L);
    }
}
