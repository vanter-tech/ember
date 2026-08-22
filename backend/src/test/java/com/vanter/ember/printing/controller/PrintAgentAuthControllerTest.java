package com.vanter.ember.printing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.dto.AgentTokenRequest;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.service.PrintAgentService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PrintAgentAuthController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class PrintAgentAuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PrintAgentService printAgentService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @Test
    void token_validApiKey_returnsJwt() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();
        PrintAgent agent = PrintAgent.builder()
                .id(agentId).tenantId(tenantId).name("Agente Caja")
                .apiKeyHash("hash").status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now()).build();
        when(printAgentService.authenticateByApiKey("plain-key")).thenReturn(agent);
        when(jwtService.generateToken(eq(agentId.toString()), any(), anyLong())).thenReturn("signed.jwt.token");

        mockMvc.perform(post("/printing/agents/token")
                        .contentType("application/json")
                        .content(new ObjectMapper().writeValueAsString(new AgentTokenRequest("plain-key"))))
                .andExpect(status().isOk());

        verify(printAgentService).markSeen(agent);
    }
}
