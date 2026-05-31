package com.vanter.ember.identity.controller;

import com.vanter.ember.identity.dto.UpdateUserRoleRequest;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin", description = "User and role management (ADMIN only)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "Assign a role to a user (ADMIN)")
    @PatchMapping("/users/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public User updateRole(@PathVariable String userId,
                           @Valid @RequestBody UpdateUserRoleRequest request) {
        return userAdminService.updateRole(userId, request.role());
    }
}
