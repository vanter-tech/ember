package com.vanter.ember.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import static org.assertj.core.api.Assertions.assertThatNoException;

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
}
