package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformRestaurantCreateRequest;
import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantStatusUpdateRequest;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.service.PlatformRestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Restaurants", description = "Operator-facing tenant directory")
@RestController
@RequestMapping("/platform/restaurants")
@RequiredArgsConstructor
public class PlatformRestaurantController {

    private final PlatformRestaurantService platformRestaurantService;

    @Operation(summary = "Operator-driven tenant onboarding: creates the restaurant and its initial ADMIN user")
    @PostMapping
    public ResponseEntity<PlatformRestaurantSummaryResponse> create(
            @Valid @RequestBody PlatformRestaurantCreateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.create(request, authentication.getName()));
    }

    @Operation(summary = "Issue a Hub license.key for this restaurant")
    @PostMapping(value = "/{id}/hub-license", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> issueHubLicense(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.issueHubLicense(id, authentication.getName()));
    }

    @Operation(summary = "List all tenants, paginated; soft-deleted excluded unless includeDeleted=true")
    @GetMapping
    public ResponseEntity<Page<PlatformRestaurantSummaryResponse>> getAll(
            Pageable pageable,
            @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        return ResponseEntity.ok(platformRestaurantService.getAll(pageable, includeDeleted));
    }

    @Operation(summary = "Soft-delete a restaurant (must be SUSPENDED); reversible via restore")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        platformRestaurantService.delete(id, authentication.getName());
    }

    @Operation(summary = "Restore a soft-deleted restaurant to SUSPENDED")
    @PostMapping("/{id}/restore")
    public ResponseEntity<PlatformRestaurantSummaryResponse> restore(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.restore(id, authentication.getName()));
    }

    @Operation(summary = "Tenant detail, including its ADMIN user(s)")
    @GetMapping("/{id}")
    public ResponseEntity<PlatformRestaurantDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(platformRestaurantService.getById(id));
    }

    @Operation(summary = "Update a tenant's status (suspend/reactivate), audited")
    @PatchMapping("/{id}/status")
    public ResponseEntity<PlatformRestaurantSummaryResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformRestaurantStatusUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                platformRestaurantService.updateStatus(id, request.getStatus(), authentication.getName()));
    }
}
