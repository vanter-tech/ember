package com.vanter.ember.printing.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.event.PrintAgentConnected;
import com.vanter.ember.printing.service.PrintAgentConnectionRegistry;
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

    @Test
    void connect_missingAuthorizationHeader_throws() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_tokenWithoutPrintAgentClaim_isRejected() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer some.jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.isTokenValid("some.jwt")).thenReturn(true);
        when(jwtService.extractClaim(org.mockito.ArgumentMatchers.eq("some.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void connect_validPrintAgentToken_marksConnectedAndPublishesEvent() {
        UUID agentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer agent.jwt");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.isTokenValid("agent.jwt")).thenReturn(true);
        when(jwtService.extractClaim(eq("agent.jwt"), org.mockito.ArgumentMatchers.any()))
                .thenReturn("print-agent");
        when(jwtService.extractSubject("agent.jwt")).thenReturn(agentId.toString());
        when(jwtService.extractTenantId("agent.jwt")).thenReturn(tenantId);

        interceptor.preSend(message, null);

        verify(connectionRegistry).markConnected(eq(agentId), any());
        verify(eventPublisher).publishEvent(new PrintAgentConnected(agentId));
    }
}
