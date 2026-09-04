package com.vanter.ember.settings.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression coverage for E-02 (QA_SIMULATION_REPORT.md): {@code PUT /settings} had no role
 * check at all, so a CUSTOMER who joined a table (and therefore carries a tenant-scoped JWT)
 * could rewrite the restaurant's tax rate and table count. GET is intentionally left open to
 * WAITER too — {@code TopNav} reads it on the waiter shell for the branding name.
 */
@WebMvcTest(SettingsController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class SettingsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean SettingService settingService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_okForAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(TENANT_ID)).thenReturn(settings);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void getSettings_okForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(TENANT_ID)).thenReturn(settings);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void getSettings_okForCustomer() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(TENANT_ID)).thenReturn(settings);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void getSettings_forbiddenForKitchen() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSettings_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_okForAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsPayload())))
                .andExpect(status().isOk());

        verify(settingService).updateSettings(eq(TENANT_ID), any());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateSettings_forbiddenForCustomer() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsPayload())))
                .andExpect(status().isForbidden());

        verify(settingService, never()).updateSettings(any(), any());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateSettings_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsPayload())))
                .andExpect(status().isForbidden());

        verify(settingService, never()).updateSettings(any(), any());
    }

    @Test
    void updateSettings_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(put("/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SettingsPayload())))
                .andExpect(status().isUnauthorized());

        verify(settingService, never()).updateSettings(any(), any());
    }
}
