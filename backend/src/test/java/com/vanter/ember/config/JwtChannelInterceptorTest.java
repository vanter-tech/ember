package com.vanter.ember.config;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.session.service.SessionService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock UserDetailsService userDetailsService;
    @Mock SessionService sessionService;
    @Mock MessageChannel channel;
    @InjectMocks JwtChannelInterceptor interceptor;

    private UserDetails waiter() {
        return new User("waiter@test.com", "",
                List.of(new SimpleGrantedAuthority("ROLE_WAITER")));
    }

    private static UsernamePasswordAuthenticationToken authOf(String username, String role) {
        return new UsernamePasswordAuthenticationToken(
                new User(username, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))),
                null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
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

    /**
     * A SUBSCRIBE frame carrying a destination, the tenant bound at CONNECT time (session
     * attribute), and the authenticated user set at CONNECT time.
     */
    private Message<byte[]> subscribeMessage(String destination, UUID boundTenantId,
                                             UsernamePasswordAuthenticationToken user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(JwtChannelInterceptor.TENANT_SESSION_ATTRIBUTE, boundTenantId);
        accessor.setSessionAttributes(sessionAttributes);
        if (user != null) {
            accessor.setUser(user);
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

    // --- SUBSCRIBE destination authorization (QA_SIMULATION_REPORT.md E-01) ---

    @Test
    void subscribe_toOwnTenantWaiterTopic_asWaiter_isAllowed() {
        UUID tenantId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/waiter/" + tenantId, tenantId, authOf("w@test.com", "WAITER")), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_toAnotherTenantsWaiterTopic_isRejected() {
        UUID ownTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        Message<byte[]> subscribe =
                subscribeMessage("/topic/waiter/" + otherTenant, ownTenant, authOf("w@test.com", "WAITER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_toWaiterTopic_asCustomer_isRejected() {
        UUID tenantId = UUID.randomUUID();

        Message<byte[]> subscribe =
                subscribeMessage("/topic/waiter/" + tenantId, tenantId, authOf("c@test.com", "CUSTOMER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_toKitchenTopic_asWaiter_isRejected() {
        UUID tenantId = UUID.randomUUID();

        Message<byte[]> subscribe =
                subscribeMessage("/topic/kitchen/" + tenantId, tenantId, authOf("w@test.com", "WAITER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_toKitchenTopic_asKitchen_isAllowed() {
        UUID tenantId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/kitchen/" + tenantId, tenantId, authOf("k@test.com", "KITCHEN")), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_toSessionTopic_asStaffOfTheSameTenant_isAllowed() {
        UUID tenantId = UUID.randomUUID();
        when(sessionService.findById("sess-1")).thenReturn(null);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/session/sess-1", tenantId, authOf("w@test.com", "WAITER")), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_toSessionTopic_notFoundUnderBoundTenant_isRejected() {
        UUID tenantId = UUID.randomUUID();
        when(sessionService.findById("sess-other-tenant")).thenThrow(new ResourceNotFoundException("not found"));

        Message<byte[]> subscribe = subscribeMessage(
                "/topic/session/sess-other-tenant", tenantId, authOf("c@test.com", "CUSTOMER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(sessionService, never()).isParticipant(any(), any());
    }

    @Test
    void subscribe_toSessionTopic_asParticipant_isAllowed() {
        UUID tenantId = UUID.randomUUID();
        when(sessionService.findById("sess-1")).thenReturn(null);
        when(sessionService.isParticipant("sess-1", "diner@test.com")).thenReturn(true);

        Message<?> result = interceptor.preSend(
                subscribeMessage("/topic/session/sess-1", tenantId, authOf("diner@test.com", "CUSTOMER")), channel);

        assertThat(result).isNotNull();
    }

    @Test
    void subscribe_toSessionTopic_asNonParticipantCustomer_isRejected() {
        UUID tenantId = UUID.randomUUID();
        when(sessionService.findById("sess-1")).thenReturn(null);
        when(sessionService.isParticipant(eq("sess-1"), any())).thenReturn(false);

        Message<byte[]> subscribe = subscribeMessage(
                "/topic/session/sess-1", tenantId, authOf("stranger@test.com", "CUSTOMER"));

        assertThatThrownBy(() -> interceptor.preSend(subscribe, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_toUnrelatedDestination_isUnaffected() {
        UUID tenantId = UUID.randomUUID();

        Message<?> result = interceptor.preSend(
                subscribeMessage("/user/queue/notifications", tenantId, authOf("c@test.com", "CUSTOMER")), channel);

        assertThat(result).isNotNull();
    }
}
