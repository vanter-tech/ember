package com.vanter.ember.printing.config;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.service.PrintAgentConnectionRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Auth for the print-agent-only {@code /ws/print-agent} endpoint. Deliberately does NOT use
 * {@code UserDetailsService} — an agent is not a {@code User} — and never touches the
 * tenant-facing {@code JwtChannelInterceptor} (spec §2.6/§3.4).
 */
@Component
@RequiredArgsConstructor
public class PrintAgentChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final PrintAgentConnectionRegistry connectionRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            connectionRegistry.markDisconnected(accessor.getSessionId());
            return message;
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

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
        TenantContextHolder.setTenantId(tenantId);
        eventPublisher.publishEvent(new PrintAgentConnected(agentId));
        return message;
    }
}
