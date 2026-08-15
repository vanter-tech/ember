package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformAuthResponse;
import com.vanter.ember.platform.model.dto.PlatformLoginRequest;
import com.vanter.ember.platform.model.dto.PlatformPasswordChangeRequest;
import com.vanter.ember.platform.service.PlatformAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Auth", description = "Platform-operator login and self-service password change")
@RestController
@RequestMapping("/platform/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

    private final PlatformAuthService platformAuthService;

    @Operation(summary = "Login and obtain a platform-operator JWT")
    @PostMapping("/login")
    public ResponseEntity<PlatformAuthResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ResponseEntity.ok(platformAuthService.login(request));
    }

    @Operation(summary = "Change the authenticated operator's own password")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PlatformPasswordChangeRequest request,
                                                Authentication authentication) {
        platformAuthService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
