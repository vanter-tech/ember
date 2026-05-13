package com.vanter.ember.catalog.controller;

import com.vanter.ember.catalog.model.RestaurantTable;
import com.vanter.ember.catalog.model.TableStatus;
import com.vanter.ember.catalog.model.dto.RestaurantTableRequest;
import com.vanter.ember.catalog.service.RestaurantTableService;
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

@RestController
@RequestMapping("/api/catalog/tables")
@RequiredArgsConstructor
public class RestaurantTableController {

    private final RestaurantTableService tableService;

    @GetMapping
    public List<RestaurantTable> getAll() {
        return tableService.findAll();
    }

    @GetMapping("/{id}")
    public RestaurantTable getById(@PathVariable Long id) {
        return tableService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantTable create(@Valid @RequestBody RestaurantTableRequest request) {
        return tableService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RestaurantTable update(@PathVariable Long id,
            @Valid @RequestBody RestaurantTableRequest request) {
        return tableService.updateCapacity(id, request.getCapacity());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('WAITER') or hasRole('ADMIN')")
    public RestaurantTable updateStatus(@PathVariable Long id,
            @RequestParam TableStatus status) {
        if (status == TableStatus.OCCUPIED) {
            return tableService.setOccupied(id);
        }
        return tableService.setAvailable(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        tableService.delete(id);
    }
}
