package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.ParticipantJoined;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SessionWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onParticipantJoined(ParticipantJoined event) {
        messagingTemplate.convertAndSend("/topic/sessions/" + event.sessionId(), event);
    }
}
