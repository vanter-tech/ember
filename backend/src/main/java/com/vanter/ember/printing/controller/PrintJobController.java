package com.vanter.ember.printing.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.dto.PrintJobResponse;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.PrintDispatchService;
import com.vanter.ember.printing.service.PrintJobQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/printing/jobs")
@RequiredArgsConstructor
public class PrintJobController {

    private final PrintJobQueryService printJobQueryService;
    private final PrintDispatchService printDispatchService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<PrintJobResponse> list(
            @RequestParam(required = false) PrintJobStatus status, Pageable pageable) {
        return printJobQueryService.list(TenantContextHolder.requireTenantId(), status, pageable);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','WAITER')")
    public void retry(@PathVariable UUID id) {
        printDispatchService.retry(TenantContextHolder.requireTenantId(), id);
    }
}
