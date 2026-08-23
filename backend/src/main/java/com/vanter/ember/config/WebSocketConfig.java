package com.vanter.ember.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(CorsProperties.class)
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Owns the ONLY `enableSimpleBroker`/`setApplicationDestinationPrefixes` call in the app:
        // every WebSocketMessageBrokerConfigurer bean's configureMessageBroker(...) runs against
        // this SAME shared MessageBrokerRegistry (Spring merges all of them, regardless of how
        // many separate @EnableWebSocketMessageBroker config classes exist), and
        // MessageBrokerRegistry#enableSimpleBroker REPLACES the previous registration rather than
        // adding to it. PrintAgentWebSocketConfig deliberately does NOT override this method — its
        // "/topic/print-agent"/"/app/print-agent" prefixes are registered here instead, so a
        // second call elsewhere can never silently wipe out tenant-facing routing again.
        registry.enableSimpleBroker("/topic", "/user", "/topic/print-agent");
        registry.setApplicationDestinationPrefixes("/app", "/app/print-agent");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same policy as the REST API (see CorsProperties): the SockJS handshake is a browser
        // request too, so a tenant subdomain cleared for HTTP must not be rejected here.
        registry.addEndpoint("/ws")
                .setAllowedOrigins(toArray(corsProperties.getAllowedOrigins()))
                .setAllowedOriginPatterns(toArray(corsProperties.getAllowedOriginPatterns()))
                .withSockJS();
    }

    private static String[] toArray(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }
}
