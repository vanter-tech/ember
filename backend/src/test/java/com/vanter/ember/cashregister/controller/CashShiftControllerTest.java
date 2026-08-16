package com.vanter.ember.cashregister.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.CloseShiftRequest;
import com.vanter.ember.cashregister.dto.OpenShiftRequest;
import com.vanter.ember.cashregister.dto.RecordMovementRequest;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CashShiftController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CashShiftControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CashShiftService cashShiftService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private User sampleUser(String email) {
        return User.builder().id("user-1").email(email).name("Alice").role(Role.WAITER).build();
    }

    private CashShift sampleShift() {
        return CashShift.builder().id(1L).shiftNumber(1).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now()).build();
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void open_returnsCreatedForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(sampleUser("waiter@ember.local")));
        when(cashShiftService.openShift(any(), eq("user-1"), any(BigDecimal.class)))
                .thenReturn(sampleShift());
        when(cashShiftService.toResponse(any())).thenReturn(new CashShiftResponse(
                1L, 1, "OPEN", new BigDecimal("100.00"), "Alice", LocalDateTime.now(),
                null, null, null, null, null, null, null, null, null));

        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("100.00"));
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void open_forbiddenForAdmin() throws Exception {
        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("100.00"));
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordMovement_forbiddenForAdmin() throws Exception {
        RecordMovementRequest request =
                new RecordMovementRequest(CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop");
        mockMvc.perform(post("/cash-shifts/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void close_returnsOkAndRevealsVarianceForWaiter() throws Exception {
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(sampleUser("waiter@ember.local")));
        CashShift closed = sampleShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftService.closeShift(eq(1L), eq("user-1"), any(BigDecimal.class))).thenReturn(closed);
        when(cashShiftService.toResponse(any())).thenReturn(new CashShiftResponse(
                1L, 1, "CLOSED", new BigDecimal("100.00"), "Alice", LocalDateTime.now(), "Alice",
                LocalDateTime.now(), new BigDecimal("265.00"), new BigDecimal("260.00"),
                new BigDecimal("-5.00"), new BigDecimal("150.00"), new BigDecimal("0.00"),
                new BigDecimal("20.00"), new BigDecimal("5.00")));

        CloseShiftRequest request = new CloseShiftRequest(new BigDecimal("260.00"));
        mockMvc.perform(post("/cash-shifts/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(265.00))
                .andExpect(jsonPath("$.variance").value(-5.00));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void dailyReport_forbiddenForWaiter() throws Exception {
        mockMvc.perform(get("/cash-shifts/daily-report").param("date", "2026-08-16"))
                .andExpect(status().isForbidden());
    }
}
