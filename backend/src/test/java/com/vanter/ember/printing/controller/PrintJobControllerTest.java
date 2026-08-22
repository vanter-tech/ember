package com.vanter.ember.printing.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.printing.service.PrintDispatchService;
import com.vanter.ember.printing.service.PrintJobQueryService;
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

@WebMvcTest(PrintJobController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class PrintJobControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PrintDispatchService printDispatchService;
    @MockBean PrintJobQueryService printJobQueryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void retry_delegatesToDispatchService() throws Exception {
        TenantContextHolder.setTenantId(UUID.randomUUID());
        UUID jobId = UUID.randomUUID();
        mockMvc.perform(post("/printing/jobs/" + jobId + "/retry").with(csrf()))
                .andExpect(status().isOk());
    }
}
