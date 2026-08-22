package com.vanter.ember.printing.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.dto.CreatePrintAgentRequest;
import com.vanter.ember.printing.dto.CreatedPrintAgentResponse;
import com.vanter.ember.printing.dto.PrintAgentResponse;
import com.vanter.ember.printing.service.PrintAgentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/printing/admin/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PrintAgentAdminController {

    private final PrintAgentService printAgentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedPrintAgentResponse create(@Valid @RequestBody CreatePrintAgentRequest request) {
        return printAgentService.create(TenantContextHolder.requireTenantId(), request.name());
    }

    @GetMapping
    public List<PrintAgentResponse> list() {
        return printAgentService.list(TenantContextHolder.requireTenantId());
    }

    @PatchMapping("/{id}")
    public PrintAgentResponse rename(@PathVariable UUID id, @RequestBody CreatePrintAgentRequest request) {
        return printAgentService.rename(TenantContextHolder.requireTenantId(), id, request.name());
    }

    @PostMapping("/{id}/regenerate-key")
    public CreatedPrintAgentResponse regenerateKey(@PathVariable UUID id) {
        return printAgentService.regenerateKey(TenantContextHolder.requireTenantId(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        printAgentService.revoke(TenantContextHolder.requireTenantId(), id);
    }
}
