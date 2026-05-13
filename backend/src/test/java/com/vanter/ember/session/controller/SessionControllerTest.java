package com.vanter.ember.session.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.session.dto.AddItemRequest;
import com.vanter.ember.session.dto.CreateSessionRequest;
import com.vanter.ember.session.dto.ExpandCapacityRequest;
import com.vanter.ember.session.dto.JoinSessionRequest;
import com.vanter.ember.session.exception.TooManyParticipantsException;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.QrTokenService;
import com.vanter.ember.session.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class SessionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SessionService sessionService;
    @MockBean QrTokenService qrTokenService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;

    private Session sampleSession() {
        return Session.builder()
                .id("sess-1").tableId(1L).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- POST /api/sessions ---

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void createSession_returnsCreatedSession() throws Exception {
        when(sessionService.createSession(1L, "waiter@test.com", 4))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(1L, 4))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("sess-1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void createSession_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(1L, 4))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createSession_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateSessionRequest(1L, 4))))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/sessions/{id}/qr ---

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void getQr_returnsToken() throws Exception {
        when(sessionService.findById("sess-1")).thenReturn(sampleSession());
        when(qrTokenService.generateQrToken("sess-1", 4)).thenReturn("qr-jwt-token");

        mockMvc.perform(get("/api/sessions/sess-1/qr"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").value("qr-jwt-token"));
    }

    @Test
    void getQr_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/sessions/sess-1/qr"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/sessions/{id}/join ---

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void joinSession_addsParticipant() throws Exception {
        Session withParticipant = sampleSession();
        withParticipant.getParticipants().add(
                Participant.builder().userId("customer@test.com").name("Alice").build());
        when(sessionService.joinSession("qr-token", "customer@test.com", "Alice"))
                .thenReturn(withParticipant);

        mockMvc.perform(post("/api/sessions/sess-1/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinSessionRequest("qr-token", "Alice"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].userId").value("customer@test.com"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void joinSession_returns409WhenFull() throws Exception {
        when(sessionService.joinSession(any(), any(), any()))
                .thenThrow(new TooManyParticipantsException("full"));

        mockMvc.perform(post("/api/sessions/sess-1/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinSessionRequest("qr-token", "Alice"))))
                .andExpect(status().isConflict());
    }

    @Test
    void joinSession_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/sessions/sess-1/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new JoinSessionRequest("qr-token", "Alice"))))
                .andExpect(status().isUnauthorized());
    }

    // --- PATCH /api/sessions/{id}/capacity ---

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void expandCapacity_updatesMaxParticipants() throws Exception {
        Session expanded = sampleSession();
        expanded.setMaxParticipants(6);
        when(sessionService.expandCapacity("sess-1", "waiter@test.com", 2))
                .thenReturn(expanded);

        mockMvc.perform(patch("/api/sessions/sess-1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpandCapacityRequest(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxParticipants").value(6));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void expandCapacity_forbiddenForCustomer() throws Exception {
        mockMvc.perform(patch("/api/sessions/sess-1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpandCapacityRequest(2))))
                .andExpect(status().isForbidden());
    }

    @Test
    void expandCapacity_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/api/sessions/sess-1/capacity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ExpandCapacityRequest(2))))
                .andExpect(status().isUnauthorized());
    }

    // --- GET /api/sessions/{id} ---

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void getSession_returnsFullSession() throws Exception {
        Session session = sampleSession();
        when(sessionService.findById("sess-1")).thenReturn(session);

        mockMvc.perform(get("/api/sessions/sess-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sess-1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getSession_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/sessions/sess-1"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /api/sessions/{id}/items ---

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void addItem_returnsUpdatedSession() throws Exception {
        when(sessionService.addItem("sess-1", "customer@test.com", 10L))
                .thenReturn(sampleSession());

        mockMvc.perform(post("/api/sessions/sess-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(10L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sess-1"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void addItem_forbiddenForWaiter() throws Exception {
        mockMvc.perform(post("/api/sessions/sess-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(10L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addItem_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/sessions/sess-1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddItemRequest(10L))))
                .andExpect(status().isUnauthorized());
    }

    // --- DELETE /api/sessions/{id}/items/{itemId} ---

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void removeItem_returnsUpdatedSessionForCustomer() throws Exception {
        when(sessionService.removeItem("sess-1", "order-item-1", "customer@test.com"))
                .thenReturn(sampleSession());

        mockMvc.perform(delete("/api/sessions/sess-1/items/order-item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sess-1"));
    }

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void removeItem_returnsUpdatedSessionForWaiter() throws Exception {
        when(sessionService.removeItem("sess-1", "order-item-1", "waiter@test.com"))
                .thenReturn(sampleSession());

        mockMvc.perform(delete("/api/sessions/sess-1/items/order-item-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sess-1"));
    }

    @Test
    void removeItem_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(delete("/api/sessions/sess-1/items/order-item-1"))
                .andExpect(status().isUnauthorized());
    }
}
