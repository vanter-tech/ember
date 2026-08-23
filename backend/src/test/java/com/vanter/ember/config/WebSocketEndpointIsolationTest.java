package com.vanter.ember.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Reproduces the bug where a tenant-facing STOMP CONNECT (customer/waiter/kitchen/admin, via
 * {@code /ws}) gets rejected by {@code PrintAgentChannelInterceptor} — a print-agent-only
 * interceptor meant for the isolated {@code /ws/print-agent} endpoint. Spring's
 * {@code @EnableWebSocketMessageBroker} merges ALL {@code WebSocketMessageBrokerConfigurer} beans
 * onto one shared {@code clientInboundChannel}, so an interceptor registered by one config
 * applies to every endpoint, not just the one registered in that same class — a full context
 * boot is required to observe this, a unit test of either interceptor in isolation cannot.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketEndpointIsolationTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("WS Isolation Test").slug("ws-isolation-" + UUID.randomUUID())
                .build());
        // A bare CUSTOMER login token carries no `rid` claim by design (customers aren't
        // tenant-bound until they join a table) — using WAITER here keeps this test focused on
        // endpoint isolation instead of the unrelated customer-tenant-binding flow.
        userRepository.save(User.builder()
                .name("Waiter").email("ws-waiter@test.com").restaurantId(restaurant)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.WAITER).build());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    private String login() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ws-waiter@test.com");
        req.setPassword(PASSWORD);

        var response = restTemplate.postForEntity("/auth/login", req, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("login response: %s", response.getBody())
                .isTrue();
        return objectMapper.readTree(response.getBody()).get("token").asText();
    }

    @Test
    void tenantClient_canConnectToWs_despitePrintAgentEndpointSharingTheBroker() throws Exception {
        String token = login();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(session);
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                    StompHeaders headers, byte[] payload, Throwable exception) {
                connected.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        };

        stompClient.connectAsync(
                "ws://localhost:" + port + "/v1/ws/websocket",
                new WebSocketHttpHeaders(), connectHeaders, handler);

        StompSession session = connected.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void tenantClient_canConnectViaSockJs_matchingWhatTheBrowserActuallyDoes() throws Exception {
        // frontend/src/store/websocket.ts connects with `new SockJS(wsUrl)`, not a raw WebSocket —
        // this negotiates transports (info/websocket) instead of hitting /ws/websocket directly,
        // so it is the more faithful reproduction of what a real browser session does.
        String token = login();

        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connected.complete(session);
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                    StompHeaders headers, byte[] payload, Throwable exception) {
                connected.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        };

        stompClient.connectAsync(
                "http://localhost:" + port + "/v1/ws",
                new WebSocketHttpHeaders(), connectHeaders, handler);

        StompSession session = connected.get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).isTrue();
        session.disconnect();
    }

    @Test
    void topicBroadcast_isActuallyDeliveredToASubscriber() throws Exception {
        // CONNECT/SUBSCRIBE succeeding (the two tests above) does not prove the simple broker is
        // still routing "/topic/**" at all: PrintAgentWebSocketConfig.configureMessageBroker also
        // gets merged onto the shared broker registry, and Spring's
        // MessageBrokerRegistry#enableSimpleBroker REPLACES the previous registration rather than
        // adding to it — if the print-agent config's call runs after WebSocketConfig's, the
        // broker's destination prefixes silently become "/topic/print-agent" ONLY, and a genuine
        // tenant broadcast like "/topic/session/{id}" is accepted by the broker (no error) but
        // never delivered to any subscriber. This directly reproduces the reported symptom:
        // CONNECT/SUBSCRIBE succeed, heartbeats keep flowing, but application messages never
        // arrive client-side.
        String token = login();

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new StringMessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);

        CompletableFuture<StompSession> connected = new CompletableFuture<>();
        CompletableFuture<String> received = new CompletableFuture<>();
        String destination = "/topic/ws-isolation-test-" + UUID.randomUUID();

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe(destination, new StompFrameHandler() {
                    @Override
                    public java.lang.reflect.Type getPayloadType(StompHeaders headers) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        received.complete((String) payload);
                    }
                });
                connected.complete(session);
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                    StompHeaders headers, byte[] payload, Throwable exception) {
                connected.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                connected.completeExceptionally(exception);
            }
        };

        stompClient.connectAsync(
                "ws://localhost:" + port + "/v1/ws/websocket",
                new WebSocketHttpHeaders(), connectHeaders, handler);

        StompSession session = connected.get(5, TimeUnit.SECONDS);
        // Give the SUBSCRIBE frame time to actually register server-side before broadcasting.
        Thread.sleep(300);

        messagingTemplate.convertAndSend(destination, "hello-from-test");

        String payload = received.get(5, TimeUnit.SECONDS);
        assertThat(payload).isEqualTo("hello-from-test");
        session.disconnect();
    }
}
