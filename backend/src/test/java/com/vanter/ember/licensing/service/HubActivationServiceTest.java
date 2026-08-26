package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HubActivationServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock UserRepository userRepository;
    @Mock HubActivationRepository hubActivationRepository;

    private KeyPair keyPair;
    private LicenseIssuingService licenseIssuingService;
    private HubActivationService hubActivationService;
    private UUID restaurantId;
    private String signedLicenseKey;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        licenseIssuingService =
                new LicenseIssuingService(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        hubActivationService = new HubActivationService(
                restaurantRepository, userRepository, hubActivationRepository, licenseIssuingService);
        restaurantId = UUID.randomUUID();
        signedLicenseKey = LicenseKeyParser.sign(new LicenseKey(restaurantId, Instant.now()), keyPair.getPrivate());
    }

    private HubActivationRequest request(String fingerprint) {
        HubActivationRequest request = new HubActivationRequest();
        request.setLicenseKey(signedLicenseKey);
        request.setHardwareFingerprint(fingerprint);
        return request;
    }

    private Restaurant restaurant() {
        return Restaurant.builder().id(restaurantId).name("Tenant Grill").slug("tenant-grill").build();
    }

    private User admin() {
        return User.builder()
                .id("u-1")
                .name("Owner Admin")
                .email("owner@tenant-grill.local")
                .passwordHash("bcrypt-hash")
                .role(Role.ADMIN)
                .build();
    }

    @Test
    void activate_firstTime_createsActivationAndReturnsAdminData() throws Exception {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of(admin()));

        HubActivationResponse result = hubActivationService.activate(request("fp-1"));

        assertThat(result.getName()).isEqualTo("Tenant Grill");
        assertThat(result.getSlug()).isEqualTo("tenant-grill");
        assertThat(result.getAdminEmail()).isEqualTo("owner@tenant-grill.local");
        assertThat(result.getAdminPasswordHash()).isEqualTo("bcrypt-hash");

        org.mockito.ArgumentCaptor<HubActivation> captor = org.mockito.ArgumentCaptor.forClass(HubActivation.class);
        org.mockito.Mockito.verify(hubActivationRepository).save(captor.capture());
        assertThat(captor.getValue().getRestaurantId()).isEqualTo(restaurantId);
        assertThat(captor.getValue().getHardwareFingerprint()).isEqualTo("fp-1");
    }

    @Test
    void activate_retrySameFingerprint_doesNotCreateSecondActivation() throws Exception {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint("fp-1")
                        .activatedAt(Instant.now()).build()));
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of(admin()));

        HubActivationResponse result = hubActivationService.activate(request("fp-1"));

        assertThat(result.getAdminEmail()).isEqualTo("owner@tenant-grill.local");
        org.mockito.Mockito.verify(hubActivationRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activate_differentFingerprint_throwsIllegalState() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint("fp-1")
                        .activatedAt(Instant.now()).build()));

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-2")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("otra PC");
    }

    @Test
    void activate_restaurantNotFound_throwsResourceNotFound() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activate_noAdminUser_throwsResourceNotFound() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant()));
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        when(userRepository.findByRestaurantId_IdAndRole(restaurantId, Role.ADMIN)).thenReturn(List.of());

        assertThatThrownBy(() -> hubActivationService.activate(request("fp-1")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activate_invalidSignature_throwsInvalidLicenseException() throws Exception {
        KeyPair otherKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        HubActivationRequest badRequest = new HubActivationRequest();
        badRequest.setLicenseKey(
                LicenseKeyParser.sign(new LicenseKey(restaurantId, Instant.now()), otherKeyPair.getPrivate()));
        badRequest.setHardwareFingerprint("fp-1");

        assertThatThrownBy(() -> hubActivationService.activate(badRequest))
                .isInstanceOf(com.vanter.ember.hub.license.InvalidLicenseException.class);
    }
}
