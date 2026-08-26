package com.vanter.ember.printing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.dto.PrintJobAck;
import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRenderMode;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

/**
 * Full-context reproduction of the print-agent ACK never reaching {@code PrintDispatchService}
 * (found 2026-08-26 while debugging why a physical printer never showed a {@code PRINTED}/{@code
 * ERROR} job, even when the agent logs a successful send): two bugs stack on the real ACK path,
 * neither visible to a unit test of either collaborator in isolation, same reasoning as {@code
 * WebSocketEndpointIsolationTest}/{@code PrintAgentTokenFlowIntegrationTest}.
 *
 * <p>1. {@code WebSocketConfig.configureMessageBroker} registers {@code
 * setApplicationDestinationPrefixes("/app", "/app/print-agent")} — Spring strips the FIRST
 * prefix that matches a destination, so the agent's {@code /app/print-agent/ack} SEND resolves
 * to mapped-destination {@code /print-agent/ack}, not {@code /ack}, and never reaches {@code
 * PrintAgentAckController}.
 *
 * <p>2. Even with (1) fixed, {@code PrintAgentChannelInterceptor} only binds
 * {@code TenantContextHolder} around the SUBSCRIBE frame (in {@code afterSendCompletion}) — a
 * later SEND frame runs on a different pooled {@code clientInboundChannel} thread with no tenant
 * bound, so {@code PrintDispatchService.handleAck}'s {@code findById} silently scopes to the
 * empty {@code NO_TENANT} partition and never finds the real job.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrintAgentAckFlowIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private PrintAgentRepository printAgentRepository;
    @Autowired private PrinterConfigRepository printerConfigRepository;
    @Autowired private PrintJobRepository printJobRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private static final String RAW_API_KEY = "test-ack-api-key-" + UUID.randomUUID();

    private Restaurant restaurant;
    private PrintAgent agent;
    private PrinterConfig printerConfig;
    private PrintJob job;

    @AfterEach
    void tearDown() {
        TenantContextHolder.setTenantId(restaurant.getId());
        try {
            if (job != null) printJobRepository.deleteById(job.getId());
            if (printerConfig != null) printerConfigRepository.deleteById(printerConfig.getId());
        } finally {
            TenantContextHolder.clear();
        }
        if (agent != null) printAgentRepository.deleteById(agent.getId());
        if (restaurant != null) restaurantRepository.deleteById(restaurant.getId());
    }

    @Test
    void agentAck_overRealStompSession_marksJobPrinted() throws Exception {
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Print Agent Ack Test").slug("print-agent-ack-" + UUID.randomUUID())
                .build());
        agent = printAgentRepository.save(PrintAgent.builder()
                .id(UUID.randomUUID()).tenantId(restaurant.getId()).name("Agente ACK")
                .apiKeyHash(passwordEncoder.encode(RAW_API_KEY)).status(PrintAgentStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build());

        TenantContextHolder.setTenantId(restaurant.getId());
        try {
            printerConfig = printerConfigRepository.save(PrinterConfig.builder()
                    .id(UUID.randomUUID()).agentId(agent.getId())
                    .role(PrinterRole.KITCHEN).connectionType(ConnectionType.WINDOWS_QUEUE)
                    .windowsQueueName("Test Queue").renderMode(PrinterRenderMode.RAW)
                    .label("Cocina").active(true).build());
            job = printJobRepository.saveAndFlush(PrintJob.builder()
                    .id(UUID.randomUUID()).role(PrinterRole.KITCHEN)
                    .sourceType(PrintJobSourceType.KITCHEN_TICKET).sourceId("test-source")
                    .payload("Mesa 1\n- Item\n").status(PrintJobStatus.SENT).attempts(1)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build());
        } finally {
            TenantContextHolder.clear();
        }

        var tokenResponse = restTemplate.postForEntity(
                "/printing/agents/token", Map.of("apiKey", RAW_API_KEY), String.class);
        assertThat(tokenResponse.getStatusCode().is2xxSuccessful())
                .as("token exchange response: %s", tokenResponse.getBody())
                .isTrue();
        String jwt = objectMapper.readTree(tokenResponse.getBody()).get("token").asText();

        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(java.util.List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwt);

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
                "http://localhost:" + port + "/v1/ws/print-agent",
                new WebSocketHttpHeaders(), connectHeaders, handler);

        StompSession session = connected.get(5, TimeUnit.SECONDS);
        // Mirrors the agent's own subscribe-then-flush ordering (AgentConnection.connect) so the
        // pending-job flush from CONNECT/SUBSCRIBE doesn't race the ACK below.
        session.subscribe("/topic/print-agent/" + agent.getId(), new StompSessionHandlerAdapter() {});
        Thread.sleep(300);

        session.send("/app/print-agent/ack",
                new PrintJobAck(job.getId(), printerConfig.getId(), "PRINTED", null));

        // Poll instead of a fixed sleep: the ACK is processed asynchronously on the broker's
        // pooled clientInboundChannel thread, not synchronously with session.send().
        PrintJobStatus finalStatus = null;
        for (int i = 0; i < 20; i++) {
            TenantContextHolder.setTenantId(restaurant.getId());
            try {
                finalStatus = printJobRepository.findById(job.getId()).map(PrintJob::getStatus).orElse(null);
            } finally {
                TenantContextHolder.clear();
            }
            if (finalStatus == PrintJobStatus.PRINTED) break;
            Thread.sleep(250);
        }

        assertThat(finalStatus).isEqualTo(PrintJobStatus.PRINTED);
        session.disconnect();
    }
}
