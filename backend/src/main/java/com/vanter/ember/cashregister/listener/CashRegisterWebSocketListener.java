package com.vanter.ember.cashregister.listener;

import com.vanter.ember.cashregister.event.CashMovementRecorded;
import com.vanter.ember.cashregister.event.CashShiftClosed;
import com.vanter.ember.cashregister.event.CashShiftOpened;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts cash-shift lifecycle events tenant-wide, mirroring {@code WaiterWebSocketListener}'s
 * shape. No frontend page subscribes to this topic yet (see the "No new frontend WebSocket
 * subscription" constraint in the plan this task came from) — the broadcast is harmless to ship
 * ahead of that and unblocks a future fix to the shared-subscription-slot limitation in {@code
 * websocket.ts}.
 */
@Component
@RequiredArgsConstructor
public class CashRegisterWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onShiftOpened(CashShiftOpened event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }

    @EventListener
    public void onShiftClosed(CashShiftClosed event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }

    @EventListener
    public void onMovementRecorded(CashMovementRecorded event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }
}
