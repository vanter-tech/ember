package com.vanter.ember.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.CreateStaffRequest;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateUserRoleRequest;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.identity.service.UserAdminService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    private User waiterUser() {
        return User.builder()
                .id("u-1").name("John").email("john@test.com").role(Role.WAITER)
                .passwordHash("$2a$10$hashedpassword").build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_responseDoesNotExposePasswordHash() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.updateRole(eq("u-1"), eq(TENANT_ID), any())).thenReturn(waiterUser());

        mockMvc.perform(patch("/admin/users/u-1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateUserRoleRequest(Role.WAITER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRole_adminCanAssignWaiterRole() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.updateRole(eq("u-1"), eq(TENANT_ID), any())).thenReturn(waiterUser());

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

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStaff_returnsCreatedForAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.create(eq(TENANT_ID), any())).thenReturn(new StaffMemberResponse(
                "u-new", "Ana", "ana@test.com", Role.WAITER, Instant.now(),
                true, null, null, null, null, null, BigDecimal.ZERO, false));

        CreateStaffRequest request = new CreateStaffRequest(
                "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro");
        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("u-new"))
                .andExpect(jsonPath("$.role").value("WAITER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void createStaff_forbiddenForWaiter() throws Exception {
        CreateStaffRequest request = new CreateStaffRequest(
                "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro");
        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createStaff_unauthenticatedReturns401() throws Exception {
        CreateStaffRequest request = new CreateStaffRequest(
                "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro");
        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStaff_returns400ForWeakPassword() throws Exception {
        CreateStaffRequest request = new CreateStaffRequest(
                "Ana", "ana@test.com", "weak", Role.WAITER,
                "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro");
        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createStaff_returns400ForBlankJobTitle() throws Exception {
        CreateStaffRequest request = new CreateStaffRequest(
                "Ana", "ana@test.com", "Sup3r$ecret", Role.WAITER,
                "", "Mañana", "Tiempo completo", "Sucursal Centro");
        mockMvc.perform(post("/admin/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getStaff_returnsStaffForCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.getStaff(TENANT_ID)).thenReturn(List.of(new StaffMemberResponse(
                "u-1", "Ana", "ana@test.com", Role.WAITER, Instant.now(),
                true, "Mesera", "Mañana", "Tiempo completo", null, null, BigDecimal.ZERO, false)));

        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u-1"))
                .andExpect(jsonPath("$[0].role").value("WAITER"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void getStaff_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getStaff_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/admin/staff"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateStaffProfile_updatesActiveFlag() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userAdminService.updateProfile(eq("u-1"), eq(TENANT_ID), any())).thenReturn(
                new StaffMemberResponse(
                        "u-1", "Ana", "ana@test.com", Role.WAITER, Instant.now(),
                        false, "Mesera", null, null, null, null, BigDecimal.ZERO, false));

        mockMvc.perform(patch("/admin/staff/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateStaffProfile_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(patch("/admin/staff/u-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setStaffPin_noContentForAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/admin/staff/u-1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
        verify(userAdminService).setPin("u-1", TENANT_ID, "1234");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void setStaffPin_returns400ForMalformedPin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/admin/staff/u-1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"abc\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void setStaffPin_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(put("/admin/staff/u-1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void setStaffPin_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(put("/admin/staff/u-1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void clearStaffPin_noContentForAdmin() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(delete("/admin/staff/u-1/pin"))
                .andExpect(status().isNoContent());
        verify(userAdminService).clearPin("u-1", TENANT_ID);
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void clearStaffPin_forbiddenForWaiter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);

        mockMvc.perform(delete("/admin/staff/u-1/pin"))
                .andExpect(status().isForbidden());
    }
}
