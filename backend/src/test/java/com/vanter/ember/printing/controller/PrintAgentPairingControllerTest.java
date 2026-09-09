package com.vanter.ember.printing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.GlobalExceptionHandler;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.dto.PairRequest;
import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.service.PairAttemptGuard;
import com.vanter.ember.printing.service.PrintAgentService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(PrintAgentPairingController.class)
@Import({SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
class PrintAgentPairingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PrintAgentService printAgentService;
    @MockBean PairAttemptGuard pairAttemptGuard;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    private static final ObjectMapper JSON = new ObjectMapper();

    private String body(String code) throws Exception {
        return JSON.writeValueAsString(new PairRequest(code));
    }

    @Test
    void pair_validCode_returns200AndBody() throws Exception {
        UUID agentId = UUID.randomUUID();
        when(printAgentService.redeemPairingCode("GOODCODE00"))
                .thenReturn(new PairResponse("api-key-xyz", "https://api.example/v1", agentId, "Caja 1"));

        mockMvc.perform(post("/printing/agents/pair")
                        .contentType("application/json").content(body("GOODCODE00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("api-key-xyz"))
                .andExpect(jsonPath("$.agentName").value("Caja 1"));

        verify(pairAttemptGuard).recordSuccess(anyString());
    }

    @Test
    void pair_badCode_returns401AndRecordsFailure() throws Exception {
        when(printAgentService.redeemPairingCode(any()))
                .thenThrow(new BadCredentialsException("Invalid, used or expired pairing code"));

        mockMvc.perform(post("/printing/agents/pair")
                        .contentType("application/json").content(body("BADCODE000")))
                .andExpect(status().isUnauthorized());

        verify(pairAttemptGuard).recordFailure(anyString());
    }

    @Test
    void pair_whenGuardLocked_returns429() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pairing attempts"))
                .when(pairAttemptGuard).assertNotLocked(anyString());

        mockMvc.perform(post("/printing/agents/pair")
                        .contentType("application/json").content(body("ANYCODE000")))
                .andExpect(status().isTooManyRequests());
    }
}
