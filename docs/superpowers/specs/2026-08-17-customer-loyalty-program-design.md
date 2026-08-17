# Customer Loyalty Program — Design Spec

**Date:** 2026-08-17
**Backlog prefix:** `EMB-CLP`
**Status:** Approved, pending implementation plan

## 1. Purpose

Add a points-based loyalty program: customers accrue points per settled bill (by visit or by amount spent, tenant's choice), points map to one of four fixed tiers (Bronce/Plata/Oro/Platino), and admins define a tier-gated reward catalog customers can see but not yet redeem.

This is sub-project **A** of a two-part module. Sub-project **B** — the customer-facing Profile page (bento-grid layout: hero, loyalty widget, spend summary, visit history, live orders, floating nav) — is a separate spec/plan cycle that consumes this engine once it exists. This spec covers only the engine: config, data model, accrual, and the minimal API surface needed to verify it end-to-end.

## 2. Scope decisions (confirmed with user)

1. **Sequencing.** Loyalty engine (A) ships and is verifiable before the Profile page (B) is designed — B consumes a real, finished API, not a guessed one.
2. **Tenant linkage.** `User.restaurantId` is null for customers by design (a customer isn't tenant-bound until they join a table). A `LoyaltyAccount` is created lazily the first time a customer joins a table at a given restaurant — that join is what links the customer to that tenant's loyalty program, not registration or login.
3. **Returning-customer access.** Out of scope for this spec (belongs to sub-project B): a "Mis Restaurantes" list on the already-reachable, tenant-less `/customer/home`, populated from whichever tenants have a `LoyaltyAccount` linked to this `User`. No changes to the login/register/auth flow are needed for that — deliberately avoids touching `/t/:slug`'s currently dead-end auth buttons.
4. **Accrual mode.** Single mode per tenant, admin-selected: `BY_VISIT` (flat points) or `BY_AMOUNT_SPENT` (points per currency unit) — not both simultaneously. Matches the single-select-enum pattern already used for `SplitMethod`.
5. **Tiers.** Fixed four-tier enum (`BRONCE, PLATA, ORO, PLATINO`) — only the point *thresholds* are admin-configurable, not the tier set itself. Bronce is always the floor at 0 points.
6. **Tier computation: computed on read, never stored.** `LoyaltyAccount` stores only `totalPoints`; tier is derived at response time from `totalPoints` against the tenant's current thresholds. Avoids a whole class of "why is this account still showing the old tier" bugs if an admin changes thresholds later — no migration/recompute job ever needed. (Trade-off accepted: no fast DB-level "list all Platino customers" query without a future computed column — not needed yet.)
7. **Rewards: catalog display only, no redemption in v1.** `LoyaltyReward` is admin-defined and tier-gated (`requiredTier`); customers see what they've unlocked and what's next. No points-cost field, no redemption flow, no billing/discount integration — that's explicitly a future phase, not folded into this one.
8. **Multi-participant accrual.** A settled bill can have multiple participants (collaborative cart). Points accrue **per participant**, based on *their own* `BillSplit.amount` — not once per table. A `BY_VISIT` bill with 3 paying participants credits 3 separate visit-bonuses; `BY_AMOUNT_SPENT` naturally already scales per participant via their own split amount.

## 3. Backend design

### 3.1 `RestaurantSettings` extension

New nested section on the existing `SettingsPayload`, alongside `BillingSettings`/`PaymentGatewaySettings` — no new settings endpoint, it rides the existing settings read/write mechanism:

```java
public static class LoyaltySettings {
    private boolean enabled;
    private AccrualMode accrualMode; // BY_VISIT | BY_AMOUNT_SPENT
    private int pointsPerVisit;              // used when BY_VISIT
    private BigDecimal pointsPerCurrencyUnit; // used when BY_AMOUNT_SPENT
    private int plataThreshold;
    private int oroThreshold;
    private int platinoThreshold;
}
```

### 3.2 Entities (new `loyalty` module, Postgres/JPA, `@TenantId`-scoped like `billing`/`cashregister`)

**`LoyaltyAccount`** — one row per (tenant, customer), created lazily on first table-join.
| column | notes |
|---|---|
| `id` | |
| `tenant_id` | `@TenantId` |
| `user_id` | plain `varchar` column holding `User#id` — same no-`@ManyToOne`-to-`User` convention as `Payment.processedBy` (avoids the `User.restaurantId` LAZY-association hazard) |
| `total_points` | |
| `created_at` | |

Unique index on `(tenant_id, user_id)`.

**`LoyaltyTransaction`** — append-only ledger, same immutable-fact pattern as `Refund`/`CashMovement`: never mutated, each row a fact.
| column | notes |
|---|---|
| `id` | |
| `tenant_id` | |
| `loyalty_account_id` | real `@ManyToOne` (no LAZY hazard on `LoyaltyAccount`, unlike `User`) |
| `points` | signed — always positive today (no redemption yet), kept signed so a future redemption phase can post negative entries without a schema change |
| `reason` | e.g. `BILL_SETTLED` |
| `bill_id` | links to **`Bill.id`**, not a specific `Payment.id` — accrual fires once per settled bill; a bill can have multiple payments, and crediting per-payment would double-count |
| `created_at` | |

**`LoyaltyReward`** — admin-defined catalog entry.
| column | notes |
|---|---|
| `id` | |
| `tenant_id` | |
| `name` | |
| `description` | |
| `required_tier` | the `LoyaltyTier` enum — gates by tier, not a points cost (nothing consumes points in v1) |
| `active` | |
| `created_at` | |

### 3.3 Accrual flow

New `LoyaltyAccrualListener` (in `loyalty.listener`) listens for the **existing** `PaymentCompleted` event — the same event that already closes the session once every split is paid. No new event type.

1. Guard: if `RestaurantSettings.loyalty.enabled` is false for the tenant, no-op immediately.
2. Fetch the settled `Bill`'s splits and the `Session` (already have `sessionId`/`billId` from the event).
3. For each `BillSplit`, resolve `participantName` → the matching `Participant.userId` in the session — every participant is a real authenticated `User` by the time they can pay (guests register/log in to join a table), so this always resolves.
4. For each resolved participant: compute points (`BY_VISIT` → flat `pointsPerVisit`; `BY_AMOUNT_SPENT` → `split.amount × pointsPerCurrencyUnit`, rounded to a whole point, `HALF_UP` — matching how this codebase already rounds money elsewhere), find-or-create their `LoyaltyAccount` for this tenant (should already exist from their table-join per decision #2 — find-or-create is a safety net, not the primary creation path), write one `LoyaltyTransaction` (`reason = BILL_SETTLED`), bump `LoyaltyAccount.totalPoints`.
5. `TenantContextHolder` stays bound throughout — event listeners run synchronously in the same request thread in this codebase (confirmed by the existing `PaymentCompletedListener`, which already performs a tenant-scoped Mongo operation the same way), so no special re-binding is needed.

### 3.4 Account creation on table-join

A separate, explicit hook (not the accrual listener) into the existing session join flow: the first time a given `(tenantId, userId)` pair is seen at a table-join, create their `LoyaltyAccount` with `totalPoints = 0`. Decision #2's ruling — the join is the link, not registration or first payment.

### 3.5 API surface

| Method & path | Role | Purpose |
|---|---|---|
| `POST /loyalty/rewards` | ADMIN | Create a reward (`name`, `description`, `requiredTier`) |
| `GET /loyalty/rewards` | ADMIN | List all rewards (incl. inactive) for management |
| `PATCH /loyalty/rewards/{id}` | ADMIN | Edit fields / toggle `active` |
| `GET /loyalty/accounts/me` | CUSTOMER | Caller's own account for the *current* tenant context: `totalPoints`, computed `tier`, `nextTier` (null if already Platino), `pointsToNextTier` (null if maxed), active reward catalog each annotated `unlocked: boolean` |

No `/api` prefix — this codebase has no real `/api`-prefixed module (the few `/api/...` rows in `SecurityAuditTest` are an acknowledged stale leftover, not a convention). Base path: `/loyalty`.

Error handling: no new exception types — reuses the existing `IllegalStateException`→409 / `IllegalArgumentException`→409 / `ResourceNotFoundException`→404 convention.

## 4. Frontend design (sub-project A's slice only)

- **Admin:** a new "Fidelización"/Loyalty tab in `pages/admin/Settings.tsx` (same tabbed pattern as MENU/BILLING/HARDWARE/HORARIO) for the config fields in §3.1, plus a reward-catalog management screen (create/edit/toggle-active) consuming §3.5's admin endpoints.
- **Customer:** no profile page yet (sub-project B). The only customer-facing surface in A is a minimal "you earned N points" confirmation, shown right after payment (e.g. on the existing `Bill.tsx` "Mi Cuenta" screen), consuming `GET /loyalty/accounts/me` — enough to make the engine's output visible and testable end-to-end without waiting on B's full page.

## 5. Testing strategy

- `LoyaltyServiceTest`: tier-computation boundary values at each threshold (Mockito unit tests, no DB).
- `LoyaltyServiceTest`: accrual math for both modes, rounding behavior.
- `LoyaltyAccrualListenerTest`: multi-participant `PaymentCompleted` event → correct per-participant crediting, verifies no double-counting across splits.
- `LoyaltyAccountRepositoryTenantIsolationTest`: matches the pattern every other new tenant-scoped entity in this codebase gets (`AbstractTenantIsolationTest`).
- `SecurityAuditTest`: new rows for all 4 endpoints in §3.5.

## 6. Explicit non-goals

- No redemption flow, no billing/discount integration.
- No tier downgrade or point expiration.
- No manual admin point adjustments.
- No `/customer/home` "Mis Restaurantes" list, no `/t/:slug` auth wiring, no bento-grid Profile page — all sub-project B.
- No admin-facing loyalty analytics/reporting (e.g. "how many Platino members") — tier is computed on read, not a queryable column, by design (decision #6); a future computed column is a targeted follow-up if this is ever needed, not part of this scope.
