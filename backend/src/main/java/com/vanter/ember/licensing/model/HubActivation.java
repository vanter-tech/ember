package com.vanter.ember.licensing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Server-side record of a Hub activation — lets {@code HubActivationService} tell a legitimate
 * retry from the same PC (e.g. after a wiped local DB) apart from a license copied onto a
 * different machine, on top of the client-side hardware lock {@code LicenseService} already does
 * locally (which a deleted {@code hub-state.json} can bypass).
 */
@Entity
@Table(name = "hub_activations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HubActivation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, unique = true)
    private UUID restaurantId;

    @Column(name = "hardware_fingerprint", nullable = false)
    private String hardwareFingerprint;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;
}
