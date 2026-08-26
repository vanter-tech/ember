package com.vanter.ember.printing.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.model.ConnectionType;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.model.PrinterConfig;
import com.vanter.ember.printing.model.PrinterRenderMode;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import com.vanter.ember.printing.repository.PrinterConfigRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Full-context reproduction of two real bugs found during PRINT-07's manual physical printer
 * verification (2026-08-26), neither caught by {@code PrintAgentServiceTest}/
 * {@code PrintAgentAuthControllerTest} since both mock the collaborators that actually broke:
 *
 * <p>1. {@code PrintAgent} used to carry {@code @TenantId}, but {@code POST
 * /printing/agents/token} is deliberately {@code permitAll} — no tenant is ever bound for that
 * request, so Hibernate silently scoped {@code authenticateByApiKey}'s cross-tenant scan to the
 * empty {@code NO_TENANT} partition and it could never find a real agent, regardless of key
 * correctness.
 *
 * <p>2. {@code jwtAuthFilter} (SecurityConfig) unconditionally treated a JWT's subject as a user
 * email and called {@code loadUserByUsername} on it — a print-agent JWT's subject is the agent's
 * own id, which threw {@code UsernameNotFoundException} (surfaced as an opaque 500, not the
 * app's normal JSON error shape, since it happens before the request reaches
 * {@code DispatcherServlet}) for every {@code GET /printing/agents/me/printers} call.
 *
 * <p>A unit test of either collaborator in isolation cannot catch either bug — both need the
 * real Hibernate tenant filter and the real security filter chain running together, same
 * reasoning as {@code WebSocketEndpointIsolationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrintAgentTokenFlowIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private PrintAgentRepository printAgentRepository;
    @Autowired private PrinterConfigRepository printerConfigRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private static final String RAW_API_KEY = "test-raw-api-key-" + UUID.randomUUID();

    private Restaurant restaurant;
    private PrintAgent agent;
    private PrinterConfig printerConfig;

    /**
     * Deletes only the rows this test itself created, by id — never a broad
     * {@code deleteAll()} on a table shared with every other {@code @SpringBootTest} class in
     * this shared-context test run, which would collide with unrelated leftover data.
     */
    @AfterEach
    void tearDown() {
        if (printerConfig != null) {
            TenantContextHolder.setTenantId(restaurant.getId());
            try {
                printerConfigRepository.deleteById(printerConfig.getId());
            } finally {
                TenantContextHolder.clear();
            }
        }
        if (agent != null) printAgentRepository.deleteById(agent.getId());
        if (restaurant != null) restaurantRepository.deleteById(restaurant.getId());
    }

    @Test
    void tokenExchangeThenMyPrinters_realFilterChain_bothSucceed() throws Exception {
        restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Print Agent Flow Test").slug("print-agent-flow-" + UUID.randomUUID())
                .build());
        agent = printAgentRepository.save(PrintAgent.builder()
                .id(UUID.randomUUID()).tenantId(restaurant.getId()).name("Agente de prueba")
                .apiKeyHash(passwordEncoder.encode(RAW_API_KEY)).status(PrintAgentStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build());
        TenantContextHolder.setTenantId(restaurant.getId());
        try {
            printerConfig = printerConfigRepository.save(PrinterConfig.builder()
                    .id(UUID.randomUUID()).agentId(agent.getId())
                    .role(PrinterRole.RECEIPT).connectionType(ConnectionType.WINDOWS_QUEUE)
                    .windowsQueueName("Test Queue").renderMode(PrinterRenderMode.RAW)
                    .label("Recibo").active(true).build());
        } finally {
            TenantContextHolder.clear();
        }

        var tokenResponse = restTemplate.postForEntity(
                "/printing/agents/token", Map.of("apiKey", RAW_API_KEY), String.class);
        assertThat(tokenResponse.getStatusCode().is2xxSuccessful())
                .as("token exchange response: %s", tokenResponse.getBody())
                .isTrue();
        String token = objectMapper.readTree(tokenResponse.getBody()).get("token").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var printersResponse = restTemplate.exchange(
                "/printing/agents/me/printers", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(printersResponse.getStatusCode().is2xxSuccessful())
                .as("printers response: %s", printersResponse.getBody())
                .isTrue();
        assertThat(printersResponse.getBody()).contains("Recibo", "Test Queue");
    }
}
