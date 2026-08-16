package com.vanter.ember.platform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.platform.config.PlatformSecurityConfig;
import com.vanter.ember.platform.model.dto.PlatformAuditLogResponse;
import com.vanter.ember.platform.service.PlatformAuditLogService;
import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformAuditLogController.class)
@Import({PlatformSecurityConfig.class, CorsConfig.class})
class PlatformAuditLogControllerTest {

    private static final String OPERATOR_EMAIL = "operator@ember.local";
    private static final String TOKEN = "valid-token";

    @Autowired MockMvc mockMvc;

    @MockBean PlatformAuditLogService platformAuditLogService;
    @MockBean PlatformJwtService platformJwtService;
    @MockBean PlatformOperatorDetailsService platformOperatorDetailsService;

    private void authenticate() {
        when(platformJwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(platformJwtService.extractSubject(TOKEN)).thenReturn(OPERATOR_EMAIL);
        UserDetails userDetails = User.builder()
                .username(OPERATOR_EMAIL)
                .password("ignored")
                .roles("PLATFORM_ADMIN")
                .build();
        when(platformOperatorDetailsService.loadUserByUsername(OPERATOR_EMAIL)).thenReturn(userDetails);
    }

    private PlatformAuditLogResponse entry(UUID restaurantId) {
        return PlatformAuditLogResponse.builder()
                .id(UUID.randomUUID())
                .operatorId(UUID.randomUUID())
                .operatorEmail(OPERATOR_EMAIL)
                .restaurantId(restaurantId)
                .action("RESTAURANT_STATUS_UPDATED")
                .oldValue("ACTIVE")
                .newValue("SUSPENDED")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void getAll_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(get("/platform/audit-log"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAll_returns200WithPageOfEntries() throws Exception {
        authenticate();
        when(platformAuditLogService.getAuditLog(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(entry(null))));

        mockMvc.perform(get("/platform/audit-log").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("RESTAURANT_STATUS_UPDATED"));
    }

    @Test
    void getAll_filtersByRestaurantId() throws Exception {
        authenticate();
        UUID restaurantId = UUID.randomUUID();
        when(platformAuditLogService.getAuditLog(eq(restaurantId), any()))
                .thenReturn(new PageImpl<>(List.of(entry(restaurantId))));

        mockMvc.perform(get("/platform/audit-log")
                        .param("restaurantId", restaurantId.toString())
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].restaurantId").value(restaurantId.toString()));
    }
}
