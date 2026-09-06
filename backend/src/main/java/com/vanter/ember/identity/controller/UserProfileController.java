package com.vanter.ember.identity.controller;

import com.vanter.ember.identity.dto.UpdateProfileRequest;
import com.vanter.ember.identity.dto.UserProfileResponse;
import com.vanter.ember.identity.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "User profile", description = "The authenticated caller's own profile")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "The caller's own profile (name, email, chosen banner preset)")
    @GetMapping("/me")
    public UserProfileResponse me(Authentication authentication) {
        return userProfileService.getByEmail(authentication.getName());
    }

    @Operation(summary = "Update the caller's own banner preset")
    @PatchMapping("/me")
    public UserProfileResponse updateMe(
            Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userProfileService.updateBanner(authentication.getName(), request.bannerKey());
    }
}
