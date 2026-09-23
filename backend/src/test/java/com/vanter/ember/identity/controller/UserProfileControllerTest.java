package com.vanter.ember.identity.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.dto.UserProfileResponse;
import com.vanter.ember.identity.model.BannerKey;
import com.vanter.ember.identity.model.dto.AuthResponse;
import com.vanter.ember.identity.service.AuthService;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.service.UserProfileService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserProfileService userProfileService;
    @MockBean AuthService authService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    @Test
    void me_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ale@x.com", roles = "CUSTOMER")
    void me_returnsProfile_withNullBannerAsAbsent() throws Exception {
        when(userProfileService.getByEmail("ale@x.com"))
                .thenReturn(new UserProfileResponse("Ale", "ale@x.com", null));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ale"))
                .andExpect(jsonPath("$.email").value("ale@x.com"))
                .andExpect(jsonPath("$.bannerKey").doesNotExist());
    }

    @Test
    @WithMockUser(username = "ale@x.com", roles = "CUSTOMER")
    void me_serialisesBannerKeyInLowerCase() throws Exception {
        when(userProfileService.getByEmail("ale@x.com"))
                .thenReturn(new UserProfileResponse("Ale", "ale@x.com", BannerKey.OCEAN));

        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerKey").value("ocean"));
    }

    @Test
    @WithMockUser(username = "ale@x.com", roles = "CUSTOMER")
    void patch_updatesBanner_andEchoesIt() throws Exception {
        when(userProfileService.updateBanner(eq("ale@x.com"), eq(BannerKey.FOREST)))
                .thenReturn(new UserProfileResponse("Ale", "ale@x.com", BannerKey.FOREST));

        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerKey\":\"forest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerKey").value("forest"));

        verify(userProfileService).updateBanner("ale@x.com", BannerKey.FOREST);
    }

    @Test
    @WithMockUser(username = "ale@x.com", roles = "CUSTOMER")
    void patch_400_onUnknownBannerKey() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bannerKey\":\"neon-chartreuse\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ale@x.com", roles = "CUSTOMER")
    void patch_400_onMissingBannerKey() throws Exception {
        mockMvc.perform(patch("/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"NewSecret1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@x.com", roles = "ADMIN")
    void changePassword_returnsFreshToken() throws Exception {
        when(authService.changePassword("admin@x.com", "old-temp", "NewSecret1!"))
                .thenReturn(AuthResponse.builder().token("jwt-token").mustChangePassword(false).build());

        mockMvc.perform(post("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-temp\",\"newPassword\":\"NewSecret1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    @WithMockUser(username = "admin@x.com", roles = "ADMIN")
    void changePassword_401_whenCurrentPasswordWrong() throws Exception {
        when(authService.changePassword("admin@x.com", "wrong", "NewSecret1!"))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"NewSecret1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@x.com", roles = "ADMIN")
    void changePassword_400_onWeakNewPassword() throws Exception {
        mockMvc.perform(post("/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-temp\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }
}
