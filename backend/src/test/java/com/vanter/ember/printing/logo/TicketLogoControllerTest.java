package com.vanter.ember.printing.logo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.restaurant.service.PlanGateService;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TicketLogoController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class TicketLogoControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Autowired MockMvc mockMvc;
    @MockBean TicketLogoService ticketLogoService;
    @MockBean SettingService settingService;
    @MockBean PlanGateService planGateService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private MockMultipartFile logoFile() {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[] {1, 2, 3});
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void upload_isGatedByPlanAndStoresTheLogo() throws Exception {
        TenantContextHolder.setTenantId(TENANT);

        mockMvc.perform(multipart("/settings/ticket-logo").file(logoFile()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(planGateService).requirePlanAtLeast(TENANT, RestaurantPlan.STARTER, "branding");
        verify(ticketLogoService).store(eq(TENANT), any());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void upload_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT);

        mockMvc.perform(multipart("/settings/ticket-logo").file(logoFile()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void remove_deletesTheLogo() throws Exception {
        TenantContextHolder.setTenantId(TENANT);

        mockMvc.perform(delete("/settings/ticket-logo").with(csrf())).andExpect(status().isNoContent());

        verify(ticketLogoService).delete(TENANT);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void preview_returnsThePngWhenThereIsALogo() throws Exception {
        TenantContextHolder.setTenantId(TENANT);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(TENANT)).thenReturn(settings);
        when(ticketLogoService.loadBitonal(eq(TENANT), any())).thenReturn(Optional.of(new byte[] {9, 9}));

        mockMvc.perform(get("/settings/ticket-logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(new byte[] {9, 9}));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void preview_is404WhenThereIsNoLogo() throws Exception {
        TenantContextHolder.setTenantId(TENANT);
        RestaurantSettings settings = new RestaurantSettings();
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(TENANT)).thenReturn(settings);
        when(ticketLogoService.loadBitonal(eq(TENANT), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/settings/ticket-logo")).andExpect(status().isNotFound());
    }
}
