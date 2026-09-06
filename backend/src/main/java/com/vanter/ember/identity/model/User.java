package com.vanter.ember.identity.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vanter.ember.restaurant.model.Restaurant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /** Null for CUSTOMER — customers aren't bound to one restaurant, see AuthService#resolveLoginRestaurant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = true)
    private Restaurant restaurantId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @JsonIgnore
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "job_title")
    private String jobTitle;

    private String shift;

    @Column(name = "contract_type")
    private String contractType;

    private String location;

    @Column(name = "efficiency_percentage")
    private BigDecimal efficiencyPercentage;

    @Column(name = "pending_hours", nullable = false)
    @Builder.Default
    private BigDecimal pendingHours = BigDecimal.ZERO;

    /** BCrypt hash of the user's 4-6 digit quick-login PIN. Null when no PIN is set. */
    @JsonIgnore
    @Column(name = "pin_hash", length = 60)
    private String pinHash;

    @Column(name = "pin_updated_at")
    private Instant pinUpdatedAt;

    /** Chosen customer-home banner preset. Null → client uses the default preset. */
    @Enumerated(EnumType.STRING)
    @Column(name = "banner_key", length = 20)
    private BannerKey bannerKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
