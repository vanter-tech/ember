package com.vanter.ember.identity.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.WaiterSummary;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
public class WaiterDirectoryController {

    private final UserRepository userRepository;

    @Operation(summary = "List active waiters for the current tenant (WAITER/ADMIN)")
    @GetMapping("/waiters")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<WaiterSummary> listWaiters() {
        return userRepository
                .findByRestaurantId_IdAndRoleAndActiveTrue(
                        TenantContextHolder.requireTenantId(), Role.WAITER)
                .stream().map(WaiterSummary::from).toList();
    }
}
