package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformAuditLogResponse;
import com.vanter.ember.platform.service.PlatformAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Audit Log", description = "Operator action history")
@RestController
@RequestMapping("/platform/audit-log")
@RequiredArgsConstructor
public class PlatformAuditLogController {

    private final PlatformAuditLogService platformAuditLogService;

    @Operation(summary = "List operator audit-log entries, paginated and optionally filtered by restaurantId")
    @GetMapping
    public ResponseEntity<Page<PlatformAuditLogResponse>> getAll(
            @RequestParam(required = false) UUID restaurantId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(platformAuditLogService.getAuditLog(restaurantId, pageable));
    }
}
