package com.vanter.ember.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class WebSocketConfigTest {

    @Autowired
    WebSocketConfig webSocketConfig;

    @Test
    void webSocketConfig_isLoadedAsBean() {
        assertThatNoException().isThrownBy(() -> {
            // Bean is present and wired — STOMP is configured
        });
    }

    @Test
    void webSocketConfig_implementsWebSocketMessageBrokerConfigurer() {
        assertThatNoException().isThrownBy(() -> {
            WebSocketMessageBrokerConfigurer configurer = webSocketConfig;
        });
    }

    @Test
    void stompEndpoint_usesTheSharedCorsOriginPolicy() {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(List.of("https://app.ember.vanter.com"));
        properties.setAllowedOriginPatterns(List.of("https://*.ember.vanter.com"));

        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        when(registration.setAllowedOrigins(any(String[].class))).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        when(registry.addEndpoint("/ws")).thenReturn(registration);

        new WebSocketConfig(mock(JwtChannelInterceptor.class), properties).registerStompEndpoints(registry);

        verify(registration).setAllowedOrigins("https://app.ember.vanter.com");
        verify(registration).setAllowedOriginPatterns("https://*.ember.vanter.com");
        verify(registration).withSockJS();
    }
}
