package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubHeartbeatServiceTest {

    private LicenseIssuingService licenseIssuingService;
    private HubActivationRepository hubActivationRepository;
    private RestaurantRepository restaurantRepository;
    private HubHeartbeatService service;

    private UUID restaurantId;
    private String signedLicense;
    private static final String FP = "fp-1";

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();

        // Real RSA key pair; sign a license with the private half, hand the service a
        // LicenseIssuingService stub whose publicKey() returns the matching public half.
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        signedLicense = com.vanter.ember.hub.license.LicenseKeyParser.sign(
                new LicenseKey(restaurantId, Instant.now()), pair.getPrivate());

        licenseIssuingService = mock(LicenseIssuingService.class);
        when(licenseIssuingService.publicKey()).thenReturn(pair.getPublic());
        hubActivationRepository = mock(HubActivationRepository.class);
        restaurantRepository = mock(RestaurantRepository.class);

        service = new HubHeartbeatService(
                licenseIssuingService, hubActivationRepository, restaurantRepository, "1.4.0");

        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint(FP)
                        .activatedAt(Instant.now()).build()));
    }

    private HubHeartbeatRequest request(String fingerprint) {
        HubHeartbeatRequest r = new HubHeartbeatRequest();
        r.setLicenseKey(signedLicense);
        r.setHardwareFingerprint(fingerprint);
        return r;
    }

    private static final String IP = "203.0.113.7";

    @Test
    void heartbeat_activeRestaurant_returnsOk() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));

        HubHeartbeatResponse response = service.heartbeat(request(FP), IP);

        assertThat(response.getStatus()).isEqualTo("OK");
        assertThat(response.getLatestVersion()).isEqualTo("1.4.0");
        assertThat(response.getServerTime()).isNotNull();
    }

    @Test
    void heartbeat_suspendedRestaurant_returnsSuspended() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.SUSPENDED)));

        assertThat(service.heartbeat(request(FP), IP).getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void heartbeat_fingerprintMismatch_throwsInvalidLicense() {
        assertThatThrownBy(() -> service.heartbeat(request("a-different-fp"), IP))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_noActivationRow_throwsInvalidLicense() {
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.heartbeat(request(FP), IP))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_unknownRestaurant_throwsInvalidLicense() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.heartbeat(request(FP), IP))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_garbageLicense_throwsInvalidLicense() {
        HubHeartbeatRequest r = new HubHeartbeatRequest();
        r.setLicenseKey("not-a-real-license");
        r.setHardwareFingerprint(FP);
        assertThatThrownBy(() -> service.heartbeat(r, IP)).isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_activeRestaurant_recordsHeartbeat() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));

        service.heartbeat(request(FP), IP);

        org.mockito.Mockito.verify(hubActivationRepository)
                .recordHeartbeat(org.mockito.ArgumentMatchers.eq(restaurantId),
                        org.mockito.ArgumentMatchers.any(java.time.Instant.class),
                        org.mockito.ArgumentMatchers.eq(IP));
    }

    @Test
    void heartbeat_fingerprintMismatch_doesNotRecord() {
        assertThatThrownBy(() -> service.heartbeat(request("a-different-fp"), IP))
                .isInstanceOf(InvalidLicenseException.class);
        org.mockito.Mockito.verify(hubActivationRepository, org.mockito.Mockito.never())
                .recordHeartbeat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void heartbeat_recordFailureIsSwallowed() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"))
                .when(hubActivationRepository).recordHeartbeat(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        HubHeartbeatResponse response = service.heartbeat(request(FP), IP);

        assertThat(response.getStatus()).isEqualTo("OK");
    }

    private Restaurant restaurantWithStatus(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setStatus(status);
        return restaurant;
    }
}
