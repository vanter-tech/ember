# Plan gating (Phase 1) — design spec

**Date:** 2026-09-14
**Status:** approved by user, pending implementation plan

## 1. Problem

`Restaurant.plan` (`RestaurantPlan`: `FREE, STARTER, PRO, ENTERPRISE`) already exists on the entity and defaults to `FREE`, but nothing in the codebase reads it — every tenant gets full functionality regardless of plan. Separately, the Console's tenant-creation form (`PlatformRestaurantCreateRequest`) has no `plan` field, so there is no way to create a tenant on anything but `FREE` in the first place.

`landing/src/lib/plans.ts` (`getComparison`) already documents, in marketing copy, what each tier is supposed to unlock — 16 rows across 4 categories. An audit against the codebase (this session) found:

- **6 rows don't exist as features at all** (rooms/salones, a distinct "advanced analytics" tier, a waiter-count limit, multi-branch, integrations, and the purely-contractual SLA/account-manager/support rows). These are out of scope for this spec — they need the underlying feature built before they can be gated, or the marketing copy needs correcting. Not addressed here.
- **4 rows are real features but spread across many files each** (floor/table management, bill splitting, printing, staff CRUD) — gating any one of them well is its own design exercise. Deferred to a later phase.
- **6 rows are real, single-gate-point features** — this spec implements exactly these 6, as Phase 1.

## 2. Phase 1 scope — the 6 gates

| # | Feature | Gate point | Threshold |
|---|---|---|---|
| 1 | Dining tables | `SettingService.syncDiningTables` | FREE: 1, STARTER: 10, PRO/ENTERPRISE: unlimited |
| 2 | Cash register (open/close/arqueo) | `CashShiftController.open` | STARTER+ |
| 3 | Analytics period filters | `AnalyticsController.getSales` (`granularity` param) | STARTER+ (FREE is forced to `DAY`) |
| 4 | Tenant data export | `ExportController`'s single endpoint | PRO+ |
| 5 | Custom branding | `SettingsController.updateSettings` (`SettingsPayload.branding` sub-object) | STARTER+ |
| 6 | Extra staff roles (KITCHEN, ACCOUNTANT) | `UserAdminService.create` | STARTER+ (FREE may only create WAITER/ADMIN staff) |

**Enforcement is forward-only.** A gate blocks a *new* action (adding a table past the limit, opening a shift, exporting, saving branding, creating a KITCHEN/ACCOUNTANT user, requesting a non-DAY granularity). It never deactivates, deletes, or hides anything that already exists — a tenant that already has a KITCHEN user and gets moved to FREE keeps that user active; they just can't create another one. This matters in practice: every tenant has been on `FREE` with full functionality since the field was added, so there is very likely production data that would "over-qualify" under any retroactive enforcement.

## 3. Backend: `PlanGateService`

New class: `backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java`.

```java
@Service
@RequiredArgsConstructor
public class PlanGateService {

    private static final Map<RestaurantPlan, Integer> TABLE_LIMITS = Map.of(
            RestaurantPlan.FREE, 1,
            RestaurantPlan.STARTER, 10,
            RestaurantPlan.PRO, Integer.MAX_VALUE,
            RestaurantPlan.ENTERPRISE, Integer.MAX_VALUE);

    private final RestaurantRepository restaurantRepository;

    public RestaurantPlan currentPlan(UUID tenantId) {
        return restaurantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + tenantId))
                .getPlan();
    }

    /** Throws when the tenant's plan is below {@code minimum}. RestaurantPlan's declaration
     *  order (FREE < STARTER < PRO < ENTERPRISE) makes compareTo a correct tier comparison. */
    public void requirePlanAtLeast(UUID tenantId, RestaurantPlan minimum, String feature) {
        RestaurantPlan plan = currentPlan(tenantId);
        if (plan.compareTo(minimum) < 0) {
            throw new PlanLimitExceededException(feature, minimum, plan);
        }
    }

    /** Throws when {@code requestedCount} exceeds the tenant's table limit. */
    public void requireTableCapacity(UUID tenantId, int requestedCount) {
        RestaurantPlan plan = currentPlan(tenantId);
        int limit = TABLE_LIMITS.get(plan);
        if (requestedCount > limit) {
            throw new PlanLimitExceededException("tables", plan, limit);
        }
    }
}
```

New exception: `backend/src/main/java/com/vanter/ember/restaurant/exception/PlanLimitExceededException.java` — a `RuntimeException` with two constructors matching the two calls above (`feature, requiredPlan, currentPlan` for tier gates; `feature, currentPlan, limit` for the table-count gate), exposing whichever fields it was built with (`getFeature()`, `getRequiredPlan()` nullable, `getCurrentPlan()`, `getLimit()` nullable — `OptionalInt` or a boxed `Integer`, implementer's call, consistent with the rest of the codebase's style).

### Exception handling

New handler in `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`, following the exact pattern already used for `CashShiftOverdueException` etc.:

```java
@ExceptionHandler(PlanLimitExceededException.class)
public ProblemDetail handlePlanLimitExceeded(PlanLimitExceededException ex, HttpServletRequest request) {
    ProblemDetail problem = problem(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request.getRequestURI());
    problem.setProperty("code", "PLAN_LIMIT_EXCEEDED");
    problem.setProperty("feature", ex.getFeature());
    if (ex.getRequiredPlan() != null) problem.setProperty("requiredPlan", ex.getRequiredPlan().name());
    problem.setProperty("currentPlan", ex.getCurrentPlan().name());
    return problem;
}
```

`402 Payment Required` is unused elsewhere in this codebase (403 is reserved for `@PreAuthorize` role denials, 409 for conflicts) — a distinct status the frontend can key off without inspecting the body, with `code`/`feature`/`requiredPlan`/`currentPlan` as the machine-readable detail, same shape as the existing `CASH_SHIFT_OVERDUE`/`BILL_NOT_PAID`/etc. codes.

## 4. Backend: the 6 insertion points

1. **Tables** — `SettingService.syncDiningTables(UUID restaurantId, List<...> requestedTables)` (`settings/service/SettingService.java`) already computes `diningTableRepository.countByRestaurantIdAndIsActiveTrue(restaurantId)` before the `difference > 0` branch. Add `planGateService.requireTableCapacity(restaurantId, requestedTables.size())` at the top of the method, before that existing count is used — reuses data already being fetched, no new query.
2. **Cash register** — `CashShiftController.open` (`cashregister/controller/CashShiftController.java`): first line of the method body, `planGateService.requirePlanAtLeast(TenantContextHolder.requireTenantId(), RestaurantPlan.STARTER, "cashclose")`. Gating only `open` is sufficient — `recordMovement`/`close`/`prolong` are unreachable without an open shift, so nothing else needs its own check.
3. **Analytics period filters** — `SalesGranularity.from(String)` (`analytics/dto/SalesGranularity.java`) currently defaults null/blank to `DAY`. Change `AnalyticsController.getSales` to resolve the tenant's plan once and pass it down, or call `planGateService.requirePlanAtLeast(..., STARTER, "periodfilters")` only when the resolved granularity is not `DAY` — `DAY` itself must stay available to FREE (it's the only value they're allowed).
4. **Export** — `ExportController`'s single `@GetMapping`: first line, `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.PRO, "export")`.
5. **Branding** — `SettingsController.updateSettings`: before delegating to `settingService.updateSettings`, compare `newPayload.getBranding()` against the tenant's *current* stored branding (fetch current `SettingsPayload` first — `SettingService` already has a getter for this, reuse it); if they differ, call `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "branding")` before persisting. Non-branding settings changes must go through untouched even on FREE.
6. **Staff roles** — `UserAdminService.create(UUID tenantId, CreateStaffRequest request)`: right after the existing `if (request.role() == Role.CUSTOMER) { ... }` rejection, add: if `request.role()` is `KITCHEN` or `ACCOUNTANT`, call `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "roles")`.

## 5. Console: assigning and changing plan

- `PlatformRestaurantCreateRequest` (`platform/model/dto/`) gains `private RestaurantPlan plan;` — **not** `@NotNull`; a null/omitted value keeps today's behavior (defaults to `FREE` via `Restaurant`'s `@Builder.Default`). `PlatformRestaurantService.create` passes it through to `Restaurant.builder().plan(request.getPlan() != null ? request.getPlan() : RestaurantPlan.FREE)`.
- New `PlatformRestaurantPlanUpdateRequest` (mirrors `PlatformRestaurantStatusUpdateRequest` exactly): `@Data class { @NotNull(message = "Plan is required") private RestaurantPlan plan; }`.
- New endpoint on `PlatformRestaurantController`: `PATCH /platform/restaurants/{id}/plan`, mirroring `updateStatus` line for line — resolves the operator, calls a new `PlatformRestaurantService.updatePlan(UUID restaurantId, RestaurantPlan newPlan, String operatorEmail)` that delegates the actual field change to `RestaurantService` (new method there, or reuse the logic from the tenant-facing `updatePlan` being removed in §6) and writes a `PlatformAuditLog` row with `action("RESTAURANT_PLAN_UPDATED")`, `oldValue(oldPlan.name())`, `newValue(newPlan.name())` — same builder shape as the status-update audit entry.
- Frontend: `ConsoleRestaurantCreate.tsx` — add a `plan` field to `createSchema` (`z.enum(['FREE','STARTER','PRO','ENTERPRISE']).default('FREE')`) and a `<Select>` `FormField` between `slug` and `adminName`. A plan-change action (`<Select>` + confirm) is added wherever the Console's tenant-detail view already lets an operator flip `status` — same interaction pattern, calling the new `PATCH .../plan`.

## 6. Removing tenant self-service plan changes

- Delete `RestaurantAdminController.updatePlan` (the `PATCH /admin/restaurant/plan` method) and `UpdateRestaurantPlanRequest`. **Keep** `RestaurantAdminController.get()` (`GET /admin/restaurant`) — it's independent, still needed so a tenant ADMIN can see (read-only) their own plan.
- `RestaurantService.updatePlan` (currently documented "safe for the tenant's own ADMIN to trigger") either gets deleted, or — cleaner given §5 needs the same field-mutation logic from the platform side — keep the method but drop that stale doc comment and call it only from `PlatformRestaurantService.updatePlan`.
- Frontend `restaurantAdminService` in `frontend/src/lib/api.ts` (~lines 577-586): delete `.updatePlan` (calls the removed `PATCH`) and the now-dead `UpdateRestaurantPlanRequest` type import. Keep `.getPlan` — it calls `GET /admin/restaurant`, which stays (§6 above), and costs nothing to leave in place for a future read-only "your plan" display even though no page calls it today.
- Regenerate `frontend/src/lib/backend-types.ts` (`pnpm run openapi` against a running backend) rather than hand-patching, since this phase changes request/response shapes in a few places — hand-patching drift risk is higher here than the single-enum-value patches done for the `ACCOUNTANT` role work.

## 7. Frontend: surfacing a blocked action

New helper `frontend/src/lib/planGate.ts`:

```ts
export function extractPlanGateError(error: unknown): { feature: string; requiredPlan?: string } | null {
  if (!axios.isAxiosError(error)) return null
  const data = error.response?.data as { code?: string; feature?: string; requiredPlan?: string } | undefined
  if (data?.code !== 'PLAN_LIMIT_EXCEEDED') return null
  return { feature: data.feature ?? '', requiredPlan: data.requiredPlan }
}
```

Same shape as the existing inline `error.response?.data?.code === 'CASH_SHIFT_OVERDUE'` check in `TableInformation.tsx`, factored out because this phase has 6 call sites instead of 1. Each of the 6 frontend call sites (settings save, cash-shift open, analytics granularity switch, export button, branding save, staff-create submit) catches this in its mutation's `onError` and shows a toast/dialog naming the required plan, instead of (or in addition to) the generic error toast already there. Exact copy/whether it's a toast vs. a dedicated "upgrade" dialog is left to implementation — not load-bearing for this spec.

## 8. Testing

Backend: TDD per gate — a red test asserting 402 with `PLAN_LIMIT_EXCEEDED` before the check exists, green after. `PlanGateServiceTest` (unit, mocked `RestaurantRepository`) covers the two methods directly (boundary cases: exactly-at-limit succeeds, one-over fails, each plan tier). Controller/service tests for each of the 6 call sites get one new case each ("blocked on FREE", "allowed on STARTER/PRO as appropriate") alongside their existing tests — not a new test class per gate.

Frontend: `extractPlanGateError` gets its own small unit test (matching code / non-matching code / non-axios error). No new component tests required beyond what each of the 6 mutations' existing test files already cover, unless a call site has no test today.

## 9. Out of scope (explicit)

- The 4 "spread" features (floor/table management, bill splitting, printing, staff CRUD as a whole) — not gated in this phase.
- The 6 non-existent marketing rows (rooms, "advanced" analytics, waiter-count limit, multi-branch, integrations, SLA/AM/support tiers) — not built, not gated. Landing copy correction (removing or marking these as roadmap) is a separate, non-code task for the user to action on `landing/src/lib/plans.ts` — not part of this spec's implementation plan.
- Any real payment/billing integration — plan changes remain manually operator-driven via the Console; `RestaurantAdminController`'s tenant self-service path is being removed, not replaced with a paid-upgrade flow.
