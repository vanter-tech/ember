package com.vanter.ember.printing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.WebSocketSessionAttributes;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.service.PrintAgentConnectionRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class PrintAgentChannelInterceptorTest {

    @Mock JwtService jwtService;
    @Mock PrintAgentConnectionRegistry connectionRegistry;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks PrintAgentChannelInterceptor interceptor;

    private static StompHeaderAccessor printAgentAccessor(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        // A real STOMP session's attribute map is mutable (backed by the WebSocketSession) —
        // handleConnect writes into it, so the test double must be mutable too, not Map.of(...).
        Map<String, Object> sessionAttributes = new HashMap<>();
        sessionAttributes.put(
                WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE, WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT);
        accessor.setSessionAttributes(sessionAttributes);
        return accessor;
    }

    private static StompHeaderAccessor printAgentConnectAccessor() {
        return printAgentAccessor(StompCommand.CONNECT);
    }

    @Test
    void connect_missingAuthorizationHeader_throws() {
        StompHeaderAccessor accessor = printAgentConnectAccessor();
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_tokenWithoutPrintAgentClaim_isRejected() {
        StompHeaderAccessor accessor = printAgentConnectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer some.jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.isTokenValid("some.jwt")).thenReturn(true);
        when(jwtService.extractClaim(org.mockito.ArgumentMatchers.eq("some.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_validPrintAgentToken_marksConnectedButDoesNotPublishYet() {
        // The pending-job flush must NOT fire on CONNECT — AgentConnection.connect only calls
        // session.subscribe(...) after connectAsync(...) resolves, so a flush here would publish
        // to "/topic/print-agent/{agentId}" before the agent has subscribed to it, and the
        // simple broker silently drops messages with no current subscriber. See the SUBSCRIBE
        // tests below for where the flush actually happens now.
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StompHeaderAccessor accessor = printAgentConnectAccessor();
        accessor.setNativeHeader("Authorization", "Bearer agent.jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.isTokenValid("agent.jwt")).thenReturn(true);
        when(jwtService.extractClaim(eq("agent.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("print-agent");
        when(jwtService.extractSubject("agent.jwt")).thenReturn(agentId.toString());
        when(jwtService.extractTenantId("agent.jwt")).thenReturn(tenantId);

        interceptor.preSend(message, null);

        verify(connectionRegistry).markConnected(eq(agentId), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void subscribe_toOwnPrintAgentTopic_afterConnect_publishesEvent() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StompHeaderAccessor connectAccessor = printAgentConnectAccessor();
        connectAccessor.setNativeHeader("Authorization", "Bearer agent.jwt");
        Message<byte[]> connectMessage =
                MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        when(jwtService.isTokenValid("agent.jwt")).thenReturn(true);
        when(jwtService.extractClaim(eq("agent.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("print-agent");
        when(jwtService.extractSubject("agent.jwt")).thenReturn(agentId.toString());
        when(jwtService.extractTenantId("agent.jwt")).thenReturn(tenantId);
        interceptor.preSend(connectMessage, null);

        // A real client reuses the same WebSocketSession (and its attribute map) for every frame
        // on that connection — sharing connectAccessor's session attributes onto the SUBSCRIBE
        // accessor reproduces that instead of re-deriving them from a fresh empty map.
        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setSessionAttributes(connectAccessor.getSessionAttributes());
        subscribeAccessor.setDestination("/topic/print-agent/" + agentId);
        Message<byte[]> subscribeMessage =
                MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        // The flush fires from afterSendCompletion, not preSend — see that method's javadoc for
        // why: preSend runs before the broker has actually registered the subscription.
        interceptor.afterSendCompletion(subscribeMessage, null, true, null);

        verify(eventPublisher).publishEvent(new PrintAgentConnected(agentId));
    }

    @Test
    void subscribe_toUnrelatedDestination_doesNotPublish() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StompHeaderAccessor connectAccessor = printAgentConnectAccessor();
        connectAccessor.setNativeHeader("Authorization", "Bearer agent.jwt");
        Message<byte[]> connectMessage =
                MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());
        when(jwtService.isTokenValid("agent.jwt")).thenReturn(true);
        when(jwtService.extractClaim(eq("agent.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("print-agent");
        when(jwtService.extractSubject("agent.jwt")).thenReturn(agentId.toString());
        when(jwtService.extractTenantId("agent.jwt")).thenReturn(tenantId);
        interceptor.preSend(connectMessage, null);

        StompHeaderAccessor subscribeAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribeAccessor.setSessionAttributes(connectAccessor.getSessionAttributes());
        subscribeAccessor.setDestination("/topic/print-agent/" + UUID.randomUUID());
        Message<byte[]> subscribeMessage =
                MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        interceptor.afterSendCompletion(subscribeMessage, null, true, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void subscribe_sendFailed_doesNotPublish() {
        // afterSendCompletion(sent=false) means the SUBSCRIBE never actually reached the broker
        // (e.g. rejected downstream) — no real subscription exists, so no flush should fire.
        UUID agentId = UUID.randomUUID();
        StompHeaderAccessor subscribeAccessor = printAgentAccessor(StompCommand.SUBSCRIBE);
        subscribeAccessor.getSessionAttributes().put("printAgentId", agentId);
        subscribeAccessor.setDestination("/topic/print-agent/" + agentId);
        Message<byte[]> subscribeMessage =
                MessageBuilder.createMessage(new byte[0], subscribeAccessor.getMessageHeaders());

        interceptor.afterSendCompletion(subscribeMessage, null, false, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void connect_notPrintAgentEndpoint_passesThroughUntouched() {
        // The client inbound channel is shared with the tenant-facing /ws endpoint (Spring merges
        // every WebSocketMessageBrokerConfigurer's interceptors onto one channel) — a CONNECT
        // with no Authorization header at all, and no print-agent session tag, must pass through
        // unrejected so tenant traffic (customer/waiter/kitchen/admin) is unaffected.
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setSessionAttributes(Map.of());
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verify(jwtService, never()).isTokenValid(any());
        verify(connectionRegistry, never()).markConnected(any(), any());
    }

    @Test
    void connect_nullSessionAttributes_passesThroughUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isSameAs(message);
        verify(jwtService, never()).isTokenValid(any());
    }
}
