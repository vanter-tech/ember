package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.MessageDeliveryException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock MessageChannel channel;
    @InjectMocks JwtChannelInterceptor interceptor;

    private UserDetails waiter() {
        return new User("waiter@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_WAITER")));
    }

    private Message<byte[]> connectMessage(String authHeader) {
        return connectMessage(authHeader, null);
    }

    private Message<byte[]> connectMessage(String authHeader, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage() {
        return subscribeMessage(null);
    }

    private Message<byte[]> subscribeMessage(Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void connect_withValidJwt_setsAuthentication() {
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractSubject("valid-token")).thenReturn("waiter@test.com");
        when(userDetailsService.loadUserByUsername("waiter@test.com")).thenReturn(waiter());

        Message<?> result = interceptor.preSend(connectMessage("Bearer valid-token"), channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(resultAccessor.getUser().getName()).isEqualTo("waiter@test.com");
    }

    @Test
    void connect_withNoAuthHeader_throwsMessageDeliveryException() {
        assertThatThrownBy(() -> interceptor.preSend(connectMessage(null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void connect_withInvalidJwt_throwsMessageDeliveryException() {
        when(jwtService.isTokenValid("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage("Bearer bad-token"), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void nonConnectFrame_isNotProcessed() {
        Message<?> result = interceptor.preSend(subscribeMessage(), channel);

        assertThat(result).isNotNull();
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void connect_withValidJwt_bindsTenantFromRidClaim() {
        UUID tenantId = UUID.randomUUID();
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.extractSubject("valid-token")).thenReturn("waiter@test.com");
        when(jwtService.extractTenantId("valid-token")).thenReturn(tenantId);
        when(userDetailsService.loadUserByUsername("waiter@test.com")).thenReturn(waiter());

        Map<String, Object> sessionAttributes = new HashMap<>();
        interceptor.preSend(connectMessage("Bearer valid-token", sessionAttributes), channel);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
        assertThat(sessionAttributes)
                .containsEntry(JwtChannelInterceptor.TENANT_SESSION_ATTRIBUTE, tenantId);
    }

    @Test
    void nonConnectFrame_bindsTenantFromSessionAttributes() {
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(JwtChannelInterceptor.TENANT_SESSION_ATTRIBUTE, tenantId);

        interceptor.preSend(subscribeMessage(sessionAttributes), channel);

        assertThat(TenantContextHolder.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void connect_printAgentEndpoint_skipsTenantValidation() {
        // The client inbound channel is shared with the print-agent-only /ws/print-agent
        // endpoint (Spring merges every WebSocketMessageBrokerConfigurer's interceptors onto one
        // channel) — a session tagged as print-agent must never be run through tenant-user JWT
        // validation, even with no Authorization header at all.
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE, WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT);

        Message<?> result = interceptor.preSend(connectMessage(null, sessionAttributes), channel);

        assertThat(result).isNotNull();
        verify(jwtService, never()).isTokenValid(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void afterSendCompletion_clearsTenant() {
        TenantContextHolder.setTenantId(UUID.randomUUID());

        interceptor.afterSendCompletion(subscribeMessage(), channel, true, null);

        assertThat(TenantContextHolder.getTenantId()).isNull();
    }
}
