package com.vanter.ember.config;

/**
 * Shared session-attribute key/value used to tag which STOMP endpoint a WebSocket session
 * actually connected through. Spring's {@code @EnableWebSocketMessageBroker} merges every
 * {@code WebSocketMessageBrokerConfigurer} bean onto ONE shared {@code clientInboundChannel} —
 * registering a second config class for a second endpoint (e.g. the print-agent-only
 * {@code /ws/print-agent}) does NOT give it an isolated channel or interceptor set. An
 * endpoint-scoped {@code HandshakeInterceptor} stamps this attribute at handshake time so
 * channel interceptors can tell which endpoint a CONNECT (and every later frame on that session)
 * actually belongs to, instead of applying to every endpoint unconditionally.
 */
public final class WebSocketSessionAttributes {

    public static final String ENDPOINT_ATTRIBUTE = "wsEndpoint";
    public static final String PRINT_AGENT_ENDPOINT = "print-agent";

    private WebSocketSessionAttributes() {
    }
}
