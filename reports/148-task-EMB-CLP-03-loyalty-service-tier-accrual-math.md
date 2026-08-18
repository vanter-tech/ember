# Report 148 — EMB-CLP-03: LoyaltyService (tier computation + accrual math)

**Predecessor Task:** EMB-CLP-02

## Objective
Add the pure domain logic used by the loyalty engine: computed-on-read tier resolution against
tenant-configured thresholds, and per-mode accrual point math (`BY_VISIT` / `BY_AMOUNT_SPENT`),
boundary-tested.

## Modified Files
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyService.java` (new)
- `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyServiceTest.java` (new)

## What Changed?
- `LoyaltyService.computeTier(int totalPoints, SettingsPayload.LoyaltySettings settings)`: compares
  `totalPoints` against `platinoThreshold` → `oroThreshold` → `plataThreshold` in descending order,
  falling back to `BRONCE`. Stateless, no persistence.
- `LoyaltyService.computeAccrualPoints(BigDecimal splitAmount, SettingsPayload.LoyaltySettings settings)`:
  returns the flat `pointsPerVisit` under `BY_VISIT`; under `BY_AMOUNT_SPENT`, multiplies
  `splitAmount` by `pointsPerCurrencyUnit` and rounds to a whole point with `RoundingMode.HALF_UP`
  (`BigDecimal.valueOf(double)` used for the currency-unit rate to avoid float-literal precision
  issues).
- `LoyaltyServiceTest`: plain JUnit5 + AssertJ (no Mockito — the service is stateless), covering
  tier boundaries at each threshold (just-below and exactly-at each of Plata/Oro/Platino, zero, and
  well above Platino) and accrual math for both modes including a HALF_UP rounding case and an
  exact-`.5` rounding case.

## Why It Changed?
Per `docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` decision #6, tier is
deliberately never persisted on `LoyaltyAccount` — it is recomputed from `totalPoints` on every
read against the tenant's *current* thresholds, so an admin changing a threshold never leaves a
stale tier on existing accounts. Isolating this math into its own stateless service (rather than
inlining it into the future `LoyaltyAccrualListener`/`GET /loyalty/accounts/me` handler) lets both
consumers share one boundary-tested implementation. Scope intentionally excludes the accrual
listener (`PaymentCompleted` wiring, EMB-CLP-05), the account-creation hook (EMB-CLP-04), and the
`GET /loyalty/accounts/me` `nextTier`/`pointsToNextTier` response shaping (EMB-CLP-08) — this task
is the pure math only.

## Verification
- `./mvnw test -Dtest=LoyaltyServiceTest` — 12/12 green.
- `./mvnw test` — 706/706 green (694 prior + 12 new).
