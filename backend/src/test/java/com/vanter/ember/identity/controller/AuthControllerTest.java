package com.vanter.ember.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.model.dto.LoginRequest;
import com.vanter.ember.identity.model.dto.RegisterRequest;
import com.vanter.ember.identity.service.AuthService;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    @Test
    void register_returns200WithTokenAndName() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("Secret123!");


        when(authService.register(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").userId("u-1").name("Ana").role("CUSTOMER").build()
        );

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.name").value("Ana"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void register_returns409WhenEmailAlreadyExists() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("Secret123!");


        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Email already in use"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void register_returns400ForInvalidBody() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("");
        req.setEmail("not-an-email");
        req.setPassword("x");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_returns400ForWeakPassword() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Ana");
        req.setEmail("ana@test.com");
        req.setPassword("alllowercase1!");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_returns200WithToken() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("secret");

        when(authService.login(any())).thenReturn(
                AuthResponse.builder().token("jwt-token").userId("u-1").name("Ana").role("CUSTOMER").build()
        );

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));
    }

    @Test
    void corsPreflightIsAllowedForAllowedOrigin() throws Exception {
        mockMvc.perform(options("/auth/register")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void login_returns401ForBadCredentials() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("ana@test.com");
        req.setPassword("wrong");

        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithPin_200_onSuccess() throws Exception {
        when(authService.loginWithPin(any())).thenReturn(
                AuthResponse.builder().token("jwt").userId("u1").name("W").role("WAITER").build());

        mockMvc.perform(post("/auth/login/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"))
                .andExpect(jsonPath("$.role").value("WAITER"));
    }

    @Test
    void loginWithPin_409_withCode_whenPinNotSet() throws Exception {
        when(authService.loginWithPin(any()))
                .thenThrow(new com.vanter.ember.identity.exception.PinNotSetException());

        mockMvc.perform(post("/auth/login/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PIN_NOT_SET"));
    }

    @Test
    void loginWithPin_423_withCode_whenLocked() throws Exception {
        when(authService.loginWithPin(any()))
                .thenThrow(new com.vanter.ember.identity.exception.PinLockedException());

        mockMvc.perform(post("/auth/login/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"w@test.com\",\"pin\":\"1234\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));
    }

    @Test
    void loginWithPin_400_whenPinMalformed() throws Exception {
        mockMvc.perform(post("/auth/login/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"w@test.com\",\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest());
    }
}
