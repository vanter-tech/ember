package com.vanter.ember.session.controller;

import com.vanter.ember.session.dto.CreateSessionRequest;
import com.vanter.ember.session.dto.ExpandCapacityRequest;
import com.vanter.ember.session.dto.JoinSessionRequest;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.service.QrTokenService;
import com.vanter.ember.session.service.SessionService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final QrTokenService qrTokenService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Session createSession(@Valid @RequestBody CreateSessionRequest request,
                                 Authentication authentication) {
        return sessionService.createSession(
                request.tableId(), authentication.getName(), request.maxParticipants());
    }

    @GetMapping("/{id}/qr")
    public Map<String, String> getQrToken(@PathVariable String id) {
        Session session = sessionService.findById(id);
        String token = qrTokenService.generateQrToken(session.getId(), session.getMaxParticipants());
        return Map.of("qrToken", token);
    }

    @PostMapping("/{id}/join")
    public Session joinSession(@PathVariable String id,
                               @Valid @RequestBody JoinSessionRequest request,
                               Authentication authentication) {
        return sessionService.joinSession(request.qrToken(), authentication.getName(), request.userName());
    }

    @PatchMapping("/{id}/capacity")
    @PreAuthorize("hasRole('WAITER')")
    public Session expandCapacity(@PathVariable String id,
                                  @Valid @RequestBody ExpandCapacityRequest request,
                                  Authentication authentication) {
        return sessionService.expandCapacity(id, authentication.getName(), request.additional());
    }
}
