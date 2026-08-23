package com.vanter.ember.printing.config;

import com.vanter.ember.config.CorsProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Second, fully isolated STOMP endpoint for print agents only — {@code /ws/print-agent}.
 * Registering a second {@code @EnableWebSocketMessageBroker} config alongside the existing
 * {@code WebSocketConfig} is supported by Spring; the isolation this spec requires is at the
 * interceptor/auth level, which is what actually matters here.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class PrintAgentWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PrintAgentChannelInterceptor printAgentChannelInterceptor;
    private final PrintAgentHandshakeInterceptor printAgentHandshakeInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/print-agent");
        registry.setApplicationDestinationPrefixes("/app/print-agent");
    }

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
