package com.vanter.ember.printing.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.KitchenTicketPrintService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(KitchenTicketController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class KitchenTicketControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean KitchenTicketPrintService kitchenTicketPrintService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void printTicket_200_withJobId() throws Exception {
        PrintJob job = PrintJob.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000002"))
                .status(PrintJobStatus.PENDING)
                .build();
        when(kitchenTicketPrintService.enqueue(eq("ko-1"))).thenReturn(job);

        mockMvc.perform(post("/printing/kitchen-orders/ko-1/ticket").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("00000000-0000-0000-0000-000000000002"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void printTicket_403_forAdmin() throws Exception {
        mockMvc.perform(post("/printing/kitchen-orders/ko-1/ticket").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void printTicket_403_forWaiter() throws Exception {
        mockMvc.perform(post("/printing/kitchen-orders/ko-1/ticket").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void printTicket_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/printing/kitchen-orders/ko-1/ticket").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
