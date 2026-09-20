package com.vanter.ember.printing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.WebSocketSessionAttributes;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

class PrintAgentHandshakeInterceptorTest {

    private final PrintAgentHandshakeInterceptor interceptor = new PrintAgentHandshakeInterceptor();

    private Map<String, Object> handshakeFrom(InetSocketAddress remote) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getRemoteAddress()).thenReturn(remote);
        Map<String, Object> attributes = new HashMap<>();
        boolean allowed = interceptor.beforeHandshake(
                request, mock(ServerHttpResponse.class), mock(WebSocketHandler.class), attributes);
        assertThat(allowed).isTrue();
        return attributes;
    }

    @Test
    void stampsTheEndpointAndThePeerAddress() {
        Map<String, Object> attributes = handshakeFrom(new InetSocketAddress("203.0.113.21", 50123));

        assertThat(attributes.get(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE))
                .isEqualTo(WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT);
        assertThat(attributes.get(WebSocketSessionAttributes.REMOTE_ADDRESS_ATTRIBUTE)).isEqualTo("203.0.113.21");
    }

    @Test
    void anUnknownPeer_stillHandshakes_withoutAnAddress() {
        Map<String, Object> attributes = handshakeFrom(null);

        assertThat(attributes).doesNotContainKey(WebSocketSessionAttributes.REMOTE_ADDRESS_ATTRIBUTE);
    }
}
