package com.vanter.ember.settings.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
@Tag(name = "Settings", description = "Settings management")
@RestController
@RequestMapping("/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingService settingService;

    // WAITER can read (TopNav shows the branding name on the waiter shell too) and so can a
    // seated CUSTOMER (needs the real tax rate for their own order preview, E-05) — only ADMIN
    // may write.
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WAITER','CUSTOMER')")
    public ResponseEntity<SettingsPayload> getSettings() {
        UUID restaurantId = TenantContextHolder.requireTenantId();

        SettingsPayload payload = settingService.getSettings(restaurantId).getPayload();
        return ResponseEntity.ok(payload);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateSettings(@RequestBody @Valid SettingsPayload newPayload) {
        UUID restaurantId = TenantContextHolder.requireTenantId();
        settingService.updateSettings(restaurantId, newPayload);
        return ResponseEntity.ok().build();
    }

}
