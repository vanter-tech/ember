package com.vanter.ember.identity.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.List;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaiterDirectoryController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class WaiterDirectoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void listWaiters_returnsSummariesOnly() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(any(), eq(Role.WAITER)))
                .thenReturn(List.of(User.builder().id("u1").name("Ana").email("ana@x.com")
                        .role(Role.WAITER).active(true).passwordHash("SECRET").build()));

        mockMvc.perform(get("/identity/waiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u1"))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].email").value("ana@x.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void listWaiters_403_forKitchen() throws Exception {
        mockMvc.perform(get("/identity/waiters")).andExpect(status().isForbidden());
    }
}
