package com.vanter.ember.cashregister.controller;

import com.vanter.ember.cashregister.dto.CashDrawerEventResponse;
import com.vanter.ember.cashregister.dto.CashReceiptStatusResponse;
import com.vanter.ember.cashregister.dto.ManualOpenRequest;
import com.vanter.ember.cashregister.service.CashDrawerService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cash Drawer", description = "Cobros en efectivo pendientes de recibir y apertura de la gaveta")
@RestController
@RequestMapping("/cash-drawer")
@RequiredArgsConstructor
public class CashDrawerController {

    private final CashDrawerService cashDrawerService;
    private final UserRepository userRepository;

    @Operation(summary = "Pending cash receipts + the open shift's drawer events (ACCOUNTANT/ADMIN)")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    public List<CashDrawerEventResponse> current() {
        return cashDrawerService.listForPanel(TenantContextHolder.requireTenantId());
    }

    @Operation(summary = "Cash-drawer state of a table's cash payments, for the waiter (WAITER/ADMIN)")
    @GetMapping("/by-session/{sessionId}")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<CashReceiptStatusResponse> bySession(@PathVariable String sessionId) {
        return cashDrawerService.statusForSession(sessionId);
    }

    @Operation(summary = "Receive a pending cash sale and open the drawer; also retries a failed kick (ACCOUNTANT/ADMIN)")
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    public CashDrawerEventResponse receive(@PathVariable UUID id, Authentication authentication) {
        return cashDrawerService.receive(
                TenantContextHolder.requireTenantId(), id, resolveUserId(authentication));
    }

    @Operation(summary = "Dismiss a failed drawer opening of a received sale (ACCOUNTANT/ADMIN)")
    @PostMapping("/{id}/skip")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    public CashDrawerEventResponse skip(@PathVariable UUID id, Authentication authentication) {
        return cashDrawerService.skipDrawer(id, resolveUserId(authentication));
    }

    @Operation(summary = "Open the drawer manually; reason required, audited (ACCOUNTANT needs an open shift)")
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','ADMIN')")
    public CashDrawerEventResponse open(
            @Valid @RequestBody ManualOpenRequest request, Authentication authentication) {
        boolean accountant = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ACCOUNTANT".equals(a.getAuthority()));
        return cashDrawerService.manualOpen(
                TenantContextHolder.requireTenantId(), resolveUserId(authentication), accountant,
                request.reason());
    }

    private String resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }
}
