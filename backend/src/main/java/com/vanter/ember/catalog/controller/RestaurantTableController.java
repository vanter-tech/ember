package com.vanter.ember.catalog.controller;

import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.model.dto.RestaurantTableResponse;
import com.vanter.ember.catalog.service.RestaurantTableService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tables", description = "Restaurant table management")
@RestController
@RequestMapping("/catalog/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    @Operation(summary = "List all tables")
    @GetMapping
    public List<RestaurantTableResponse> getAll() {
        return tableService.findAll();
    }

    @Operation(summary = "Get table by ID")
    @GetMapping("/{id}")
    public RestaurantTableResponse getById(@PathVariable Long id) {
        return tableService.findById(id);
    }

    @Operation(summary = "Create a table (ADMIN)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantTableResponse create(@Valid @RequestBody RestaurantTableRequest request) {
        return tableService.create(request);
    }

    @Operation(summary = "Update table capacity (ADMIN)")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantTableResponse update(@PathVariable Long id,
            @Valid @RequestBody RestaurantTableRequest request) {
        return tableService.updateCapacity(id, request.getCapacity());
    }

    @Operation(summary = "Update table status (WAITER/ADMIN)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WAITER') or hasRole('ADMIN')")
    public RestaurantTableResponse updateStatus(@PathVariable Long id,
            @RequestParam TableStatus status) {
        if (status == TableStatus.OCCUPIED) {
            return tableService.setOccupied(id);
        }
        return tableService.setAvailable(id);
    }

    @Operation(summary = "Delete a table (ADMIN)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        tableService.delete(id);
    }
}
