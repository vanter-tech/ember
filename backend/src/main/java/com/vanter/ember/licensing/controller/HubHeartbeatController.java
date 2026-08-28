package com.vanter.ember.licensing.controller;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.service.HubHeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public — the Hub authenticates via the license signature, not a bearer token. Cloud-side only:
 * {@code @Profile("!hub")} keeps it off a Hub's own LAN (same reasoning as
 * {@link com.vanter.ember.licensing.controller.HubActivationController}).
 */
@RestController
@RequestMapping("/hub-heartbeat")
@RequiredArgsConstructor
@Profile("!hub")
public class HubHeartbeatController {

    private final HubHeartbeatService hubHeartbeatService;

    @PostMapping
    public ResponseEntity<HubHeartbeatResponse> heartbeat(@Valid @RequestBody HubHeartbeatRequest request)
            throws InvalidLicenseException {
        return ResponseEntity.ok(hubHeartbeatService.heartbeat(request));
    }
}
