package com.vanter.ember.identity.controller;

import com.vanter.ember.identity.model.dto.SetPinRequest;
import com.vanter.ember.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Account", description = "Self-service credential management")
@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AuthService authService;

    @Operation(summary = "Set or replace the caller's quick-access PIN")
    @PostMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPin(@Valid @RequestBody SetPinRequest request, Authentication authentication) {
        authService.setPin(authentication.getName(), request);
    }

    @Operation(summary = "Remove the caller's quick-access PIN")
    @DeleteMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearPin(Authentication authentication) {
        authService.clearPin(authentication.getName());
    }
}
