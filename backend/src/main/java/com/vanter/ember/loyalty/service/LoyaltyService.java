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
}
