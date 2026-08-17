package com.vanter.ember.cashregister.controller;

import com.vanter.ember.cashregister.dto.CashMovementResponse;
import com.vanter.ember.cashregister.dto.CashShiftDetailResponse;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.CloseShiftRequest;
import com.vanter.ember.cashregister.dto.DailyReportResponse;
import com.vanter.ember.cashregister.dto.OpenShiftRequest;
import com.vanter.ember.cashregister.dto.RecordMovementRequest;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cash Register", description = "Apertura de Caja, Movimientos Manuales, Arqueo de Turnos, Corte Diario")
@RestController
@RequestMapping("/cash-shifts")
@RequiredArgsConstructor
public class CashShiftController {

    private final CashShiftService cashShiftService;
    private final UserRepository userRepository;

    @Operation(summary = "Open a new cash shift — Apertura de Caja (WAITER)")
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public CashShiftResponse open(@Valid @RequestBody OpenShiftRequest request, Authentication authentication) {
        CashShift shift = cashShiftService.openShift(
                TenantContextHolder.requireTenantId(), resolveUserId(authentication), request.openingFloat());
        return cashShiftService.toResponse(shift);
    }

    @Operation(summary = "Get the tenant's currently open shift (WAITER/ADMIN)")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public CashShiftResponse current() {
        return cashShiftService.toResponse(
                cashShiftService.getCurrentOpenShift(TenantContextHolder.requireTenantId()));
    }

    @Operation(summary = "List cash shift history (WAITER/ADMIN)")
    @GetMapping
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public Page<CashShiftResponse> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return cashShiftService
                .getHistory(TenantContextHolder.requireTenantId(), from, to, pageable)
                .map(cashShiftService::toResponse);
    }

    @Operation(summary = "Get one shift's detail including its movements (WAITER/ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public CashShiftDetailResponse detail(@PathVariable Long id) {
        return cashShiftService.getDetail(id);
    }

    @Operation(summary = "Record a manual cash movement on an open shift (WAITER)")
    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public CashMovementResponse recordMovement(
            @PathVariable Long id,
            @Valid @RequestBody RecordMovementRequest request,
            Authentication authentication) {
        CashMovement movement = cashShiftService.recordMovement(
                id, resolveUserId(authentication), request.type(), request.amount(), request.reason());
        return cashShiftService.toMovementResponse(movement);
    }

    @Operation(summary = "Close a shift with a blind cash count — Arqueo de Turno (WAITER)")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('WAITER')")
    public CashShiftResponse close(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request,
            Authentication authentication) {
        CashShift shift = cashShiftService.closeShift(id, resolveUserId(authentication), request.countedCash());
        return cashShiftService.toResponse(shift);
    }

    @Operation(summary = "Corte Diario: roll up every shift closed on the given business day (ADMIN)")
    @GetMapping("/daily-report")
    @PreAuthorize("hasRole('ADMIN')")
    public DailyReportResponse dailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashShiftService.getDailyReport(TenantContextHolder.requireTenantId(), date);
    }

    private String resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }
}
