package com.vanter.ember.loyalty.controller;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.loyalty.dto.LoyaltyAccountResponse;
import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;
import com.vanter.ember.loyalty.service.LoyaltyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loyalty", description = "Customer-facing loyalty account (CUSTOMER only)")
@RestController
@RequestMapping("/loyalty/accounts")
@RequiredArgsConstructor
public class LoyaltyAccountController {

    private final LoyaltyAccountService loyaltyAccountService;
    private final UserRepository userRepository;

    @Operation(summary = "Caller's own loyalty account for the current tenant (CUSTOMER)")
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public LoyaltyAccountResponse me(Authentication authentication) {
        return loyaltyAccountService.getMyAccount(
                TenantContextHolder.requireTenantId(), resolveUserId(authentication));
    }

    @Operation(summary = "Caller's most recent visits for the current tenant (CUSTOMER); "
            + "404 if no tenant is bound (customer has never joined a table anywhere)")
    @GetMapping("/me/visits")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<LoyaltyVisitResponse> myVisits(Authentication authentication) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No tenant context — join a table first");
        }
        return loyaltyAccountService.getMyVisits(tenantId, resolveUserId(authentication));
    }

    private String resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + authentication.getName()));
    }
}
