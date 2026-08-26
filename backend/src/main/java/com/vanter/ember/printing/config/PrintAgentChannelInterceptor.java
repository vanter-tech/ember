package com.vanter.ember.printing.config;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.WebSocketSessionAttributes;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.service.PrintAgentConnectionRegistry;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Auth for the print-agent-only {@code /ws/print-agent} endpoint. Deliberately does NOT use
 * {@code UserDetailsService} — an agent is not a {@code User} — and never touches the
 * tenant-facing {@code JwtChannelInterceptor} (spec §2.6/§3.4).
 */
@Component
@RequiredArgsConstructor
public class PrintAgentChannelInterceptor implements ExecutorChannelInterceptor {

    private static final String AGENT_ID_ATTRIBUTE = "printAgentId";
    private static final String TENANT_ID_ATTRIBUTE = "printAgentTenantId";

    private final JwtService jwtService;
    private final PrintAgentConnectionRegistry connectionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        // The client inbound channel is shared with the tenant-facing /ws endpoint (Spring
        // merges every WebSocketMessageBrokerConfigurer's interceptors onto one channel) — only
        // enforce print-agent auth for sessions actually opened through /ws/print-agent.
        if (!isPrintAgentEndpoint(accessor)) {
            return message;
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            connectionRegistry.markDisconnected(accessor.getSessionId());
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(message, accessor);
        }

        return message;
    }

    // The pending-job flush fires from here, not preSend — preSend runs BEFORE a SUBSCRIBE
    // command is actually handed to the broker's own subscription registry (that registration
    // happens in a downstream message handler, not in any ChannelInterceptor#preSend), so a
    // flush fired from preSend still publishes to "/topic/print-agent/{agentId}" before the
    // subscription exists, and the simple broker silently drops it. afterSendCompletion only
    // fires once the message has actually been sent through the channel to its handler(s), i.e.
    // once the subscription is genuinely registered. (Found+fixed during PRINT-07's manual
    // verification, 2026-08-26 — an earlier version of this fix moved the flush from CONNECT to
    // SUBSCRIBE's preSend and still lost the message the exact same way, just one frame later.)
    @Override
    public void afterSendCompletion(
            Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        if (!sent || ex != null) {
            return;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !isPrintAgentEndpoint(accessor)) {
            return;
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        }
    }

    // beforeHandle/afterMessageHandled (ExecutorChannelInterceptor), unlike preSend/
    // afterSendCompletion, are guaranteed to run on the SAME thread that invokes the message
    // handler — clientInboundChannel dispatches to handlers via a thread pool, so preSend's
    // thread (where CONNECT resolved the agent/tenant ids) is not reliably the thread that later
    // runs a SEND frame's @MessageMapping method, e.g. PrintAgentAckController. Binding the
    // tenant here (instead of in preSend) is what makes PrintDispatchService.handleAck's
    // tenant-scoped findById actually see the job (found+fixed 2026-08-26: an agent ACK was
    // silently dropped by the empty NO_TENANT partition even after the ACK reached the
    // controller).
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !isPrintAgentEndpoint(accessor)) {
            return message;
        }
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.get(TENANT_ID_ATTRIBUTE) instanceof UUID tenantId) {
            TenantContextHolder.setTenantId(tenantId);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(
            Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && isPrintAgentEndpoint(accessor)) {
            TenantContextHolder.clear();
        }
    }

    private void handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException(message, "Missing Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException(message, "Invalid or expired agent token");
        }

        String type = jwtService.extractClaim(token, claims -> claims.get("typ", String.class));
        if (!"print-agent".equals(type)) {
            throw new MessageDeliveryException(message, "Not a print-agent token");
        }

        UUID agentId = UUID.fromString(jwtService.extractSubject(token));
        UUID tenantId = jwtService.extractTenantId(token);
        connectionRegistry.markConnected(agentId, accessor.getSessionId());

        // STOMP frames on the same session can be processed on different pooled threads, so the
        // resolved ids are stashed on the session (not just a ThreadLocal) for the later
        // SUBSCRIBE frame to read back.
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(AGENT_ID_ATTRIBUTE, agentId);
            sessionAttributes.put(TENANT_ID_ATTRIBUTE, tenantId);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }
        UUID agentId = (UUID) sessionAttributes.get(AGENT_ID_ATTRIBUTE);
        UUID tenantId = (UUID) sessionAttributes.get(TENANT_ID_ATTRIBUTE);
        if (agentId == null || !("/topic/print-agent/" + agentId).equals(accessor.getDestination())) {
            return;
        }

        TenantContextHolder.setTenantId(tenantId);
        try {
            eventPublisher.publishEvent(new PrintAgentConnected(agentId));
        } finally {
            TenantContextHolder.clear();
        }
    }

    private static boolean isPrintAgentEndpoint(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null
                && WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT.equals(
                        sessionAttributes.get(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE));
    }
}
