package com.vanter.ember.loyalty.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.model.SettingsPayload.AccrualMode;
import com.vanter.ember.settings.model.SettingsPayload.LoyaltySettings;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LoyaltyServiceTest {

    private final LoyaltyService loyaltyService = new LoyaltyService();

    private LoyaltySettings settings() {
        LoyaltySettings settings = new SettingsPayload().getLoyalty();
        settings.setPlataThreshold(100);
        settings.setOroThreshold(500);
        settings.setPlatinoThreshold(1500);
        return settings;
    }

    @Test
    void computeTier_zeroPoints_isBronce() {
        assertThat(loyaltyService.computeTier(0, settings())).isEqualTo(LoyaltyTier.BRONCE);
    }

    @Test
    void computeTier_justBelowPlataThreshold_staysBronce() {
        assertThat(loyaltyService.computeTier(99, settings())).isEqualTo(LoyaltyTier.BRONCE);
    }

    @Test
    void computeTier_atPlataThreshold_isPlata() {
        assertThat(loyaltyService.computeTier(100, settings())).isEqualTo(LoyaltyTier.PLATA);
    }

    @Test
    void computeTier_justBelowOroThreshold_staysPlata() {
        assertThat(loyaltyService.computeTier(499, settings())).isEqualTo(LoyaltyTier.PLATA);
    }

    @Test
    void computeTier_atOroThreshold_isOro() {
        assertThat(loyaltyService.computeTier(500, settings())).isEqualTo(LoyaltyTier.ORO);
    }

    @Test
    void computeTier_justBelowPlatinoThreshold_staysOro() {
        assertThat(loyaltyService.computeTier(1499, settings())).isEqualTo(LoyaltyTier.ORO);
    }

    @Test
    void computeTier_atPlatinoThreshold_isPlatino() {
        assertThat(loyaltyService.computeTier(1500, settings())).isEqualTo(LoyaltyTier.PLATINO);
    }

    @Test
    void computeTier_wellAbovePlatinoThreshold_staysPlatino() {
        assertThat(loyaltyService.computeTier(50_000, settings())).isEqualTo(LoyaltyTier.PLATINO);
    }

    @Test
    void computeAccrualPoints_byVisit_returnsFlatPointsRegardlessOfAmount() {
        LoyaltySettings settings = settings();
        settings.setAccrualMode(AccrualMode.BY_VISIT);
        settings.setPointsPerVisit(10);

        assertThat(loyaltyService.computeAccrualPoints(new BigDecimal("250.00"), settings))
                .isEqualTo(10);
        assertThat(loyaltyService.computeAccrualPoints(new BigDecimal("0.01"), settings))
                .isEqualTo(10);
    }

    @Test
    void computeAccrualPoints_byAmountSpent_multipliesAndRoundsToWholePoint() {
        LoyaltySettings settings = settings();
        settings.setAccrualMode(AccrualMode.BY_AMOUNT_SPENT);
        settings.setPointsPerCurrencyUnit(1.0);

        assertThat(loyaltyService.computeAccrualPoints(new BigDecimal("25.00"), settings))
                .isEqualTo(25);
    }

    @Test
    void computeAccrualPoints_byAmountSpent_roundsHalfUp() {
        LoyaltySettings settings = settings();
        settings.setAccrualMode(AccrualMode.BY_AMOUNT_SPENT);
        settings.setPointsPerCurrencyUnit(0.5);

        // 25.30 * 0.5 = 12.65 -> HALF_UP -> 13
        assertThat(loyaltyService.computeAccrualPoints(new BigDecimal("25.30"), settings))
                .isEqualTo(13);
    }

    @Test
    void computeAccrualPoints_byAmountSpent_exactHalfRoundsUp() {
        LoyaltySettings settings = settings();
        settings.setAccrualMode(AccrualMode.BY_AMOUNT_SPENT);
        settings.setPointsPerCurrencyUnit(1.0);

        // 10.5 -> HALF_UP -> 11
        assertThat(loyaltyService.computeAccrualPoints(new BigDecimal("10.5"), settings))
                .isEqualTo(11);
    }

    @Test
    void computeAccrualPoints_byAmountSpent_zeroAmountYieldsZeroPoints() {
        LoyaltySettings settings = settings();
        settings.setAccrualMode(AccrualMode.BY_AMOUNT_SPENT);
        settings.setPointsPerCurrencyUnit(2.0);

        assertThat(loyaltyService.computeAccrualPoints(BigDecimal.ZERO, settings)).isEqualTo(0);
    }

    @Test
    void tierFloor_bronceIsAlwaysZero() {
        assertThat(loyaltyService.tierFloor(LoyaltyTier.BRONCE, settings())).isZero();
    }

    @Test
    void tierFloor_matchesEachTiersOwnThreshold() {
        LoyaltySettings settings = settings();
        assertThat(loyaltyService.tierFloor(LoyaltyTier.PLATA, settings)).isEqualTo(100);
        assertThat(loyaltyService.tierFloor(LoyaltyTier.ORO, settings)).isEqualTo(500);
        assertThat(loyaltyService.tierFloor(LoyaltyTier.PLATINO, settings)).isEqualTo(1500);
    }
}
