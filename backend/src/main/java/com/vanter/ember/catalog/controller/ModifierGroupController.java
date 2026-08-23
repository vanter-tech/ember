package com.vanter.ember.catalog.controller;

import com.vanter.ember.catalog.model.dto.ModifierGroupRequest;
import com.vanter.ember.catalog.model.dto.ModifierGroupResponse;
import com.vanter.ember.catalog.model.dto.ModifierOptionRequest;
import com.vanter.ember.catalog.service.ModifierGroupService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Modifier Groups", description = "Reusable menu item modifier group management")
@RestController
@RequestMapping("/catalog/modifier-groups")
@RequiredArgsConstructor
public class ModifierGroupController {

    private final ModifierGroupService modifierGroupService;

    @Operation(summary = "List all modifier groups, including inactive")
    @GetMapping
    public List<ModifierGroupResponse> getAll() {
        return modifierGroupService.findAll();
    }

    @Operation(summary = "Create a modifier group with its options (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse create(@Valid @RequestBody ModifierGroupRequest request) {
        return modifierGroupService.create(request);
    }

    @Operation(summary = "Update a modifier group (ADMIN)")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse update(@PathVariable Long id, @Valid @RequestBody ModifierGroupRequest request) {
        return modifierGroupService.update(id, request);
    }

    @Operation(summary = "Activate/deactivate a modifier group (ADMIN)")
    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse setActive(@PathVariable Long id, @RequestBody boolean active) {
        return modifierGroupService.setActive(id, active);
    }

    @Operation(summary = "Add an option to a modifier group (ADMIN)")
    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse addOption(@PathVariable Long id, @Valid @RequestBody ModifierOptionRequest request) {
        return modifierGroupService.addOption(id, request);
    }

    @Operation(summary = "Update a modifier option (ADMIN)")
    @PatchMapping("/{id}/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse updateOption(
            @PathVariable Long id, @PathVariable Long optionId, @Valid @RequestBody ModifierOptionRequest request) {
        return modifierGroupService.updateOption(id, optionId, request);
    }

    @Operation(summary = "Deactivate a modifier option, never hard-deleted (ADMIN)")
    @DeleteMapping("/{id}/options/{optionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ModifierGroupResponse deactivateOption(@PathVariable Long id, @PathVariable Long optionId) {
        return modifierGroupService.deactivateOption(id, optionId);
    }
}
