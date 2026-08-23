package com.vanter.ember.printing.config;

import com.vanter.ember.config.CorsProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Second, fully isolated STOMP endpoint for print agents only — {@code /ws/print-agent}. Isolation
 * is enforced at the interceptor/auth level ({@link PrintAgentChannelInterceptor} +
 * {@link PrintAgentHandshakeInterceptor}), not at the config-class level: every
 * {@code WebSocketMessageBrokerConfigurer} bean in the app (this one included) runs against the
 * SAME shared broker/channel infrastructure, regardless of each having its own
 * {@code @EnableWebSocketMessageBroker}. This class deliberately does NOT override
 * {@code configureMessageBroker} — {@code MessageBrokerRegistry#enableSimpleBroker} REPLACES the
 * previous registration rather than adding to it, so a second call here would silently wipe out
 * {@code WebSocketConfig}'s tenant-facing "/topic"/"/app" prefixes (this exact bug shipped and
 * broke every tenant broadcast until it was caught — see the isolation test in
 * {@code WebSocketEndpointIsolationTest}). {@code WebSocketConfig.configureMessageBroker} is the
 * one place that registers "/topic/print-agent"/"/app/print-agent" too.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class PrintAgentWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PrintAgentChannelInterceptor printAgentChannelInterceptor;
    private final PrintAgentHandshakeInterceptor printAgentHandshakeInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(printAgentChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/print-agent")
                .setAllowedOrigins(toArray(corsProperties.getAllowedOrigins()))
                .setAllowedOriginPatterns(toArray(corsProperties.getAllowedOriginPatterns()))
                .addInterceptors(printAgentHandshakeInterceptor)
                .withSockJS();
    }

    private static String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }
}
