package com.vanter.ember.licensing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.service.HubHeartbeatService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubHeartbeatController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class HubHeartbeatControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean HubHeartbeatService hubHeartbeatService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final String VALID_BODY =
            "{\"licenseKey\":\"abc.def\",\"hardwareFingerprint\":\"fp-1\"}";

    @Test
    void heartbeat_noAuthHeader_reaches200() throws Exception {
        when(hubHeartbeatService.heartbeat(any())).thenReturn(HubHeartbeatResponse.builder()
                .status("OK").serverTime(Instant.now()).latestVersion(null).build());

        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void heartbeat_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void heartbeat_invalidLicense_returns400() throws Exception {
        when(hubHeartbeatService.heartbeat(any()))
                .thenThrow(new InvalidLicenseException("nope"));

        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }
}
