package com.vanter.ember.settings.controller;

import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@Tag(name = "Settings", description = "Settings management")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<SettingsPayload> getSettings(Authentication authentication) {
        UUID restaurantId = getRestaurantIdFromAuth(authentication);

        SettingsPayload payload = settingService.getSettings(restaurantId).getPayload();
        return ResponseEntity.ok(payload);
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(
            @RequestBody @Valid SettingsPayload newPayload,
            Authentication authentication
    ){
        UUID restaurantId = getRestaurantIdFromAuth(authentication);
        settingService.updateSettings(restaurantId, newPayload);
        return ResponseEntity.ok().build();
    }

    private UUID getRestaurantIdFromAuth(Authentication authentication) {
        String userId = authentication.getName();
        return UUID.fromString(userRepository.findByEmail(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado")).getId());
    }

}
