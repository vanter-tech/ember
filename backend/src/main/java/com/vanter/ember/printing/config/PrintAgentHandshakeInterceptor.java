package com.vanter.ember.printing.config;

import com.vanter.ember.config.WebSocketSessionAttributes;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Stamps every {@code /ws/print-agent} session with {@link WebSocketSessionAttributes#ENDPOINT_ATTRIBUTE}
 * so {@code PrintAgentChannelInterceptor} (and {@code JwtChannelInterceptor}) can tell this
 * session apart from a tenant-facing {@code /ws} one on the shared {@code clientInboundChannel}.
 */
@Component
public class PrintAgentHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        attributes.put(WebSocketSessionAttributes.ENDPOINT_ATTRIBUTE, WebSocketSessionAttributes.PRINT_AGENT_ENDPOINT);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }
}
