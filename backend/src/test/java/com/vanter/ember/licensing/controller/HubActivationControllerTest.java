package com.vanter.ember.licensing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.service.HubActivationService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubActivationController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class HubActivationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean HubActivationService hubActivationService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final String VALID_BODY =
            "{\"licenseKey\":\"abc.def\",\"hardwareFingerprint\":\"fp-1\"}";

    @Test
    void activate_withNoAuthHeader_stillReaches200() throws Exception {
        when(hubActivationService.activate(any())).thenReturn(HubActivationResponse.builder()
                .name("Tenant Grill")
                .slug("tenant-grill")
                .adminName("Owner Admin")
                .adminEmail("owner@tenant-grill.local")
                .adminPasswordHash("bcrypt-hash")
                .build());

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("tenant-grill"))
                .andExpect(jsonPath("$.adminPasswordHash").value("bcrypt-hash"));
    }

    @Test
    void activate_returns400OnMissingFields() throws Exception {
        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activate_returns400OnInvalidLicense() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new InvalidLicenseException("La firma de license.key no es válida."));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activate_returns409OnAlreadyActivatedElsewhere() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new IllegalStateException("Esta licencia ya fue activada en otra PC."));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void activate_returns404WhenRestaurantNotFound() throws Exception {
        when(hubActivationService.activate(any()))
                .thenThrow(new ResourceNotFoundException("Restaurant not found"));

        mockMvc.perform(post("/hub-activations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound());
    }
}
