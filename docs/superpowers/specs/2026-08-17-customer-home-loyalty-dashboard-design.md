# Customer Home Loyalty Dashboard — Design Spec

**Date:** 2026-08-17
**Backlog prefix:** `EMB-CLH`
**Status:** Approved, pending implementation plan

## 1. Purpose

`/customer/home` currently shows nothing but the customer's avatar/name and a
big "Entrar a una mesa" button — even for a returning customer who has
already joined tables and earned loyalty points at a restaurant. Replace that
with a loyalty dashboard (points, tier, and recent-visit history with what
they paid) for customers who have already joined a table somewhere, while
leaving the existing avatar+join-table card exactly as-is for customers who
never have.

This is a narrower, home-page-scoped slice of what
`docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md`
(EMB-CLP) called "sub-project B" — that spec's §2 decision 3 sketched a
fuller future Profile page (bento-grid: hero, spend summary, live orders,
floating nav, multi-restaurant "Mis Restaurantes" list). This spec
deliberately does **not** build that. It builds only the single-restaurant
loyalty view on the existing Home route, per the scope decisions below. A
full standalone Profile page, live-order integration, or multi-restaurant
aggregation remain future work if ever needed.

## 2. Scope decisions (confirmed with user)

1. **History detail: visit summary, not itemized orders.** Each past visit
   shows date, amount the customer personally paid, and points earned — not
   the dishes ordered. Sourced from the existing `LoyaltyTransaction` ledger
   (one row per settled bill per participant), not a new join into
   session/menu-item data.
2. **"Pay" = payment history only, no active-bill shortcut.** The visit list
   shows what was paid on each past visit. There is deliberately no "pay my
   current bill" shortcut on Home — that flow already exists on `Bill.tsx`
   and is out of scope here.
3. **Tenant scope: single "current restaurant," not an aggregate.** A
   customer only carries a tenant-scoped JWT once they've joined a table
   there (`rid` claim, minted by `JoinTableModal`/QR join), and that scoping
   persists across reloads until logout. Home shows loyalty data for
   whichever restaurant that token is currently scoped to — never an
   aggregate across every restaurant the customer has ever visited. This
   matches how the rest of the customer app already resolves tenant context;
   building a multi-restaurant aggregate would require a different tenant
   resolution mechanism entirely and is explicitly out of scope.
4. **Join-table access is preserved.** Even when the loyalty dashboard is
   showing, a compact "Entrar a una mesa" action stays reachable on Home —
   customers still need to join tables on repeat visits.

## 3. Backend design

### 3.1 Problem: no persisted "amount paid" per visit

`LoyaltyTransaction` (added in EMB-CLP-01) stores `points`, `reason`
(always `BILL_SETTLED`), and `billId`, but not the amount the customer
personally paid. That value exists only transiently as
`BillSplit.getAmount()` inside `LoyaltyAccrualListener.accrue` at accrual
time — `billId` alone can't reconstruct it later, since a bill can be split
across multiple named participants and nothing links a ledger row back to
one specific `BillSplit` row. Fix: capture and persist it at write time.

### 3.2 Schema change

New migration `V10__loyalty_transaction_amount.sql`:

```sql
-- Persists the amount the customer personally paid for this visit (from
-- BillSplit.amount at accrual time) — needed to show payment history on
-- the customer Home loyalty dashboard. Nullable: pre-existing rows from
-- before this migration have no recoverable amount.
ALTER TABLE loyalty_transactions ADD COLUMN IF NOT EXISTS amount numeric(10,2);
```

`LoyaltyTransaction` entity gains:

```java
@Column(precision = 10, scale = 2)
private BigDecimal amount;
```

No backfill runner — unlike the Mongo/`KitchenOrder.active` precedents,
there's no reliable source to backfill this from after the fact, and it's
an additive nullable column, not a `NOT NULL` one. Historical rows just
render as "—" in the amount column client-side.

### 3.3 Write path

`LoyaltyAccountService.credit` gains an `amount` parameter, stored on the
new `LoyaltyTransaction` row alongside the existing fields:

```java
@Transactional
public void credit(LoyaltyAccount account, int points, String reason, Long billId, BigDecimal amount)
```

`LoyaltyAccrualListener.accrue` passes `split.getAmount()` through to the
new parameter — it already has the `BillSplit` in scope, no new lookups
needed.

### 3.4 New read endpoint

`GET /loyalty/accounts/me/visits` (CUSTOMER) — the tenant-nullable-safe
sibling of the existing `GET /loyalty/accounts/me` (which stays completely
unchanged, still used as-is by `Bill.tsx`):

```java
@GetMapping("/me/visits")
@PreAuthorize("hasRole('CUSTOMER')")
public List<LoyaltyVisitResponse> myVisits(Authentication authentication) {
    UUID tenantId = TenantContextHolder.getTenantId(); // nullable getter, NOT requireTenantId()
    if (tenantId == null) {
        throw new ResourceNotFoundException("No tenant context — join a table first");
    }
    return loyaltyAccountService.getMyVisits(tenantId, resolveUserId(authentication));
}
```

New DTO:

```java
public record LoyaltyVisitResponse(LocalDateTime visitedAt, BigDecimal amountPaid, int pointsEarned) {}
```

New service method on `LoyaltyAccountService`:

```java
public List<LoyaltyVisitResponse> getMyVisits(UUID tenantId, String userId) {
    LoyaltyAccount account = loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("No loyalty account for this restaurant yet"));
    return loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId()).stream()
            .limit(20)
            .map(tx -> new LoyaltyVisitResponse(tx.getCreatedAt(), tx.getAmount(), tx.getPoints()))
            .toList();
}
```

Reuses the existing `findByLoyaltyAccountIdOrderByCreatedAtDesc` repository
method (no new query needed) — `.limit(20)` caps the response to the most
recent 20 visits; no pagination params in v1 (YAGNI, matches the "visit
summary" scope decision).

**Why this design makes the "customer never joined any table, ever" case
safe:** `tenantId == null` is checked with the nullable `getTenantId()`
getter and turned into a clean 404, instead of `/me`'s existing
`requireTenantId()` (which throws an unhandled `IllegalStateException` →
500 for that same case). Home calls `/visits` first and only calls the
existing `/me` after `/visits` succeeds — by which point tenant presence,
and therefore account existence (accounts are created lazily on table-join
per EMB-CLP-04), are both already confirmed. `/me`'s own contract and
error behavior are untouched.

An empty `[]` from `/visits` (tenant known, zero settled visits yet — e.g.
just joined, hasn't paid yet) is a normal 200, distinct from the 404
"never joined anywhere" case — Home renders these as two different states
(dashboard-with-empty-history vs. today's join-table card).

### 3.5 Tests

- `LoyaltyAccountServiceTest` (or extend existing loyalty service tests):
  `getMyVisits` returns the capped, ordered list; `credit` persists `amount`.
- `SecurityAuditTest`: new row for `GET, /loyalty/accounts/me/visits`.
- No new tenant-isolation test needed — `LoyaltyTransaction` already has one
  from EMB-CLP-01; this only adds a column to it.

## 4. Frontend design

### 4.1 Types

After the backend endpoint ships, run `pnpm run openapi` to regenerate
`backend-types.ts`, then add a generated-type alias in `api.ts`:

```ts
export type LoyaltyVisitResponse = components['schemas']['LoyaltyVisitResponse']
```

matching the established convention (report 154) — no hand-typed
interface.

### 4.2 `loyaltyAccountService`

New method alongside the existing `me`:

```ts
visits: async (): Promise<LoyaltyVisitResponse[]> => {
  const { data } = await api.get<LoyaltyVisitResponse[]>('/loyalty/accounts/me/visits')
  return data
},
```

### 4.3 `Home.tsx`

Two sequential, gated queries:

```ts
const { data: visits, isSuccess: hasTenant, isLoading: visitsLoading } = useQuery({
  queryKey: ['loyaltyVisits', 'me'],
  queryFn: loyaltyAccountService.visits,
  retry: false,
})

const { data: loyaltyAccount } = useQuery({
  queryKey: ['loyaltyAccount', 'me'],
  queryFn: loyaltyAccountService.me,
  enabled: hasTenant, // only fires once /visits has confirmed a tenant exists
  retry: false,
})
```

- While `visitsLoading`: keep today's card (avoids a flash of the wrong
  state).
- On `/visits` 404 (`isError`, not `hasTenant`): render today's existing
  avatar + "Entrar a una mesa" card completely unchanged.
- On success: render the loyalty dashboard —
  - Points + tier + progress-to-next-tier, same copy/logic pattern as the
    "¡Ganaste puntos!" card in `Bill.tsx` (reuses `TIER_LABELS` /
    `TIER_BADGE_CLASSNAMES` from
    `pages/admin/components/settings/loyalty/types`).
  - "Última visita" = `visits[0].visitedAt` (list is already
    newest-first); omit this line if `visits` is empty.
  - A visit-history list: date, `amountPaid` (or "—" if `null`, for
    pre-migration rows), `pointsEarned`. Empty-state copy if `visits` is
    `[]` ("Aún no tienes visitas registradas").
  - A compact "Entrar a una mesa" button/icon (opens the existing
    `JoinTableModal` via `useUIStore`) stays visible in this state too, per
    scope decision 4 — not removed, just no longer the dominant element.

No new route, no new nav entry — same `/customer/home` path, same
`JoinTableModal` already rendered by this page today.

## 5. Explicit non-goals

- No itemized per-dish order history.
- No "pay my active bill" shortcut on Home (`Bill.tsx` already owns that).
- No multi-restaurant aggregation or "Mis Restaurantes" list.
- No rewards-catalog display on Home (that's `/admin` management only
  today; a customer-facing rewards browse view is unrequested and out of
  scope).
- No pagination UI for visit history (hard-capped at the 20 most recent).
