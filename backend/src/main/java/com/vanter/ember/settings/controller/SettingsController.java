package com.vanter.ember.settings.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@Tag(name = "Settings", description = "Settings management")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;

    @GetMapping
    public ResponseEntity<SettingsPayload> getSettings() {
        UUID restaurantId = TenantContextHolder.requireTenantId();

        SettingsPayload payload = settingService.getSettings(restaurantId).getPayload();
        return ResponseEntity.ok(payload);
    }

    @PutMapping
    public ResponseEntity<Void> updateSettings(@RequestBody @Valid SettingsPayload newPayload) {
        UUID restaurantId = TenantContextHolder.requireTenantId();
        settingService.updateSettings(restaurantId, newPayload);
        return ResponseEntity.ok().build();
    }

}
