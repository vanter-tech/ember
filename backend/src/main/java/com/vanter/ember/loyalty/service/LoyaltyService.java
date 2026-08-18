package com.vanter.ember.loyalty.service;

import com.vanter.ember.loyalty.model.LoyaltyTier;
import com.vanter.ember.settings.model.SettingsPayload;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Pure tier-computation and accrual-math functions, stateless and settings-driven — no
 * persistence here. {@link #computeTier} is deliberately never cached against {@code
 * LoyaltyAccount} (see decision #6 in the design spec): callers always pass the account's current
 * {@code totalPoints} and the tenant's current thresholds.
 */
@Service
public class LoyaltyService {

    public LoyaltyTier computeTier(int totalPoints, SettingsPayload.LoyaltySettings settings) {
        if (totalPoints >= settings.getPlatinoThreshold()) {
            return LoyaltyTier.PLATINO;
        }
        if (totalPoints >= settings.getOroThreshold()) {
            return LoyaltyTier.ORO;
        }
        if (totalPoints >= settings.getPlataThreshold()) {
            return LoyaltyTier.PLATA;
        }
        return LoyaltyTier.BRONCE;
    }

    /**
     * {@code BY_VISIT} returns the flat {@code pointsPerVisit}; {@code BY_AMOUNT_SPENT} multiplies
     * the participant's own {@code BillSplit.amount} by {@code pointsPerCurrencyUnit}, rounded to
     * a whole point (HALF_UP, matching this codebase's other money rounding).
     */
    public int computeAccrualPoints(BigDecimal splitAmount, SettingsPayload.LoyaltySettings settings) {
        if (settings.getAccrualMode() == SettingsPayload.AccrualMode.BY_VISIT) {
            return settings.getPointsPerVisit();
        }
        return splitAmount
                .multiply(BigDecimal.valueOf(settings.getPointsPerCurrencyUnit()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    /** Next rung on the fixed tier ladder, or {@code null} if already {@code PLATINO}. */
    public LoyaltyTier nextTier(LoyaltyTier current) {
        return switch (current) {
            case BRONCE -> LoyaltyTier.PLATA;
            case PLATA -> LoyaltyTier.ORO;
            case ORO -> LoyaltyTier.PLATINO;
            case PLATINO -> null;
        };
    }

    /** Points still needed to reach {@code next}, or {@code null} if {@code next} is null (maxed). */
    public Integer pointsToNextTier(int totalPoints, LoyaltyTier next, SettingsPayload.LoyaltySettings settings) {
        if (next == null) {
            return null;
        }
        int threshold = switch (next) {
            case PLATA -> settings.getPlataThreshold();
            case ORO -> settings.getOroThreshold();
            case PLATINO -> settings.getPlatinoThreshold();
            case BRONCE -> 0;
        };
        return Math.max(threshold - totalPoints, 0);
    }
}
