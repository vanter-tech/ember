package com.vanter.ember.catalog.controller;

import com.vanter.ember.catalog.model.dto.MenuItemRequest;
import com.vanter.ember.catalog.model.dto.MenuItemResponse;
import com.vanter.ember.catalog.service.MenuItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Menu Items", description = "Menu item management")
@RestController
@RequestMapping("/catalog/items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @Operation(summary = "List all menu items by category")
    @GetMapping
    public List<MenuItemResponse> getAll(@RequestParam(required = false) Long id) {
        return menuItemService.findAll(id);
    }

    @Operation(summary = "Get menu item by ID")
    @GetMapping("/{id}")
    public MenuItemResponse getById(@PathVariable Long id) {
        return menuItemService.findById(id);
    }

    @Operation(summary = "Create a menu item (ADMIN)")
    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemResponse create(
            @Valid @ModelAttribute MenuItemRequest menuItemRequest,
            @RequestPart(required = false) MultipartFile image) {
        return menuItemService.create(menuItemRequest, image);
    }

    @Operation(summary = "Update a menu item (ADMIN)")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemResponse update(
            @PathVariable Long id,
            @Valid @ModelAttribute MenuItemRequest menuItemRequest) {
        return menuItemService.update(id, menuItemRequest);
    }

    @Operation(summary = "Toggle availability (ADMIN)")
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    public MenuItemResponse toggleAvailability(@PathVariable Long id) {
        return menuItemService.toggleAvailability(id);
    }

    @Operation(summary = "Delete a menu item (ADMIN)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        menuItemService.delete(id);
    }
}
