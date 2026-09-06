package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.service.PlatformStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Stats", description = "Operator dashboard KPIs")
@RestController
@RequestMapping("/platform/stats")
@RequiredArgsConstructor
public class PlatformStatsController {

    private final PlatformStatsService platformStatsService;

    @Operation(summary = "Tenant counts by status + Hub counts by liveness")
    @GetMapping
    public ResponseEntity<PlatformStatsResponse> get() {
        return ResponseEntity.ok(platformStatsService.get());
    }
}
