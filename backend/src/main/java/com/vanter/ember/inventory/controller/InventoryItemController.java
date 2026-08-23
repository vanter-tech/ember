package com.vanter.ember.inventory.controller;

import com.vanter.ember.inventory.dto.InventoryItemRequest;
import com.vanter.ember.inventory.dto.InventoryItemResponse;
import com.vanter.ember.inventory.dto.InventoryItemUpdateRequest;
import com.vanter.ember.inventory.dto.RestockRequest;
import com.vanter.ember.inventory.service.InventoryService;
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

@Tag(name = "Inventory", description = "Basic per-menu-item stock tracking")
@RestController
@RequestMapping("/catalog/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class InventoryItemController {

    private final InventoryService inventoryService;

    @Operation(summary = "List all tracked inventory items")
    @GetMapping
    public List<InventoryItemResponse> getAll() {
        return inventoryService.findAll();
    }

    @Operation(summary = "Start tracking inventory for a menu item")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryItemResponse create(@Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.create(request);
    }

    @Operation(summary = "Edit unit/low-stock threshold")
    @PatchMapping("/{id}")
    public InventoryItemResponse update(
            @PathVariable Long id, @Valid @RequestBody InventoryItemUpdateRequest request) {
        return inventoryService.update(id, request);
    }

    @Operation(summary = "Restock (or correct) an item's current stock")
    @PostMapping("/{id}/restock")
    public InventoryItemResponse restock(@PathVariable Long id, @Valid @RequestBody RestockRequest request) {
        return inventoryService.restock(id, request.getDelta());
    }

    @Operation(summary = "Stop tracking inventory for a menu item")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        inventoryService.delete(id);
    }
}
