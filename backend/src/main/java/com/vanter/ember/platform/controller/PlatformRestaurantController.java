package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformRestaurantDetailResponse;
import com.vanter.ember.platform.model.dto.PlatformRestaurantSummaryResponse;
import com.vanter.ember.platform.service.PlatformRestaurantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Restaurants", description = "Operator-facing tenant directory")
@RestController
@RequestMapping("/platform/restaurants")
@RequiredArgsConstructor
public class PlatformRestaurantController {

    private final PlatformRestaurantService platformRestaurantService;

    @Operation(summary = "List all tenants, paginated")
    @GetMapping
    public ResponseEntity<Page<PlatformRestaurantSummaryResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(platformRestaurantService.getAll(pageable));
    }

    @Operation(summary = "Tenant detail, including its ADMIN user(s)")
    @GetMapping("/{id}")
    public ResponseEntity<PlatformRestaurantDetailResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(platformRestaurantService.getById(id));
    }
}
