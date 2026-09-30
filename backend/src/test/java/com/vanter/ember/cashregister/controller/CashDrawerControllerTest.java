package com.vanter.ember.cashregister.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.cashregister.dto.CashDrawerEventResponse;
import com.vanter.ember.cashregister.dto.CashReceiptStatusResponse;
import com.vanter.ember.cashregister.service.CashDrawerService;
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
import java.util.List;
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

@WebMvcTest(CashDrawerController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CashDrawerControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean CashDrawerService cashDrawerService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private User user(String email) {
        return User.builder().id("user-1").email(email).name("Alice").role(Role.ACCOUNTANT).build();
    }

    private CashDrawerEventResponse sampleResponse(UUID id) {
        return new CashDrawerEventResponse(id, "CASH_SALE", "RECEIVED", 3, new BigDecimal("20.00"), null,
                LocalDateTime.now(), LocalDateTime.now(), "OPENING", "Alice", null);
    }

    @Test
    @WithMockUser(username = "acc@ember.local", roles = "ACCOUNTANT")
    void current_okForAccountant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(cashDrawerService.listForPanel(TENANT_ID)).thenReturn(List.of(sampleResponse(UUID.randomUUID())));

        mockMvc.perform(get("/cash-drawer/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].drawer").value("OPENING"));
    }

    @Test
    @WithMockUser(username = "acc@ember.local", roles = "ACCOUNTANT")
    void receive_okForAccountant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("acc@ember.local")).thenReturn(Optional.of(user("acc@ember.local")));
        UUID id = UUID.randomUUID();
        when(cashDrawerService.receive(eq(TENANT_ID), eq(id), eq("user-1"))).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/cash-drawer/" + id + "/receive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @WithMockUser(username = "w@ember.local", roles = "WAITER")
    void everyEndpoint_isForbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/cash-drawer/current")).andExpect(status().isForbidden());
        mockMvc.perform(post("/cash-drawer/" + UUID.randomUUID() + "/receive")).andExpect(status().isForbidden());
        mockMvc.perform(post("/cash-drawer/" + UUID.randomUUID() + "/skip")).andExpect(status().isForbidden());
        mockMvc.perform(post("/cash-drawer/open").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"x\"}")).andExpect(status().isForbidden());

        verifyNoInteractions(cashDrawerService);
    }

    @Test
    @WithMockUser(username = "acc@ember.local", roles = "ACCOUNTANT")
    void open_withoutReason_isBadRequest() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(post("/cash-drawer/open").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "acc@ember.local", roles = "ACCOUNTANT")
    void open_accountantRequiresAnOpenShift() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("acc@ember.local")).thenReturn(Optional.of(user("acc@ember.local")));
        when(cashDrawerService.manualOpen(eq(TENANT_ID), eq("user-1"), eq(true), eq("cambio")))
                .thenReturn(sampleResponse(UUID.randomUUID()));

        mockMvc.perform(post("/cash-drawer/open").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"cambio\"}")).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin@ember.local", roles = "ADMIN")
    void open_adminDoesNotRequireAnOpenShift() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("admin@ember.local")).thenReturn(Optional.of(user("admin@ember.local")));
        when(cashDrawerService.manualOpen(eq(TENANT_ID), eq("user-1"), eq(false), eq("retiro")))
                .thenReturn(sampleResponse(UUID.randomUUID()));

        mockMvc.perform(post("/cash-drawer/open").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"retiro\"}")).andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "w@ember.local", roles = "WAITER")
    void bySession_okForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(cashDrawerService.statusForSession("s1"))
                .thenReturn(List.of(new CashReceiptStatusResponse("Ana", "PENDING")));

        mockMvc.perform(get("/cash-drawer/by-session/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantName").value("Ana"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "cust@ember.local", roles = "CUSTOMER")
    void bySession_forbiddenForCustomer() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/cash-drawer/by-session/s1")).andExpect(status().isForbidden());

        verifyNoInteractions(cashDrawerService);
    }

    @Test
    @WithMockUser(username = "acc@ember.local", roles = "ACCOUNTANT")
    void skip_okForAccountant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("acc@ember.local")).thenReturn(Optional.of(user("acc@ember.local")));
        UUID id = UUID.randomUUID();
        when(cashDrawerService.skipDrawer(id, "user-1")).thenReturn(sampleResponse(id));

        mockMvc.perform(post("/cash-drawer/" + id + "/skip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }
}
