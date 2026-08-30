package com.vanter.ember.session.listener;

import com.vanter.ember.session.event.ParticipantJoined;
import com.vanter.ember.session.event.ParticipantLeft;
import com.vanter.ember.session.event.SessionClosed;
import com.vanter.ember.session.event.SessionOpened;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts table-occupancy-affecting session events tenant-wide, for the waiter floor
 * dashboard ({@code Tables.tsx}) — distinct from {@link SessionWebSocketListener}, which
 * broadcasts to individual customer-facing per-session topics.
 */
@Component
@RequiredArgsConstructor
public class WaiterWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onSessionOpened(SessionOpened event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }

    @EventListener
    public void onParticipantJoined(ParticipantJoined event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }

    @EventListener
    public void onParticipantLeft(ParticipantLeft event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }

    @EventListener
    public void onSessionClosed(SessionClosed event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }
}
