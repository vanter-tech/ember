package com.vanter.ember.licensing.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubActivationRequest;
import com.vanter.ember.licensing.model.dto.HubActivationResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers a Hub's one-time activation call: verifies the license signature, resolves the
 * restaurant + its first ADMIN user, and enforces the server-side hardware lock (a defense the
 * client-side {@code hub-state.json} lock can't provide on its own — deleting that file resets
 * nothing server-side).
 */
@Service
public class HubActivationService {

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final HubActivationRepository hubActivationRepository;
    private final LicenseIssuingService licenseIssuingService;

    /**
     * Manual constructor (not Lombok's {@code @RequiredArgsConstructor}): {@code @Lazy} on a
     * field is never copied onto the generated constructor parameter, so it would silently NOT
     * defer {@link LicenseIssuingService}'s creation — and that bean throws at construction time
     * whenever {@code hub.license.private-key} isn't set (true for every non-{@code hub}-profile
     * boot, tests included). Same fix as {@link
     * com.vanter.ember.platform.service.PlatformRestaurantService}'s constructor (report 241).
     */
    public HubActivationService(
            RestaurantRepository restaurantRepository,
            UserRepository userRepository,
            HubActivationRepository hubActivationRepository,
            @Lazy LicenseIssuingService licenseIssuingService) {
        this.restaurantRepository = restaurantRepository;
        this.userRepository = userRepository;
        this.hubActivationRepository = hubActivationRepository;
        this.licenseIssuingService = licenseIssuingService;
    }

    @Transactional
    public HubActivationResponse activate(HubActivationRequest request) throws InvalidLicenseException {
        LicenseKey licenseKey = new LicenseKeyParser()
                .parseAndVerify(request.getLicenseKey(), licenseIssuingService.publicKey());
        UUID restaurantId = licenseKey.restaurantId();

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        Optional<HubActivation> existing = hubActivationRepository.findByRestaurantId(restaurantId);
        if (existing.isPresent()) {
            if (!existing.get().getHardwareFingerprint().equals(request.getHardwareFingerprint())) {
                throw new IllegalStateException("Esta licencia ya fue activada en otra PC.");
            }
        } else {
            hubActivationRepository.save(HubActivation.builder()
                    .restaurantId(restaurantId)
                    .hardwareFingerprint(request.getHardwareFingerprint())
                    .activatedAt(Instant.now())
                    .build());
        }

        User admin = userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant " + restaurantId + " has no ADMIN user"));

        return HubActivationResponse.builder()
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .adminName(admin.getName())
                .adminEmail(admin.getEmail())
                .adminPasswordHash(admin.getPasswordHash())
                .build();
    }
}
