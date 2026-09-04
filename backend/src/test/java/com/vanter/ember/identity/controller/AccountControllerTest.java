package com.vanter.ember.identity.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    @Test
    @WithMockUser(username = "w@test.com")
    void setPin_204_onSuccess() throws Exception {
        mockMvc.perform(post("/account/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"pw\",\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
        verify(authService).setPin(eq("w@test.com"), any());
    }

    @Test
    @WithMockUser(username = "w@test.com")
    void setPin_400_whenPinMalformed() throws Exception {
        mockMvc.perform(post("/account/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"pw\",\"pin\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "w@test.com")
    void setPin_401_whenCurrentPasswordWrong() throws Exception {
        doThrow(new BadCredentialsException("x"))
                .when(authService).setPin(eq("w@test.com"), any());
        mockMvc.perform(post("/account/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"bad\",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void setPin_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/account/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"pw\",\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "w@test.com")
    void clearPin_204() throws Exception {
        mockMvc.perform(delete("/account/pin")).andExpect(status().isNoContent());
        verify(authService).clearPin("w@test.com");
    }
}
