package com.vanter.ember.platform.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.platform.config.PlatformSecurityConfig;
import com.vanter.ember.platform.model.dto.PlatformAuthResponse;
import com.vanter.ember.platform.model.dto.PlatformLoginRequest;
import com.vanter.ember.platform.model.dto.PlatformPasswordChangeRequest;
import com.vanter.ember.platform.service.PlatformAuthService;
import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformAuthController.class)
@Import({PlatformSecurityConfig.class, CorsConfig.class})
class PlatformAuthControllerTest {

    private static final String OPERATOR_EMAIL = "operator@ember.local";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean PlatformAuthService platformAuthService;
    @MockBean PlatformJwtService platformJwtService;
    @MockBean PlatformOperatorDetailsService platformOperatorDetailsService;

    private void authenticateAs(String token, String email) {
        when(platformJwtService.isTokenValid(token)).thenReturn(true);
        when(platformJwtService.extractSubject(token)).thenReturn(email);
        UserDetails userDetails = User.builder()
                .username(email)
                .password("ignored")
                .roles("PLATFORM_ADMIN")
                .build();
        when(platformOperatorDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
    }

    @Test
    void login_returns200WithToken() throws Exception {
        PlatformLoginRequest req = new PlatformLoginRequest();
        req.setEmail(OPERATOR_EMAIL);
        req.setPassword("ChangeMe123!");

        when(platformAuthService.login(any())).thenReturn(
                PlatformAuthResponse.builder()
                        .token("platform-jwt")
                        .operatorId(UUID.randomUUID())
                        .name("Platform Admin")
                        .email(OPERATOR_EMAIL)
                        .build()
        );

        mockMvc.perform(post("/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("platform-jwt"));
    }

    @Test
    void login_returns401ForBadCredentials() throws Exception {
        PlatformLoginRequest req = new PlatformLoginRequest();
        req.setEmail(OPERATOR_EMAIL);
        req.setPassword("wrong");

        when(platformAuthService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_returns400ForInvalidBody() throws Exception {
        PlatformLoginRequest req = new PlatformLoginRequest();
        req.setEmail("not-an-email");
        req.setPassword("");

        mockMvc.perform(post("/platform/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_returns401WithoutAuthHeader() throws Exception {
        PlatformPasswordChangeRequest req = new PlatformPasswordChangeRequest();
        req.setCurrentPassword("ChangeMe123!");
        req.setNewPassword("NewSecret123!");

        mockMvc.perform(patch("/platform/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_returns204WhenAuthenticated() throws Exception {
        authenticateAs("valid-token", OPERATOR_EMAIL);
        PlatformPasswordChangeRequest req = new PlatformPasswordChangeRequest();
        req.setCurrentPassword("ChangeMe123!");
        req.setNewPassword("NewSecret123!");

        mockMvc.perform(patch("/platform/auth/password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(platformAuthService).changePassword(eq(OPERATOR_EMAIL), any());
    }

    @Test
    void changePassword_returns401ForWrongCurrentPassword() throws Exception {
        authenticateAs("valid-token", OPERATOR_EMAIL);
        PlatformPasswordChangeRequest req = new PlatformPasswordChangeRequest();
        req.setCurrentPassword("WrongPassword1!");
        req.setNewPassword("NewSecret123!");

        org.mockito.Mockito.doThrow(new BadCredentialsException("Invalid credentials"))
                .when(platformAuthService).changePassword(eq(OPERATOR_EMAIL), any());

        mockMvc.perform(patch("/platform/auth/password")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
