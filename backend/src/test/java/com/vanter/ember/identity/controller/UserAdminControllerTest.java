package com.vanter.ember.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.dto.UpdateUserRoleRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.service.UserAdminService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserAdminController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class UserAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean UserAdminService userAdminService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private User waiterUser() {
        return User.builder()
                .id("u-1").name("John").email("john@test.com").role(Role.WAITER)
                .passwordHash("$2a$10$hashedpassword").build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_responseDoesNotExposePasswordHash() throws Exception {
        when(userAdminService.updateRole(eq("u-1"), any())).thenReturn(waiterUser());

        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_adminCanAssignWaiterRole() throws Exception {
        when(userAdminService.updateRole(eq("u-1"), any())).thenReturn(waiterUser());

        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.id").value("u-1"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateRole_forbiddenForWaiter() throws Exception {
        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateRole_forbiddenForCustomer() throws Exception {
        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateRole_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_returns400ForNullRole() throws Exception {
        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\": null}"))
                .andExpect(status().isBadRequest());
    }
}
