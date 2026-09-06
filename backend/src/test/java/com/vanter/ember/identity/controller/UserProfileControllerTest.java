package com.vanter.ember.identity.controller;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.dto.UserProfileResponse;
import com.vanter.ember.identity.model.BannerKey;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.service.UserProfileService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserProfileControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserProfileService userProfileService;
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
}
