package com.vanter.ember.session.controller;

import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.AuthService;
import com.vanter.ember.session.dto.*;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.service.QrTokenService;
import com.vanter.ember.session.service.SessionService;
import com.vanter.ember.settings.repository.DiningTableRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final QrTokenService qrTokenService;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Operation(summary = "Create a session (WAITER)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public SessionCreatedResponse createSession(@Valid @RequestBody CreateSessionRequest request,
                                 Authentication authentication) {
        Session savedSession =  sessionService.createSession(
                request.tableId(), authentication.getName(), request.maxParticipants());

        return new SessionCreatedResponse(savedSession.getId(), savedSession.getJoinCode());
    }

    @Operation(summary = "Get session by ID")
    @GetMapping("/{id}")
    public SessionDetailResponseDto getSession(@PathVariable String id, Authentication authentication) {
        SessionDetailResponseDto session = sessionService.getSessionDetails(id);
        boolean isCustomer = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CUSTOMER"));
        if (isCustomer) {
            String requesterId = userRepository.findByEmail(authentication.getName())
                    .map(u -> u.getId())
                    .orElse(null);
            boolean isParticipant = session.participants().stream()
                    .anyMatch(p -> p.userId().equals(requesterId));
            if (!isParticipant) {
                throw new AccessDeniedException("Not authorized to view this session");
            }
        }
        return session;
    }

    @Operation(summary = "Get table status")
    @GetMapping("/{id}/status")
    public SessionStatusDto getSessionStatus(@PathVariable String id){
        SessionStatus status = sessionService.getSessionStatus(id);
        return new SessionStatusDto(status);
    }

    @Operation(summary = "Generate QR token for a session (WAITER)")
    @GetMapping("/{id}/qr")
    @PreAuthorize("hasRole('WAITER')")
    public Map<String, String> getQrToken(@PathVariable String id, Authentication authentication) {
        Session session = sessionService.findById(id);
        if (!session.getWaiterId().equals(authentication.getName())) {
            throw new AccessDeniedException("Only the assigned waiter can generate QR codes");
        }
        String token = qrTokenService.generateQrToken(session.getId());
        return Map.of("qrToken", token);
    }

    @Operation(summary = "Join a session via QR token (CUSTOMER)")
    @PostMapping("/{id}/join")
    @PreAuthorize("hasRole('CUSTOMER')")
    public JoinSessionResponse joinSession(@PathVariable String id,
                               @Valid @RequestBody JoinSessionRequest request,
                               Authentication authentication) {
        Session session = sessionService.joinSession(
                request.qrToken(), authentication.getName(), request.userName());
        return withRescopedToken(session, authentication);
    }

    @Operation(summary = "Join a session via Code (CUSTOMER)")
    @PostMapping("/join")
    @PreAuthorize("hasRole('CUSTOMER')")
    public JoinSessionResponse joinSessionCode(@Valid @RequestBody JoinSessionCodeRequest request,
                                   Authentication authentication) {
        Session session = sessionService.joinSessionCode(
                request.joinCode(), authentication.getName());
        return withRescopedToken(session, authentication);
    }

    /**
     * Joining a table is the moment a customer's restaurant becomes known, so it is also where
     * their token stops being tenant-less and starts carrying the {@code rid} every later
     * tenant-scoped read needs.
     */
    private JoinSessionResponse withRescopedToken(Session session, Authentication authentication) {
        String token = authService
                .issueTenantScopedToken(authentication.getName(), session.getTenantId())
                .getToken();
        return new JoinSessionResponse(session, token);
    }

    @Operation(summary = "Expand session capacity (WAITER)")
    @PatchMapping("/{id}/capacity")
    @PreAuthorize("hasRole('WAITER')")
    public Session expandCapacity(@PathVariable String id,
                                  @Valid @RequestBody ExpandCapacityRequest request,
                                  Authentication authentication) {
        return sessionService.expandCapacity(id, authentication.getName(), request.additional());
    }

    @Operation(summary = "Add item to session/Order (CUSTOMER)")
    @PostMapping("/{id}/items")
    @PreAuthorize("hasRole('CUSTOMER')")
    public Session addItem(@PathVariable String id,
                           @Valid @RequestBody AddItemRequest request,
                           Authentication authentication) {
        return sessionService.addItem(id, authentication.getName(), request.menuItemId());
    }

    @Operation(summary = "Send item to KITCHEN")
    @PostMapping("/{sessionId}/participants/{userId}/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> confirmMyOrder(@PathVariable String sessionId, @PathVariable String userId,
                                             Authentication authentication){
        sessionService.confirmDraftsForUser(sessionId, userId, authentication.getName());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove item from session (CUSTOMER/WAITER)")
    @DeleteMapping("/{id}/items/{itemId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'WAITER')")
    public Session removeItem(@PathVariable String id,
                              @PathVariable String itemId,
                              Authentication authentication) {
        return sessionService.removeItem(id, itemId, authentication.getName());
    }
    @Operation(summary = "Close session with no items (WAITER)")
    @DeleteMapping("/{sessionId}/cancel")
    @PreAuthorize("hasRole('WAITER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void closeEmptySession(@PathVariable String sessionId, Authentication authentication) {
        sessionService.closeEmptySession(sessionId);
    }

}
