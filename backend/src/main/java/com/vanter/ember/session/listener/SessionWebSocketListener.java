package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.*;
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
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void onParticipantLeft(ParticipantLeft event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void onItemAdded(ItemAdded event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void onSessionCLose(SessionClosed event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void deleteItem(DeleteItem event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void onItemSent(ItemSent event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

    @EventListener
    public void onTableTransferred(TableTransferred event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }

}
