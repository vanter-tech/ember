package com.vanter.ember.loyalty.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.loyalty.dto.CreateLoyaltyRewardRequest;
import com.vanter.ember.loyalty.dto.LoyaltyRewardResponse;
import com.vanter.ember.loyalty.dto.UpdateLoyaltyRewardRequest;
import com.vanter.ember.loyalty.service.LoyaltyRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Loyalty", description = "Admin reward-catalog management (ADMIN only)")
@RestController
@RequestMapping("/loyalty/rewards")
@RequiredArgsConstructor
public class LoyaltyRewardController {

    private final LoyaltyRewardService loyaltyRewardService;

    @Operation(summary = "Create a reward catalog entry (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public LoyaltyRewardResponse create(@Valid @RequestBody CreateLoyaltyRewardRequest request) {
        return loyaltyRewardService.create(request);
    }

    @Operation(summary = "List all rewards for the current tenant, including inactive (ADMIN)")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LoyaltyRewardResponse> list() {
        return loyaltyRewardService.list(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Edit a reward's fields / toggle active (ADMIN)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public LoyaltyRewardResponse update(
            @PathVariable Long id, @Valid @RequestBody UpdateLoyaltyRewardRequest request) {
        return loyaltyRewardService.update(id, request);
    }
}
