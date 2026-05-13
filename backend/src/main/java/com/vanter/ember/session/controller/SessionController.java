package com.vanter.ember.session.controller;

import com.vanter.ember.session.dto.AddItemRequest;
import com.vanter.ember.session.dto.CreateSessionRequest;
import com.vanter.ember.session.dto.ExpandCapacityRequest;
import com.vanter.ember.session.dto.JoinSessionRequest;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.QrTokenService;
import com.vanter.ember.session.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sessions", description = "Dining session lifecycle")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final QrTokenService qrTokenService;

    @Operation(summary = "Create a session (WAITER)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Session createSession(@Valid @RequestBody CreateSessionRequest request,
                                 Authentication authentication) {
        return sessionService.createSession(
                request.tableId(), authentication.getName(), request.maxParticipants());
    }

    @Operation(summary = "Get session by ID")
    @GetMapping("/{id}")
    public Session getSession(@PathVariable String id, Authentication authentication) {
        Session session = sessionService.findById(id);
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        if (isCustomer) {
            boolean isParticipant = session.getParticipants().stream()
                    .anyMatch(p -> p.getUserId().equals(authentication.getName()));
            if (!isParticipant) {
                throw new AccessDeniedException("Not authorized to view this session");
            }
        }
        return session;
    }

    @Operation(summary = "Generate QR token for a session (WAITER)")
    @GetMapping("/{id}/qr")
    @PreAuthorize("hasRole('WAITER')")
    public Map<String, String> getQrToken(@PathVariable String id, Authentication authentication) {
        Session session = sessionService.findById(id);
        if (!session.getWaiterId().equals(authentication.getName())) {
            throw new AccessDeniedException("Only the assigned waiter can generate QR codes");
        }
        String token = qrTokenService.generateQrToken(session.getId(), session.getMaxParticipants());
        return Map.of("qrToken", token);
    }

    @Operation(summary = "Join a session via QR token (CUSTOMER)")
    @PostMapping("/{id}/join")
    public Session joinSession(@PathVariable String id,
                               @Valid @RequestBody JoinSessionRequest request,
                               Authentication authentication) {
        return sessionService.joinSession(request.qrToken(), authentication.getName(), request.userName());
    }

    @Operation(summary = "Expand session capacity (WAITER)")
    @PatchMapping("/{id}/capacity")
    @PreAuthorize("hasRole('WAITER')")
    public Session expandCapacity(@PathVariable String id,
                                  @Valid @RequestBody ExpandCapacityRequest request,
                                  Authentication authentication) {
        return sessionService.expandCapacity(id, authentication.getName(), request.additional());
    }

    @Operation(summary = "Add item to session (CUSTOMER)")
    @PostMapping("/{id}/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Session addItem(@PathVariable String id,
                           @Valid @RequestBody AddItemRequest request,
                           Authentication authentication) {
        return sessionService.addItem(id, authentication.getName(), request.menuItemId());
    }

    @Operation(summary = "Remove item from session (CUSTOMER/WAITER)")
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WAITER')")
    public Session removeItem(@PathVariable String id,
                              @PathVariable String itemId,
                              Authentication authentication) {
        return sessionService.removeItem(id, itemId, authentication.getName());
    }
}
