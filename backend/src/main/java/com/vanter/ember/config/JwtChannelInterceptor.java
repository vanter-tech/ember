package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    static final String TENANT_SESSION_ATTRIBUTE = "tenantId";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        // The client inbound channel is shared with the print-agent-only /ws/print-agent
        // endpoint (Spring merges every WebSocketMessageBrokerConfigurer's interceptors onto one
        // channel) — a print-agent session authenticates via PrintAgentChannelInterceptor
        // instead, never through this tenant-user path.
        if (isPrintAgentEndpoint(accessor)) {
            return message;
        }

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            bindTenantFromSession(accessor);
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new MessageDeliveryException(message, "Missing Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            throw new MessageDeliveryException(message, "Invalid or expired token");
        }

        String email = jwtService.extractSubject(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);

        UUID tenantId = jwtService.extractTenantId(token);
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            sessionAttributes.put(TENANT_SESSION_ATTRIBUTE, tenantId);
        }
        TenantContextHolder.setTenantId(tenantId);

        return message;
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        TenantContextHolder.clear();
    }

    private void bindTenantFromSession(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return;
        }
        Object tenantId = sessionAttributes.get(TENANT_SESSION_ATTRIBUTE);
        if (tenantId instanceof UUID uuid) {
            TenantContextHolder.setTenantId(uuid);
        }
    }

    private static boolean isPrintAgentEndpoint(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null
                && WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT.equals(
                        sessionAttributes.get(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE));
    }
}
