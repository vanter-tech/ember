package com.vanter.ember.platform.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.platform.config.PlatformSecurityConfig;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.HubCounts;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.TenantCounts;
import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import com.vanter.ember.platform.service.PlatformStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformStatsController.class)
@Import({PlatformSecurityConfig.class, CorsConfig.class})
class PlatformStatsControllerTest {

    private static final String OPERATOR_EMAIL = "operator@ember.local";
    private static final String TOKEN = "valid-token";

    @Autowired MockMvc mockMvc;

    @MockBean PlatformStatsService platformStatsService;
    @MockBean PlatformJwtService platformJwtService;
    @MockBean PlatformOperatorDetailsService platformOperatorDetailsService;

    private void authenticate() {
        when(platformJwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(platformJwtService.extractSubject(TOKEN)).thenReturn(OPERATOR_EMAIL);
        UserDetails userDetails = User.builder()
                .username(OPERATOR_EMAIL).password("ignored").roles("PLATFORM_ADMIN").build();
        when(platformOperatorDetailsService.loadUserByUsername(OPERATOR_EMAIL)).thenReturn(userDetails);
    }

    @Test
    void get_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(get("/platform/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_returns200WithCounts() throws Exception {
        authenticate();
        when(platformStatsService.get()).thenReturn(new PlatformStatsResponse(
                new TenantCounts(5, 2, 1), new HubCounts(3, 1, 0, 2)));

        mockMvc.perform(get("/platform/stats").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants.active").value(5))
                .andExpect(jsonPath("$.hubs.never").value(2));
    }
}
