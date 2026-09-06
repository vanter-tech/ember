package com.vanter.ember.licensing.service;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Cloud-side answer to a Hub's periodic license heartbeat: verifies the signed license, confirms
 * the calling PC is the one this restaurant activated on, and reports whether the restaurant is
 * still allowed to operate (spec 2026-08-28-hub-license-heartbeat-design.md). Never echoes any
 * restaurant/admin data — only {status, serverTime, latestVersion}.
 */
@Service
public class HubHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HubHeartbeatService.class);

    private final LicenseIssuingService licenseIssuingService;
    private final HubActivationRepository hubActivationRepository;
    private final RestaurantRepository restaurantRepository;
    private final String latestVersion;

    // Hand-written constructor (NOT @RequiredArgsConstructor): LicenseIssuingService is a
    // @Lazy @Service and Lombok will not copy a field-level @Lazy onto the generated constructor,
    // which forces eager construction and throws on the unset HUB_LICENSE_PRIVATE_KEY placeholder
    // outside the `hub` profile (reports 241, 243).
    public HubHeartbeatService(
            @Lazy LicenseIssuingService licenseIssuingService,
            HubActivationRepository hubActivationRepository,
            RestaurantRepository restaurantRepository,
            @Value("${hub.latest-version:}") String latestVersion) {
        this.licenseIssuingService = licenseIssuingService;
        this.hubActivationRepository = hubActivationRepository;
        this.restaurantRepository = restaurantRepository;
        this.latestVersion = latestVersion;
    }

    public HubHeartbeatResponse heartbeat(HubHeartbeatRequest request, String callerIp)
            throws InvalidLicenseException {
        LicenseKey licenseKey = new LicenseKeyParser()
                .parseAndVerify(request.getLicenseKey(), licenseIssuingService.publicKey());
        UUID restaurantId = licenseKey.restaurantId();

        HubActivation activation = hubActivationRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new InvalidLicenseException(
                        "Esta licencia no está activada. Contacta a soporte de Ember."));
        if (!activation.getHardwareFingerprint().equals(request.getHardwareFingerprint())) {
            throw new InvalidLicenseException(
                    "Esta licencia está activada en otra PC. Contacta a soporte de Ember.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new InvalidLicenseException(
                        "Esta licencia no corresponde a ningún restaurante."));

        recordHeartbeatQuietly(restaurantId, callerIp);

        String status = restaurant.getStatus() == RestaurantStatus.ACTIVE ? "OK" : "SUSPENDED";

        return HubHeartbeatResponse.builder()
                .status(status)
                .serverTime(Instant.now())
                .latestVersion(latestVersion == null || latestVersion.isBlank() ? null : latestVersion)
                .build();
    }

    /** Liveness telemetry: a write failure here must not turn a valid heartbeat into an error. */
    private void recordHeartbeatQuietly(UUID restaurantId, String callerIp) {
        try {
            hubActivationRepository.recordHeartbeat(restaurantId, Instant.now(), callerIp);
        } catch (DataAccessException e) {
            log.warn("Could not record heartbeat for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }
}
