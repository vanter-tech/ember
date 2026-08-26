package com.vanter.ember.licensing.controller;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.licensing.model.dto.HubActivationRequest;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.service.HubActivationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public — the Hub authenticates via the license signature itself, not a bearer token. This is
 * cloud-side only: a Hub install runs the same jar, and without {@code @Profile("!hub")} this
 * controller would also boot there, exposing an unauthenticated activation endpoint on the Hub's
 * own LAN (it currently fails closed only by accident, because a Hub has no
 * {@code HUB_LICENSE_PRIVATE_KEY} configured).
 */
@RestController
@RequestMapping("/hub-activations")
@RequiredArgsConstructor
@Profile("!hub")
public class HubActivationController {

    private final HubActivationService hubActivationService;

    @PostMapping
    public ResponseEntity<HubActivationResponse> activate(@Valid @RequestBody HubActivationRequest request)
            throws InvalidLicenseException {
        return ResponseEntity.ok(hubActivationService.activate(request));
    }
}
