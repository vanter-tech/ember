package com.vanter.ember.restaurant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RestaurantPlan plan = RestaurantPlan.FREE;

    @Column(name = "plan_started_at")
    private Instant planStartedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_period")
    private BillingPeriod billingPeriod;

    @Column(name = "plan_period_end")
    private Instant planPeriodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "deployment_mode", nullable = false)
    @Builder.Default
    private DeploymentMode deploymentMode = DeploymentMode.CLOUD;

    @Column(nullable = false)
    @Builder.Default
    private String timezone = "UTC";

    @Column(nullable = false)
    @Builder.Default
    private String currency = "USD";

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    /** Plan start as shown to the tenant: the operator-recorded date, else the account creation day. */
    public Instant effectivePlanStart() {
        if (planStartedAt != null) {
            return planStartedAt;
        }
        return createdAt == null ? null : createdAt.truncatedTo(java.time.temporal.ChronoUnit.DAYS);
    }

    public BillingPeriod effectiveBillingPeriod() {
        return billingPeriod != null ? billingPeriod : BillingPeriod.MONTHLY;
    }

    /** Next payment date: the recorded period end, else one month after the effective start. */
    public Instant effectivePlanEnd() {
        if (planPeriodEnd != null) {
            return planPeriodEnd;
        }
        Instant start = effectivePlanStart();
        return start == null ? null : effectiveBillingPeriod().endFrom(start);
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
