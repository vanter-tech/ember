package com.vanter.ember.export.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.export.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Export", description = "Tenant business-data Excel export (ADMIN only)")
@RestController
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ExportController {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExportService exportService;

    @Operation(
            summary = "Download the tenant's sales and product-performance history as an .xlsx workbook",
            description = "'from'/'to' are optional inclusive ISO date-times; they default to the "
                    + "tenant's whole history up to now, the same rule every analytics read uses.")
    @GetMapping
    public ResponseEntity<byte[]> exportData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        byte[] workbook = exportService.buildTenantExportWorkbook(TenantContextHolder.requireTenantId(), from, to);
        String filename = "ember-export-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf(XLSX_CONTENT_TYPE))
                .body(workbook);
    }
}
