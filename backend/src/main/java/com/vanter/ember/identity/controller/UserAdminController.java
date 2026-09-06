package com.vanter.ember.identity.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.AdminSetPinRequest;
import com.vanter.ember.identity.dto.CreateStaffRequest;
import com.vanter.ember.identity.dto.StaffMemberResponse;
import com.vanter.ember.identity.dto.UpdateStaffProfileRequest;
import com.vanter.ember.identity.dto.UpdateUserRoleRequest;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "User and role management (ADMIN only)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Assign a role to a staff member of the caller's own tenant (ADMIN)")
    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(@PathVariable String userId,
                           @Valid @RequestBody UpdateUserRoleRequest request) {
        return userAdminService.updateRole(
                userId, TenantContextHolder.requireTenantId(), request.role());
    }

    @Operation(summary = "Create a new staff member for the current tenant (ADMIN)")
    @PostMapping("/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public StaffMemberResponse createStaff(@Valid @RequestBody CreateStaffRequest request) {
        return userAdminService.create(TenantContextHolder.requireTenantId(), request);
    }

    @Operation(summary = "List the current tenant's staff, i.e. every non-CUSTOMER user (ADMIN)")
    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public List<StaffMemberResponse> getStaff() {
        return userAdminService.getStaff(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Update a staff member's HR profile fields (ADMIN)")
    @PatchMapping("/staff/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public StaffMemberResponse updateStaffProfile(
            @PathVariable String userId,
            @Valid @RequestBody UpdateStaffProfileRequest request) {
        return userAdminService.updateProfile(
                userId, TenantContextHolder.requireTenantId(), request);
    }

    @Operation(summary = "Set or replace a staff member's quick-login PIN (ADMIN)")
    @PutMapping("/staff/{userId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void setStaffPin(@PathVariable String userId,
                            @Valid @RequestBody AdminSetPinRequest request) {
        userAdminService.setPin(userId, TenantContextHolder.requireTenantId(), request.pin());
    }

    @Operation(summary = "Remove a staff member's quick-login PIN (ADMIN)")
    @DeleteMapping("/staff/{userId}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void clearStaffPin(@PathVariable String userId) {
        userAdminService.clearPin(userId, TenantContextHolder.requireTenantId());
    }
}
