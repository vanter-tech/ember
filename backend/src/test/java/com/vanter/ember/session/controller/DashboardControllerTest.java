package com.vanter.ember.session.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.service.DashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean DashboardService dashboardService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private TableStatusResponse table(int number) {
        return TableStatusResponse.builder()
                .tableId(UUID.randomUUID())
                .tableNumber(number)
                .isOccupied(false)
                .build();
    }

    @Test
    @WithMockUser
    void liveStatus_usesTenantFromContext() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(dashboardService.getLiveStatus(TENANT_ID)).thenReturn(List.of(table(1)));

        mockMvc.perform(get("/dashboard/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableNumber").value(1));

        verify(dashboardService).getLiveStatus(TENANT_ID);
    }

    @Test
    @WithMockUser
    void liveStatus_ignoresClientSuppliedRestaurantId() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(dashboardService.getLiveStatus(TENANT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/dashboard/status").param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isOk());

        verify(dashboardService).getLiveStatus(TENANT_ID);
        verify(dashboardService, never()).getLiveStatus(OTHER_TENANT_ID);
    }

    @Test
    @WithMockUser
    void liveStatus_withoutTenantBound_isRejected() throws Exception {
        mockMvc.perform(get("/dashboard/status").param("restaurantId", OTHER_TENANT_ID.toString()))
                .andExpect(status().isConflict());

        verify(dashboardService, never()).getLiveStatus(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void liveStatus_withoutAuthentication_isUnauthorized() throws Exception {
        mockMvc.perform(get("/dashboard/status"))
                .andExpect(status().isUnauthorized());

        verify(dashboardService, never()).getLiveStatus(org.mockito.ArgumentMatchers.any());
    }
}
