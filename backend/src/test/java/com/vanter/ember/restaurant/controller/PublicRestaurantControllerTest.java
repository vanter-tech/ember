package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.model.Restaurant;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicRestaurantController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class PublicRestaurantControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RestaurantRepository restaurantRepository;
    @MockBean SettingService settingService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private Restaurant restaurant(UUID id, String slug) {
        return Restaurant.builder().id(id).name("Fallback Name").slug(slug).build();
    }

    @Test
    void getBranding_withoutAuthentication_returnsCuratedFields() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.findBySlug("acme-grill"))
                .thenReturn(Optional.of(restaurant(restaurantId, "acme-grill")));

        SettingsPayload payload = new SettingsPayload();
        payload.getBranding().setBusinessName("Acme Grill");
        payload.getBranding().setPrimaryThemeColor("#7a1315");
        payload.getBranding().setOpeningTime("08:00");
        payload.getBranding().setClosingTime("22:00");
        payload.getBranding().setRuc("secret-ruc");
        payload.getBranding().setPhone("secret-phone");

        RestaurantSettings settings = new RestaurantSettings();
        settings.setRestaurantId(restaurantId);
        settings.setPayload(payload);
        when(settingService.getSettings(restaurantId)).thenReturn(settings);

        mockMvc.perform(get("/public/restaurants/acme-grill/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("acme-grill"))
                .andExpect(jsonPath("$.businessName").value("Acme Grill"))
                .andExpect(jsonPath("$.primaryThemeColor").value("#7a1315"))
                .andExpect(jsonPath("$.openingTime").value("08:00"))
                .andExpect(jsonPath("$.closingTime").value("22:00"))
                .andExpect(jsonPath("$.ruc").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void getBranding_unknownSlug_returnsNotFound() throws Exception {
        when(restaurantRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/public/restaurants/ghost/branding"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getBranding_clearsTenantContextAfterRequest() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.findBySlug("acme-grill"))
                .thenReturn(Optional.of(restaurant(restaurantId, "acme-grill")));

        RestaurantSettings settings = new RestaurantSettings();
        settings.setRestaurantId(restaurantId);
        settings.setPayload(new SettingsPayload());
        when(settingService.getSettings(restaurantId)).thenReturn(settings);

        mockMvc.perform(get("/public/restaurants/acme-grill/branding"))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertNull(TenantContextHolder.getTenantId());
    }

    @Test
    void getBranding_settingsLookupFails_stillClearsTenantContext() throws Exception {
        UUID restaurantId = UUID.randomUUID();
        when(restaurantRepository.findBySlug("acme-grill"))
                .thenReturn(Optional.of(restaurant(restaurantId, "acme-grill")));
        when(settingService.getSettings(any())).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/public/restaurants/acme-grill/branding"))
                .andExpect(status().is5xxServerError());

        org.junit.jupiter.api.Assertions.assertNull(TenantContextHolder.getTenantId());
    }
}
