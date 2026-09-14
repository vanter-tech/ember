# Plan Gating (Phase 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `Restaurant.plan` (FREE/STARTER/PRO/ENTERPRISE) actually restrict functionality — 6 concrete gates, the Console can assign/change a tenant's plan, and the unpaid tenant self-service plan-change endpoint is removed.

**Architecture:** One new `PlanGateService` (backend) that every gate calls, throwing a `PlanLimitExceededException` mapped to `402 Payment Required` by `GlobalExceptionHandler`. One new `extractPlanGateError` helper (frontend) every gated mutation's `onError` calls to show a consistent "upgrade your plan" toast instead of a generic error. Each of the 6 features gets its backend check plus its one frontend call site wired in the same task, so every task ships a complete, working, testable slice.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 (backend), React 19 / TypeScript / TanStack Query / Zod / react-hook-form (frontend), JUnit 5 + Mockito + MockMvc (backend tests), Vitest (frontend tests).

**Spec:** `docs/superpowers/specs/2026-09-14-plan-gating-design.md`

## Global Constraints

- `RestaurantPlan` is declared `FREE, STARTER, PRO, ENTERPRISE` (ascending tier order) — `RestaurantPlan.compareTo(...)` is the correct way to compare tiers. Never reorder this enum.
- Every gate is **forward-only**: it blocks a new action, never deactivates or deletes anything that already exists.
- Blocked requests return **`402 Payment Required`** with body `{ code: "PLAN_LIMIT_EXCEEDED", feature: "<name>", requiredPlan?: "<PLAN>", currentPlan: "<PLAN>" }` — `requiredPlan` is present for tier gates, absent for the table-count gate.
- Follow this repo's existing `GlobalExceptionHandler` convention exactly: one `@ExceptionHandler` method per exception type, fully-qualified exception class name inline (no import), `problem(status, message, path)` helper, `problem.setProperty("code", "...")`.
- Backend tests: `@WebMvcTest` + `@MockBean` for controllers (matching `CashShiftControllerTest`/`AnalyticsControllerTest`/etc.), plain `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks` for services (matching `RestaurantServiceTest`/`UserAdminServiceTest`).
- No `pnpm run openapi` regeneration mid-plan — `frontend/src/lib/backend-types.ts` is regenerated once, in Task 10, after every backend shape change is done.
- One task per context window; each task ends in its own commit (no co-authorship/AI-attribution lines, per this repo's `CLAUDE.md`) and a numbered report in `/reports/`.

---

## Task 1: `PlanGateService` + `PlanLimitExceededException` + 402 handler + frontend `extractPlanGateError`

No existing gate depends on this yet — this task is infrastructure only, fully covered by its own unit tests.

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/restaurant/exception/PlanLimitExceededException.java`
- Create: `backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java`
- Create: `backend/src/test/java/com/vanter/ember/restaurant/service/PlanGateServiceTest.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- Create: `frontend/src/lib/planGate.ts`
- Create: `frontend/src/lib/planGate.test.ts`

**Interfaces:**
- Produces: `PlanGateService.currentPlan(UUID tenantId): RestaurantPlan`, `PlanGateService.requirePlanAtLeast(UUID tenantId, RestaurantPlan minimum, String feature): void` (throws `PlanLimitExceededException`), `PlanGateService.requireTableCapacity(UUID tenantId, int requestedCount): void` (throws `PlanLimitExceededException`). `PlanLimitExceededException.getFeature(): String`, `.getRequiredPlan(): RestaurantPlan` (nullable), `.getCurrentPlan(): RestaurantPlan`, `.getLimit(): Integer` (nullable). `extractPlanGateError(error: unknown): { feature: string; requiredPlan?: string } | null`. Every later task consumes these exact names.

- [ ] **Step 1: Write the failing test for `PlanGateService`**

```java
// backend/src/test/java/com/vanter/ember/restaurant/service/PlanGateServiceTest.java
package com.vanter.ember.restaurant.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanGateServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks PlanGateService planGateService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private Restaurant restaurantOnPlan(RestaurantPlan plan) {
        return Restaurant.builder().id(TENANT_ID).name("Acme").slug("acme").plan(plan).build();
    }

    @Test
    void requirePlanAtLeast_throwsWhenBelowMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        assertThatThrownBy(() ->
                planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose"))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void requirePlanAtLeast_allowsWhenAtMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.STARTER)));

        planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose");
    }

    @Test
    void requirePlanAtLeast_allowsWhenAboveMinimum() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.ENTERPRISE)));

        planGateService.requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "cashclose");
    }

    @Test
    void requireTableCapacity_throwsWhenOverLimit() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        assertThatThrownBy(() -> planGateService.requireTableCapacity(TENANT_ID, 2))
                .isInstanceOf(PlanLimitExceededException.class);
    }

    @Test
    void requireTableCapacity_allowsExactlyAtLimit() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.FREE)));

        planGateService.requireTableCapacity(TENANT_ID, 1);
    }

    @Test
    void requireTableCapacity_unlimitedOnProAndEnterprise() {
        when(restaurantRepository.findById(TENANT_ID))
                .thenReturn(Optional.of(restaurantOnPlan(RestaurantPlan.PRO)));

        planGateService.requireTableCapacity(TENANT_ID, 500);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PlanGateServiceTest`
Expected: FAIL — compile error, `PlanGateService`/`PlanLimitExceededException` don't exist yet.

- [ ] **Step 3: Create `PlanLimitExceededException`**

```java
// backend/src/main/java/com/vanter/ember/restaurant/exception/PlanLimitExceededException.java
package com.vanter.ember.restaurant.exception;

import com.vanter.ember.restaurant.model.RestaurantPlan;

/** Thrown by {@link com.vanter.ember.restaurant.service.PlanGateService} when the tenant's plan
 *  doesn't cover a gated feature — mapped to 402 by GlobalExceptionHandler. Two shapes: a tier
 *  gate (requiredPlan set, limit null) and a count gate (limit set, requiredPlan null). */
public class PlanLimitExceededException extends RuntimeException {

    private final String feature;
    private final RestaurantPlan requiredPlan;
    private final RestaurantPlan currentPlan;
    private final Integer limit;

    public PlanLimitExceededException(String feature, RestaurantPlan requiredPlan, RestaurantPlan currentPlan) {
        super("Feature '" + feature + "' requires plan " + requiredPlan
                + " or higher (current: " + currentPlan + ")");
        this.feature = feature;
        this.requiredPlan = requiredPlan;
        this.currentPlan = currentPlan;
        this.limit = null;
    }

    public PlanLimitExceededException(String feature, RestaurantPlan currentPlan, int limit) {
        super("Feature '" + feature + "' is limited to " + limit + " on plan " + currentPlan);
        this.feature = feature;
        this.requiredPlan = null;
        this.currentPlan = currentPlan;
        this.limit = limit;
    }

    public String getFeature() {
        return feature;
    }

    public RestaurantPlan getRequiredPlan() {
        return requiredPlan;
    }

    public RestaurantPlan getCurrentPlan() {
        return currentPlan;
    }

    public Integer getLimit() {
        return limit;
    }
}
```

- [ ] **Step 4: Create `PlanGateService`**

```java
// backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java
package com.vanter.ember.restaurant.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    /** Declaration order (FREE &lt; STARTER &lt; PRO &lt; ENTERPRISE) makes compareTo a correct
     *  tier comparison — never reorder the RestaurantPlan enum. */
    public void requirePlanAtLeast(UUID tenantId, RestaurantPlan minimum, String feature) {
        RestaurantPlan plan = currentPlan(tenantId);
        if (plan.compareTo(minimum) < 0) {
            throw new PlanLimitExceededException(feature, minimum, plan);
        }
    }

    public void requireTableCapacity(UUID tenantId, int requestedCount) {
        RestaurantPlan plan = currentPlan(tenantId);
        int limit = TABLE_LIMITS.get(plan);
        if (requestedCount > limit) {
            throw new PlanLimitExceededException("tables", plan, limit);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PlanGateServiceTest`
Expected: PASS, 6/6.

- [ ] **Step 6: Wire the 402 handler into `GlobalExceptionHandler`**

In `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`, insert this new method right after `handleCashShiftOverdue` (after its closing `}`, before `handleBadCredentials`):

```java
    @ExceptionHandler(com.vanter.ember.restaurant.exception.PlanLimitExceededException.class)
    public ProblemDetail handlePlanLimitExceeded(
            com.vanter.ember.restaurant.exception.PlanLimitExceededException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request.getRequestURI());
        problem.setProperty("code", "PLAN_LIMIT_EXCEEDED");
        problem.setProperty("feature", ex.getFeature());
        if (ex.getRequiredPlan() != null) {
            problem.setProperty("requiredPlan", ex.getRequiredPlan().name());
        }
        problem.setProperty("currentPlan", ex.getCurrentPlan().name());
        return problem;
    }
```

No test for this handler in isolation (matching this file's existing convention — `CashShiftOverdueException`/`BillNotPaidException` aren't tested in `GlobalExceptionHandlerTest` either; the 402 mapping gets its first real proof in Task 2's controller test).

- [ ] **Step 7: Write the failing test for `extractPlanGateError`**

```ts
// frontend/src/lib/planGate.test.ts
import { describe, test, expect } from 'vitest'
import axios from 'axios'
import { extractPlanGateError } from './planGate'

describe('extractPlanGateError', () => {
  test('returns null for a non-axios error', () => {
    expect(extractPlanGateError(new Error('boom'))).toBeNull()
  })

  test('returns null when the axios error has a different code', () => {
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 409,
      data: { code: 'CASH_SHIFT_OVERDUE' },
    } as never)
    expect(extractPlanGateError(error)).toBeNull()
  })

  test('extracts feature and requiredPlan when the code matches', () => {
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 402,
      data: { code: 'PLAN_LIMIT_EXCEEDED', feature: 'export', requiredPlan: 'PRO' },
    } as never)
    expect(extractPlanGateError(error)).toEqual({ feature: 'export', requiredPlan: 'PRO' })
  })
})
```

- [ ] **Step 8: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/lib/planGate.test.ts`
Expected: FAIL — `./planGate` module doesn't exist.

- [ ] **Step 9: Create `extractPlanGateError`**

```ts
// frontend/src/lib/planGate.ts
import axios from 'axios'

export interface PlanGateError {
  feature: string
  requiredPlan?: string
}

/** Matches the `{ code: "PLAN_LIMIT_EXCEEDED", feature, requiredPlan, currentPlan }` body
 *  GlobalExceptionHandler.handlePlanLimitExceeded sends on a 402, same code-property pattern
 *  already used for CASH_SHIFT_OVERDUE etc. (see TableInformation.tsx). */
export function extractPlanGateError(error: unknown): PlanGateError | null {
  if (!axios.isAxiosError(error)) return null
  const data = error.response?.data as
    | { code?: string; feature?: string; requiredPlan?: string }
    | undefined
  if (data?.code !== 'PLAN_LIMIT_EXCEEDED') return null
  return { feature: data.feature ?? '', requiredPlan: data.requiredPlan }
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/lib/planGate.test.ts`
Expected: PASS, 3/3.

- [ ] **Step 11: Add the shared toast copy key**

Add this key to both `frontend/src/locales/es/common.ts` and `frontend/src/locales/en/common.ts` (inside their exported object, alongside the existing keys):

```ts
// es/common.ts
planGateUpgradeToast: 'Esta función requiere el plan {{plan}} o superior.',
```
```ts
// en/common.ts
planGateUpgradeToast: 'This feature requires the {{plan}} plan or higher.',
```

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/restaurant/exception/PlanLimitExceededException.java \
        backend/src/main/java/com/vanter/ember/restaurant/service/PlanGateService.java \
        backend/src/test/java/com/vanter/ember/restaurant/service/PlanGateServiceTest.java \
        backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java \
        frontend/src/lib/planGate.ts frontend/src/lib/planGate.test.ts \
        frontend/src/locales/es/common.ts frontend/src/locales/en/common.ts
git commit -m "feat(restaurant): add PlanGateService, 402 handler, and frontend plan-gate helper"
```

---

## Task 2: Gate — dining table count

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/settings/service/SettingService.java`
- Create: `backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java` (no such file exists yet)
- Modify: `frontend/src/pages/admin/components/settings/SpaceSettings.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requireTableCapacity(UUID, int)` (Task 1), `extractPlanGateError` (Task 1).
- Produces: nothing new consumed elsewhere.

- [ ] **Step 1: Write the failing tests**

```java
// backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java
package com.vanter.ember.settings.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.restaurant.exception.PlanLimitExceededException;
import com.vanter.ember.restaurant.model.RestaurantPlan;
import com.vanter.ember.restaurant.service.PlanGateService;
import com.vanter.ember.settings.model.RestaurantSettings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.repository.DiningTableRepository;
import com.vanter.ember.settings.repository.SettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

    @Mock SettingsRepository settingsRepository;
    @Mock DiningTableRepository diningTableRepository;
    @Mock PlanGateService planGateService;
    @InjectMocks SettingService settingService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SettingsPayload payloadWithTables(int totalTables) {
        SettingsPayload payload = new SettingsPayload();
        payload.getSpace().setTotalTables(totalTables);
        return payload;
    }

    @Test
    void updateSettings_blockedWhenRequestedTablesExceedThePlanLimit() {
        SettingsPayload payload = payloadWithTables(5);
        doThrow(new PlanLimitExceededException("tables", RestaurantPlan.FREE, 1))
                .when(planGateService).requireTableCapacity(TENANT_ID, 5);

        assertThatThrownBy(() -> settingService.updateSettings(TENANT_ID, payload))
                .isInstanceOf(PlanLimitExceededException.class);

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_allowedWhenRequestedTablesAreWithinThePlanLimit() {
        SettingsPayload payload = payloadWithTables(1);
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(new SettingsPayload());
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(0L);

        settingService.updateSettings(TENANT_ID, payload);

        verify(settingsRepository).save(current);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=SettingServiceTest`
Expected: FAIL — `SettingService` has no `PlanGateService` constructor param yet (compile error), or (once that's stubbed) the blocked test fails because nothing throws.

- [ ] **Step 3: Wire the gate into `SettingService`**

In `backend/src/main/java/com/vanter/ember/settings/service/SettingService.java`:
- Add `import com.vanter.ember.restaurant.service.PlanGateService;`
- Add field `private final PlanGateService planGateService;` alongside the existing `settingsRepository`/`diningTableRepository` fields (constructor is `@RequiredArgsConstructor`, no other change needed).
- Change `updateSettings` (currently lines 41-50) to:

```java
    @Transactional
    public void updateSettings(UUID restaurantId, SettingsPayload payload) {
        planGateService.requireTableCapacity(restaurantId, payload.getSpace().getTotalTables());

        RestaurantSettings currentSettings = getSettings(restaurantId);
        currentSettings.setPayload(payload);
        settingsRepository.save(currentSettings);

        int requestedTables = payload.getSpace().getTotalTables();
        syncDiningTables(restaurantId, requestedTables);
    }
```

(The gate runs before `getSettings`/`save` so a blocked request never touches the database — matches the test's `verify(settingsRepository, never()).save(any())`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=SettingServiceTest`
Expected: PASS, 2/2.

- [ ] **Step 5: Run the existing `SettingsControllerTest` to confirm no regression**

Run: `cd backend && ./mvnw test -Dtest=SettingsControllerTest`
Expected: PASS — it `@MockBean`s `SettingService` entirely, so it's unaffected by the new constructor parameter.

- [ ] **Step 6: Wire the frontend — `SpaceSettings.tsx`**

Read `frontend/src/pages/admin/components/settings/SpaceSettings.tsx` first to get its exact current `onError` line (reported shape: `onError: () => { toast.error(t('settingsSaveErrorToast')) }` around line 30-41, using `useTranslation('admin')` as `t`). Add a second translation hook and branch the error:

```tsx
// near the top of the component, alongside the existing useTranslation('admin') call
const { t: tCommon } = useTranslation('common')
```

```tsx
  onError: (error) => {
    const gate = extractPlanGateError(error)
    toast.error(
      gate
        ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
        : t('settingsSaveErrorToast'),
    )
  },
```

Add the import: `import { extractPlanGateError } from '@/lib/planGate'`.

- [ ] **Step 7: Run the frontend build and existing settings tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean; full suite green (no test currently asserts on `SpaceSettings.tsx`'s error copy, so nothing to update).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/settings/service/SettingService.java \
        backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java \
        frontend/src/pages/admin/components/settings/SpaceSettings.tsx
git commit -m "feat(settings): gate dining table count by plan"
```

---

## Task 3: Gate — cash register open

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
- Modify: `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requirePlanAtLeast` (Task 1), `extractPlanGateError` (Task 1).

- [ ] **Step 1: Write the failing test**

Add to `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`: add `@MockBean com.vanter.ember.restaurant.service.PlanGateService planGateService;` to the field list, then add this test near `open_returnsCreatedForAccountant`:

```java
    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void open_blockedWhenPlanBelowStarter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        org.mockito.Mockito.doThrow(new com.vanter.ember.restaurant.exception.PlanLimitExceededException(
                        "cashclose", com.vanter.ember.restaurant.model.RestaurantPlan.STARTER,
                        com.vanter.ember.restaurant.model.RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(
                        TENANT_ID, com.vanter.ember.restaurant.model.RestaurantPlan.STARTER, "cashclose");

        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("100.00"));
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerTest`
Expected: FAIL — compile error (`planGateService` field doesn't exist on the controller / `PlanGateService` never referenced), or once it compiles, a 201 instead of the expected 402.

- [ ] **Step 3: Wire the gate into `CashShiftController.open`**

In `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`:
- Add `import com.vanter.ember.restaurant.model.RestaurantPlan;` and `import com.vanter.ember.restaurant.service.PlanGateService;`
- Add field `private final PlanGateService planGateService;`
- Change `open` to:

```java
    @Operation(summary = "Open a new cash shift — Apertura de Caja (ACCOUNTANT)")
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public CashShiftResponse open(@Valid @RequestBody OpenShiftRequest request, Authentication authentication) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "cashclose");
        CashShift shift = cashShiftService.openShift(
                tenantId, resolveUserId(authentication), request.openingFloat());
        return cashShiftService.toResponse(shift);
    }
```

(Add `import java.util.UUID;` if not already present — check first, `CashShiftController` already imports `java.time.LocalDate`/`LocalDateTime` but confirm `UUID` before adding a duplicate.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerTest,CashShiftControllerProlongTest`
Expected: PASS, all green (the new test plus every pre-existing one — `@MockBean` defaults `planGateService.requirePlanAtLeast` to a no-op, so `open_returnsCreatedForAccountant` still passes unmodified).

- [ ] **Step 5: Wire the frontend — `OpenShiftDialog.tsx`**

In `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx` (mutation at lines 39-50 per the exploration), change:

```tsx
  onError: () => {
    toast.error(t('shiftOpenErrorToast'))
  },
```

to:

```tsx
  onError: (error) => {
    const gate = extractPlanGateError(error)
    toast.error(
      gate
        ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
        : t('shiftOpenErrorToast'),
    )
  },
```

Add `import { extractPlanGateError } from '@/lib/planGate'` and, alongside the existing `useTranslation('waiter')` call, `const { t: tCommon } = useTranslation('common')`.

- [ ] **Step 6: Run the frontend build and cash-register tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean, full suite green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java \
        backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java \
        frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx
git commit -m "feat(cashregister): gate opening a cash shift by plan"
```

---

## Task 4: Gate — analytics period filters

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`
- Modify: `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`
- Modify: `frontend/src/pages/admin/analytics/components/SalesChart.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requirePlanAtLeast` (Task 1), `extractPlanGateError` (Task 1), `SalesGranularity.from(String)` (existing, defaults null/blank to `DAY`).

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java`: add `@MockBean com.vanter.ember.restaurant.service.PlanGateService planGateService;` to the field list, then add:

```java
    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_blockedWhenNonDayGranularityAndPlanBelowStarter() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        org.mockito.Mockito.doThrow(new com.vanter.ember.restaurant.exception.PlanLimitExceededException(
                        "periodfilters", com.vanter.ember.restaurant.model.RestaurantPlan.STARTER,
                        com.vanter.ember.restaurant.model.RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(
                        TENANT_ID, com.vanter.ember.restaurant.model.RestaurantPlan.STARTER, "periodfilters");

        mockMvc.perform(get("/admin/analytics/sales").param("granularity", "week"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sales_defaultDayGranularityIsNeverGated() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(analyticsService.getSales(any(), any(), any(), any())).thenReturn(null);

        mockMvc.perform(get("/admin/analytics/sales"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(planGateService, org.mockito.Mockito.never())
                .requirePlanAtLeast(any(), any(), any());
    }
```

(Reuse whatever `any()`/`jsonPath` static imports the file already has; add `import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;` if it isn't already imported.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=AnalyticsControllerTest`
Expected: FAIL — compile error until `PlanGateService` is referenced by the controller, then the blocked test gets 200 instead of 402.

- [ ] **Step 3: Wire the gate into `AnalyticsController.getSales`**

In `backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java`:
- Add imports: `java.util.UUID`, `com.vanter.ember.analytics.dto.SalesGranularity`, `com.vanter.ember.restaurant.model.RestaurantPlan`, `com.vanter.ember.restaurant.service.PlanGateService`.
- Add field `private final PlanGateService planGateService;`
- Change `getSales` to:

```java
    @GetMapping("/sales")
    public AnalyticsSalesResponse getSales(
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        if (SalesGranularity.from(granularity) != SalesGranularity.DAY) {
            planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "periodfilters");
        }
        return analyticsService.getSales(tenantId, granularity, from, to);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=AnalyticsControllerTest`
Expected: PASS, all green including the 2 new tests and every pre-existing `sales_*` test (default-mocked `planGateService` never throws).

- [ ] **Step 5: Wire the frontend — `SalesChart.tsx`**

In `frontend/src/pages/admin/analytics/components/SalesChart.tsx` (currently a bare `useQuery`, no error branching at all — lines 32-39 per the exploration), change:

```tsx
const { data, isLoading, isError } = useQuery({
  queryKey: ['analyticsSales', granularity],
  queryFn: () => analyticsService.getSales(granularity),
})
```

to:

```tsx
const { data, isLoading, isError, error } = useQuery({
  queryKey: ['analyticsSales', granularity],
  queryFn: () => analyticsService.getSales(granularity),
})
const planGate = isError ? extractPlanGateError(error) : null
```

Then where the existing `isError` branch renders `t('loadingSalesError')` (lines 85-89), render `planGate ? tCommon('planGateUpgradeToast', { plan: planGate.requiredPlan ?? '' }) : t('loadingSalesError')` instead.

Add `import { extractPlanGateError } from '@/lib/planGate'` and, alongside whatever `useTranslation` call the file already has, `const { t: tCommon } = useTranslation('common')`.

- [ ] **Step 6: Run the frontend build and analytics tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean, full suite green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/analytics/controller/AnalyticsController.java \
        backend/src/test/java/com/vanter/ember/analytics/controller/AnalyticsControllerTest.java \
        frontend/src/pages/admin/analytics/components/SalesChart.tsx
git commit -m "feat(analytics): gate non-day sales granularity by plan"
```

---

## Task 5: Gate — tenant data export

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java`
- Modify: `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java`
- Modify: `frontend/src/pages/admin/components/settings/ExportSettings.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requirePlanAtLeast` (Task 1), `extractPlanGateError` (Task 1).

- [ ] **Step 1: Write the failing test**

Add to `backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java`: add `@MockBean com.vanter.ember.restaurant.service.PlanGateService planGateService;`, then:

```java
    @Test
    @WithMockUser(roles = "ADMIN")
    void export_blockedWhenPlanBelowPro() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        org.mockito.Mockito.doThrow(new com.vanter.ember.restaurant.exception.PlanLimitExceededException(
                        "export", com.vanter.ember.restaurant.model.RestaurantPlan.PRO,
                        com.vanter.ember.restaurant.model.RestaurantPlan.STARTER))
                .when(planGateService).requirePlanAtLeast(
                        TENANT_ID, com.vanter.ember.restaurant.model.RestaurantPlan.PRO, "export");

        mockMvc.perform(get("/admin/export"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PLAN_LIMIT_EXCEEDED"));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=ExportControllerTest`
Expected: FAIL — compile error, then 200 instead of 402.

- [ ] **Step 3: Wire the gate into `ExportController.exportData`**

In `backend/src/main/java/com/vanter/ember/export/controller/ExportController.java`:
- Add imports: `java.util.UUID`, `com.vanter.ember.restaurant.model.RestaurantPlan`, `com.vanter.ember.restaurant.service.PlanGateService`.
- Add field `private final PlanGateService planGateService;`
- Change `exportData` to:

```java
    @GetMapping
    public ResponseEntity<byte[]> exportData(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    LocalDateTime to) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.PRO, "export");
        byte[] workbook = exportService.buildTenantExportWorkbook(tenantId, from, to);
        String filename = "ember-export-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf(XLSX_CONTENT_TYPE))
                .body(workbook);
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=ExportControllerTest`
Expected: PASS, all green.

- [ ] **Step 5: Wire the frontend — `ExportSettings.tsx`**

In `frontend/src/pages/admin/components/settings/ExportSettings.tsx` (mutation at lines 22-36 per the exploration), change:

```tsx
onError: () => toast.error(t('exportErrorToast')),
```

to:

```tsx
onError: (error) => {
  const gate = extractPlanGateError(error)
  toast.error(
    gate
      ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
      : t('exportErrorToast'),
  )
},
```

Add `import { extractPlanGateError } from '@/lib/planGate'` and `const { t: tCommon } = useTranslation('common')` alongside the file's existing `useTranslation` call.

- [ ] **Step 6: Run the frontend build and settings tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean, full suite green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/export/controller/ExportController.java \
        backend/src/test/java/com/vanter/ember/export/controller/ExportControllerTest.java \
        frontend/src/pages/admin/components/settings/ExportSettings.tsx
git commit -m "feat(export): gate tenant data export by plan"
```

---

## Task 6: Gate — custom branding

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/settings/service/SettingService.java` (already has `planGateService` since Task 2)
- Modify: `backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java` (created in Task 2)
- Modify: `frontend/src/pages/admin/components/settings/BrandingSettings.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requirePlanAtLeast` (Task 1), `extractPlanGateError` (Task 1). Reuses the `planGateService` field `SettingService` already has from Task 2.

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java`:

```java
    @Test
    void updateSettings_blockedWhenBrandingChangedAndPlanBelowStarter() {
        SettingsPayload newPayload = payloadWithTables(1);
        newPayload.getBranding().setBusinessName("New Name");
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(new SettingsPayload());
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        doThrow(new PlanLimitExceededException("branding", RestaurantPlan.STARTER, RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "branding");

        assertThatThrownBy(() -> settingService.updateSettings(TENANT_ID, newPayload))
                .isInstanceOf(PlanLimitExceededException.class);

        verify(settingsRepository, never()).save(any());
    }

    @Test
    void updateSettings_allowedWhenBrandingUnchanged() {
        SettingsPayload newPayload = payloadWithTables(1);
        RestaurantSettings current = new RestaurantSettings();
        current.setRestaurantId(TENANT_ID);
        current.setPayload(payloadWithTables(1));
        when(settingsRepository.findByRestaurantId(TENANT_ID)).thenReturn(Optional.of(current));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(1L);

        settingService.updateSettings(TENANT_ID, newPayload);

        verify(planGateService, never()).requirePlanAtLeast(TENANT_ID, RestaurantPlan.STARTER, "branding");
        verify(settingsRepository).save(current);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=SettingServiceTest`
Expected: FAIL — `updateSettings_blockedWhenBrandingChangedAndPlanBelowStarter` doesn't throw yet (nothing compares branding).

- [ ] **Step 3: Wire the branding gate into `SettingService.updateSettings`**

Change the method (as it stands after Task 2) to:

```java
    @Transactional
    public void updateSettings(UUID restaurantId, SettingsPayload payload) {
        planGateService.requireTableCapacity(restaurantId, payload.getSpace().getTotalTables());

        RestaurantSettings currentSettings = getSettings(restaurantId);

        if (!payload.getBranding().equals(currentSettings.getPayload().getBranding())) {
            planGateService.requirePlanAtLeast(restaurantId, RestaurantPlan.STARTER, "branding");
        }

        currentSettings.setPayload(payload);
        settingsRepository.save(currentSettings);

        int requestedTables = payload.getSpace().getTotalTables();
        syncDiningTables(restaurantId, requestedTables);
    }
```

Add `import com.vanter.ember.restaurant.model.RestaurantPlan;` (the `SettingsPayload.BrandingSettings` nested `@Data` class already generates a field-by-field `.equals()` — no manual comparator needed).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=SettingServiceTest`
Expected: PASS, 4/4 (2 from Task 2 + 2 new).

- [ ] **Step 5: Run `SettingsControllerTest` to confirm no regression**

Run: `cd backend && ./mvnw test -Dtest=SettingsControllerTest`
Expected: PASS, unaffected (still fully mocks `SettingService`).

- [ ] **Step 6: Wire the frontend — `BrandingSettings.tsx`**

In `frontend/src/pages/admin/components/settings/BrandingSettings.tsx` (mutation at lines 35-46 per the exploration), change:

```tsx
onError: () => {
  toast.error(t('settingsSaveErrorToast'))
},
```

to:

```tsx
onError: (error) => {
  const gate = extractPlanGateError(error)
  toast.error(
    gate
      ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
      : t('settingsSaveErrorToast'),
  )
},
```

Add `import { extractPlanGateError } from '@/lib/planGate'` and `const { t: tCommon } = useTranslation('common')` alongside the file's existing translation hook.

- [ ] **Step 7: Run the frontend build and settings tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean, full suite green.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/settings/service/SettingService.java \
        backend/src/test/java/com/vanter/ember/settings/service/SettingServiceTest.java \
        frontend/src/pages/admin/components/settings/BrandingSettings.tsx
git commit -m "feat(settings): gate custom branding changes by plan"
```

---

## Task 7: Gate — extra staff roles (KITCHEN, ACCOUNTANT)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- Modify: `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- Modify: `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx`

**Interfaces:**
- Consumes: `PlanGateService.requirePlanAtLeast` (Task 1), `extractPlanGateError` (Task 1).

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`: add `@Mock com.vanter.ember.restaurant.service.PlanGateService planGateService;` to the field list, then:

```java
    @Test
    void create_blockedForKitchenRoleWhenPlanBelowStarter() {
        org.mockito.Mockito.doThrow(new com.vanter.ember.restaurant.exception.PlanLimitExceededException(
                        "roles", com.vanter.ember.restaurant.model.RestaurantPlan.STARTER,
                        com.vanter.ember.restaurant.model.RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(
                        TENANT_A, com.vanter.ember.restaurant.model.RestaurantPlan.STARTER, "roles");

        assertThatThrownBy(() -> userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Cook", "cook@test.com", "Sup3r$ecret!", Role.KITCHEN,
                        "Cocinero", "Mañana", "Tiempo completo", "Sucursal Centro")))
                .isInstanceOf(com.vanter.ember.restaurant.exception.PlanLimitExceededException.class);
    }

    @Test
    void create_blockedForAccountantRoleWhenPlanBelowStarter() {
        org.mockito.Mockito.doThrow(new com.vanter.ember.restaurant.exception.PlanLimitExceededException(
                        "roles", com.vanter.ember.restaurant.model.RestaurantPlan.STARTER,
                        com.vanter.ember.restaurant.model.RestaurantPlan.FREE))
                .when(planGateService).requirePlanAtLeast(
                        TENANT_A, com.vanter.ember.restaurant.model.RestaurantPlan.STARTER, "roles");

        assertThatThrownBy(() -> userAdminService.create(
                TENANT_A, new CreateStaffRequest(
                        "Acc", "acc@test.com", "Sup3r$ecret!", Role.ACCOUNTANT,
                        "Contador", "Mañana", "Tiempo completo", "Sucursal Centro")))
                .isInstanceOf(com.vanter.ember.restaurant.exception.PlanLimitExceededException.class);
    }

    @Test
    void create_allowsWaiterRoleWithoutTouchingThePlanGate() {
        com.vanter.ember.restaurant.model.Restaurant restaurant =
                com.vanter.ember.restaurant.model.Restaurant.builder()
                        .id(TENANT_A).name("Acme").slug("acme").build();
        when(restaurantRepository.findById(TENANT_A)).thenReturn(Optional.of(restaurant));
        when(userRepository.existsByEmail("w@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userAdminService.create(TENANT_A, new CreateStaffRequest(
                "Wai", "w@test.com", "Sup3r$ecret!", Role.WAITER,
                "Mesera", "Mañana", "Tiempo completo", "Sucursal Centro"));

        org.mockito.Mockito.verify(planGateService, org.mockito.Mockito.never())
                .requirePlanAtLeast(any(), any(), any());
    }
```

(Match whichever `import static org.mockito.ArgumentMatchers.any;` / `when` imports the file already has.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=UserAdminServiceTest`
Expected: FAIL — compile error until `UserAdminService` takes a `PlanGateService`, then the two "blocked" tests get a saved user instead of the exception.

- [ ] **Step 3: Wire the gate into `UserAdminService.create`**

In `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`:
- Add `import com.vanter.ember.restaurant.model.RestaurantPlan;` and `import com.vanter.ember.restaurant.service.PlanGateService;`
- Add field `private final PlanGateService planGateService;`
- Change the start of `create` to:

```java
    public StaffMemberResponse create(UUID tenantId, CreateStaffRequest request) {
        if (request.role() == Role.CUSTOMER) {
            throw new IllegalArgumentException("Cannot create a CUSTOMER account as staff");
        }
        if (request.role() == Role.KITCHEN || request.role() == Role.ACCOUNTANT) {
            planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "roles");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use: " + request.email());
        }
        // ... rest of the method unchanged
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=UserAdminServiceTest`
Expected: PASS, all green (existing `create_savesEncodedPasswordAndTenantBoundUser` etc. use `Role.WAITER`/`Role.ADMIN` per their names, so the default no-op mock never fires the new gate for them — if any existing test happens to use `Role.KITCHEN`/`Role.ACCOUNTANT`, adjust it to also mock `planGateService` to no-op explicitly, which is Mockito's default behavior for an unstubbed void method anyway).

- [ ] **Step 5: Wire the frontend — `CreateStaffModal.tsx`**

In `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx` (mutation at lines 75-86 per the exploration), change:

```tsx
onError: () => {
  toast.error(t('staffCreateErrorToast'))
},
```

to:

```tsx
onError: (error) => {
  const gate = extractPlanGateError(error)
  toast.error(
    gate
      ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
      : t('staffCreateErrorToast'),
  )
},
```

Add `import { extractPlanGateError } from '@/lib/planGate'` and `const { t: tCommon } = useTranslation('common')` alongside the file's existing `useTranslation('admin')` call.

- [ ] **Step 6: Run the frontend build and staff tests**

Run: `cd frontend && pnpm run build && pnpm vitest run`
Expected: build clean, full suite green.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java \
        backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java \
        frontend/src/pages/admin/staff/components/CreateStaffModal.tsx
git commit -m "feat(identity): gate KITCHEN/ACCOUNTANT staff creation by plan"
```

---

## Task 8: Console — assign a plan when creating a tenant

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`

**Interfaces:**
- Produces: `PlatformRestaurantCreateRequest.getPlan(): RestaurantPlan` (nullable). Frontend `PlatformRestaurantCreateRequest.plan?: 'FREE'|'STARTER'|'PRO'|'ENTERPRISE'`.

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`:

```java
    private PlatformRestaurantCreateRequest createRequest(RestaurantPlan plan) {
        PlatformRestaurantCreateRequest request = new PlatformRestaurantCreateRequest();
        request.setName("Tenant Grill");
        request.setSlug("tenant-grill");
        request.setAdminName("Jane");
        request.setAdminEmail("jane@tenant.com");
        request.setAdminPassword("Sup3r$ecret!");
        request.setPlan(plan);
        return request;
    }

    @Test
    void create_defaultsToFreeWhenPlanOmitted() {
        when(platformOperatorRepository.findByEmail("op@ember.local"))
                .thenReturn(Optional.of(PlatformOperator.builder()
                        .id(UUID.randomUUID()).email("op@ember.local").build()));
        when(restaurantRepository.existsBySlug("tenant-grill")).thenReturn(false);
        when(userRepository.existsByEmail("jane@tenant.com")).thenReturn(false);
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        when(restaurantRepository.save(captor.capture())).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        platformRestaurantService.create(createRequest(null), "op@ember.local");

        assertThat(captor.getValue().getPlan()).isEqualTo(RestaurantPlan.FREE);
    }

    @Test
    void create_usesRequestedPlanWhenProvided() {
        when(platformOperatorRepository.findByEmail("op@ember.local"))
                .thenReturn(Optional.of(PlatformOperator.builder()
                        .id(UUID.randomUUID()).email("op@ember.local").build()));
        when(restaurantRepository.existsBySlug("tenant-grill")).thenReturn(false);
        when(userRepository.existsByEmail("jane@tenant.com")).thenReturn(false);
        ArgumentCaptor<Restaurant> captor = ArgumentCaptor.forClass(Restaurant.class);
        when(restaurantRepository.save(captor.capture())).thenAnswer(inv -> {
            Restaurant r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        platformRestaurantService.create(createRequest(RestaurantPlan.PRO), "op@ember.local");

        assertThat(captor.getValue().getPlan()).isEqualTo(RestaurantPlan.PRO);
    }
```

(The test class already imports `ArgumentCaptor`, `PlatformOperator`, `RestaurantPlan`, `Restaurant`, `Optional`, `UUID` — no new imports needed.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: FAIL — `PlatformRestaurantCreateRequest` has no `setPlan`/`getPlan` yet (compile error).

- [ ] **Step 3: Add the `plan` field to `PlatformRestaurantCreateRequest`**

```java
// backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java
package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.RestaurantPlan;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PlatformRestaurantCreateRequest {

    @NotBlank(message = "Restaurant name is required")
    private String name;

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug must be lowercase alphanumeric with single hyphens")
    private String slug;

    /** Optional — omitted or null defaults to FREE, same as Restaurant's own @Builder.Default. */
    private RestaurantPlan plan;

    @NotBlank(message = "Admin name is required")
    private String adminName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be valid")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).+$",
            message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
    )
    private String adminPassword;
}
```

- [ ] **Step 4: Use the plan in `PlatformRestaurantService.create`**

Change the `Restaurant.builder()` call (currently 3 lines, around line 223-226) to:

```java
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .plan(request.getPlan() != null ? request.getPlan() : RestaurantPlan.FREE)
                .build());
```

Add `import com.vanter.ember.restaurant.model.RestaurantPlan;` if not already present in this file (it already imports `Restaurant`/`RestaurantStatus` per the earlier exploration, but confirm `RestaurantPlan` specifically before adding — it may already be imported for `updateStatus`'s use of `RestaurantStatus` only, so `RestaurantPlan` is likely new here).

**Note:** passing `request.getPlan()` directly (without the ternary) would break `Restaurant`'s `@Builder.Default` — Lombok's default only applies when the builder method for that field is never called at all; calling `.plan(null)` would persist `null` into a `NOT NULL` column. The ternary is required, not optional style.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: PASS, all green.

- [ ] **Step 6: Frontend — add `plan` to the request type and the create form**

In `frontend/src/lib/platformApi.ts`, change the `PlatformRestaurantCreateRequest` interface (lines 70-77) to:

```ts
// Mirrors PlatformRestaurantCreateRequest (platform/model/dto).
export interface PlatformRestaurantCreateRequest {
  name: string
  slug: string
  plan?: 'FREE' | 'STARTER' | 'PRO' | 'ENTERPRISE'
  adminName: string
  adminEmail: string
  adminPassword: string
}
```

In `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`:
- Add `import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'`.
- Add `plan: z.enum(['FREE', 'STARTER', 'PRO', 'ENTERPRISE']).default('FREE')` to `createSchema` (after the `slug` field).
- Add `plan: 'FREE' as const` to the `useForm` `defaultValues` object (after `slug`).
- Insert this `FormField` between the `slug` `FormField` and the `adminName` `FormField`:

```tsx
              <FormField
                control={form.control}
                name="plan"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Plan</FormLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <FormControl>
                        <SelectTrigger className="w-full">
                          <SelectValue />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="FREE">Free</SelectItem>
                        <SelectItem value="STARTER">Starter</SelectItem>
                        <SelectItem value="PRO">Pro</SelectItem>
                        <SelectItem value="ENTERPRISE">Enterprise</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
```

- [ ] **Step 7: Run the frontend build**

Run: `cd frontend && pnpm run build`
Expected: clean.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java \
        backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java \
        backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java \
        frontend/src/lib/platformApi.ts \
        frontend/src/pages/console/ConsoleRestaurantCreate.tsx
git commit -m "feat(platform): let the Console assign a plan when creating a tenant"
```

---

## Task 9: Console — change a tenant's plan after creation

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantPlanUpdateRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`

**Interfaces:**
- Produces: `PlatformRestaurantService.updatePlan(UUID restaurantId, RestaurantPlan newPlan, String operatorEmail): PlatformRestaurantSummaryResponse`. Frontend `platformRestaurantService.updatePlan(id: string, plan: PlatformRestaurantDetail['plan']): Promise<PlatformRestaurantSummary>`.

- [ ] **Step 1: Write the failing service test**

Add to `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`:

```java
    @Test
    void updatePlan_updatesRestaurantAndWritesAuditLog() {
        UUID restaurantId = UUID.randomUUID();
        Restaurant restaurant = Restaurant.builder()
                .id(restaurantId).name("Acme").slug("acme").plan(RestaurantPlan.FREE).build();
        when(platformOperatorRepository.findByEmail("op@ember.local"))
                .thenReturn(Optional.of(PlatformOperator.builder()
                        .id(UUID.randomUUID()).email("op@ember.local").build()));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(restaurant));
        when(restaurantService.updatePlan(restaurantId, RestaurantPlan.PRO))
                .thenReturn(Restaurant.builder()
                        .id(restaurantId).name("Acme").slug("acme").plan(RestaurantPlan.PRO).build());

        PlatformRestaurantSummaryResponse response =
                platformRestaurantService.updatePlan(restaurantId, RestaurantPlan.PRO, "op@ember.local");

        assertThat(response.getPlan()).isEqualTo(RestaurantPlan.PRO);
    }

    @Test
    void updatePlan_throwsWhenRestaurantNotFound() {
        UUID restaurantId = UUID.randomUUID();
        when(platformOperatorRepository.findByEmail("op@ember.local"))
                .thenReturn(Optional.of(PlatformOperator.builder()
                        .id(UUID.randomUUID()).email("op@ember.local").build()));
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                platformRestaurantService.updatePlan(restaurantId, RestaurantPlan.PRO, "op@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: FAIL — `updatePlan` doesn't exist on `PlatformRestaurantService` yet.

- [ ] **Step 3: Create `PlatformRestaurantPlanUpdateRequest`**

```java
// backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantPlanUpdateRequest.java
package com.vanter.ember.platform.model.dto;

import com.vanter.ember.restaurant.model.RestaurantPlan;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PlatformRestaurantPlanUpdateRequest {

    @NotNull(message = "Plan is required")
    private RestaurantPlan plan;
}
```

- [ ] **Step 4: Add `updatePlan` to `PlatformRestaurantService`**

Insert this method right after `updateStatus` (mirrors it exactly):

```java
    @Transactional
    public PlatformRestaurantSummaryResponse updatePlan(UUID restaurantId, RestaurantPlan newPlan,
                                                          String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));

        RestaurantPlan oldPlan = restaurant.getPlan();

        Restaurant updated = restaurantService.updatePlan(restaurantId, newPlan);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_PLAN_UPDATED")
                .oldValue(oldPlan.name())
                .newValue(newPlan.name())
                .build());

        return PlatformRestaurantSummaryResponse.from(updated);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: PASS, all green.

- [ ] **Step 6: Write the failing controller tests**

Add to `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`:

```java
    @Test
    void updatePlan_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(patch("/platform/restaurants/" + UUID.randomUUID() + "/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatePlan_returns200WithUpdatedSummary() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.updatePlan(eq(id), eq(RestaurantPlan.PRO), eq(OPERATOR_EMAIL)))
                .thenReturn(PlatformRestaurantSummaryResponse.builder()
                        .id(id).name("Acme").slug("acme").plan(RestaurantPlan.PRO)
                        .status(RestaurantStatus.ACTIVE).createdAt(Instant.now()).build());

        mockMvc.perform(patch("/platform/restaurants/" + id + "/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":\"PRO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"));
    }

    @Test
    void updatePlan_returns400OnMissingPlan() throws Exception {
        authenticate();

        mockMvc.perform(patch("/platform/restaurants/" + UUID.randomUUID() + "/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plan\":null}"))
                .andExpect(status().isBadRequest());
    }
```

(Uses `PlatformRestaurantSummaryResponse`'s existing `.builder()` shape — check it against how the file's other tests already construct one, e.g. inside `create_returns200WithCreatedSummary`, and match that exact field list rather than the one shown here if it differs.)

- [ ] **Step 7: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantControllerTest`
Expected: FAIL — no `PATCH /{id}/plan` mapping yet (404).

- [ ] **Step 8: Add the endpoint to `PlatformRestaurantController`**

Add `import com.vanter.ember.platform.model.dto.PlatformRestaurantPlanUpdateRequest;` and `import com.vanter.ember.restaurant.model.RestaurantPlan;`, then insert this after `updateStatus`:

```java
    @Operation(summary = "Update a tenant's subscription plan, audited")
    @PatchMapping("/{id}/plan")
    public ResponseEntity<PlatformRestaurantSummaryResponse> updatePlan(
            @PathVariable UUID id,
            @Valid @RequestBody PlatformRestaurantPlanUpdateRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(
                platformRestaurantService.updatePlan(id, request.getPlan(), authentication.getName()));
    }
```

- [ ] **Step 9: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantControllerTest`
Expected: PASS, all green.

- [ ] **Step 10: Frontend — add `updatePlan` and wire the Console detail page**

In `frontend/src/lib/platformApi.ts`, add this to the `platformRestaurantService` object (after `updateStatus`):

```ts
  updatePlan: async (
    id: string,
    plan: PlatformRestaurantDetail['plan']
  ): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.patch<PlatformRestaurantSummary>(
      `/platform/restaurants/${id}/plan`,
      { plan }
    )
    return data
  },
```

In `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`:
- Add `import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'`.
- Add this mutation alongside `toggleStatus` (same shape):

```tsx
  const changePlan = useMutation({
    mutationFn: (plan: PlatformRestaurantDetail['plan']) =>
      platformRestaurantService.updatePlan(id!, plan),
    onSuccess: invalidateAll,
  })
```

- Replace the read-only plan block (currently):

```tsx
          <div>
            <div className="text-zinc-500">Plan</div>
            <div className="font-medium text-zinc-800">{restaurant.plan}</div>
          </div>
```

with:

```tsx
          <div>
            <div className="text-zinc-500">Plan</div>
            <Select
              value={restaurant.plan}
              onValueChange={(value) => changePlan.mutate(value as PlatformRestaurantDetail['plan'])}
            >
              <SelectTrigger className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="FREE">Free</SelectItem>
                <SelectItem value="STARTER">Starter</SelectItem>
                <SelectItem value="PRO">Pro</SelectItem>
                <SelectItem value="ENTERPRISE">Enterprise</SelectItem>
              </SelectContent>
            </Select>
          </div>
```

- [ ] **Step 11: Run the frontend build**

Run: `cd frontend && pnpm run build`
Expected: clean.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantPlanUpdateRequest.java \
        backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java \
        backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java \
        backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java \
        backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java \
        frontend/src/lib/platformApi.ts \
        frontend/src/pages/console/ConsoleRestaurantDetail.tsx
git commit -m "feat(platform): let the Console change a tenant's plan, audited"
```

---

## Task 10: Remove the tenant self-service plan endpoint; regenerate frontend types

Comes last because Tasks 8-9 already give the Console a full replacement path — there is no window where plan changes are impossible.

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/controller/RestaurantAdminController.java`
- Delete: `backend/src/main/java/com/vanter/ember/restaurant/model/dto/UpdateRestaurantPlanRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- Modify: `backend/src/test/java/com/vanter/ember/restaurant/controller/RestaurantAdminControllerTest.java`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/backend-types.ts` (regenerated)

**Interfaces:**
- Removes: `RestaurantAdminController.updatePlan`, `UpdateRestaurantPlanRequest`, `restaurantAdminService.updatePlan`. `RestaurantAdminController.get()` and `restaurantAdminService.getPlan` are kept, untouched.

- [ ] **Step 1: Delete the backend endpoint and its DTO**

In `backend/src/main/java/com/vanter/ember/restaurant/controller/RestaurantAdminController.java`, delete the `updatePlan` method and its now-unused imports (`UpdateRestaurantPlanRequest`, `PatchMapping`, `Valid`, `RequestBody` — check each is not used by anything else in the file before removing the import line). The file should end up as just:

```java
package com.vanter.ember.restaurant.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/restaurant")
@RequiredArgsConstructor
public class RestaurantAdminController {
    private final RestaurantService restaurantService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Restaurant get() {
        return restaurantService.getCurrent(TenantContextHolder.requireTenantId());
    }
}
```

Delete `backend/src/main/java/com/vanter/ember/restaurant/model/dto/UpdateRestaurantPlanRequest.java` entirely.

- [ ] **Step 2: Update `RestaurantAdminControllerTest`**

Delete these 3 test methods from `backend/src/test/java/com/vanter/ember/restaurant/controller/RestaurantAdminControllerTest.java`: `updatePlan_adminCanUpgradePlan`, `updatePlan_forbiddenForNonAdmin`, `updatePlan_returns400ForNullPlan`. Remove the now-unused `import com.vanter.ember.restaurant.model.dto.UpdateRestaurantPlanRequest;` and the `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;` line (confirm `patch` isn't used elsewhere in the file first — it shouldn't be, `get` is the only remaining endpoint). Keep `get_returnsCurrentTenantPlanAndStatus`, `get_forbiddenForNonAdmin`, `get_unauthenticatedReturns401` untouched.

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RestaurantAdminControllerTest`
Expected: PASS, 3/3 (the 3 kept tests; deleting the other 3 isn't a red/green cycle — there's no new behavior here, only removal).

- [ ] **Step 4: Update `RestaurantService`'s stale doc comment**

In `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`, replace the `updatePlan` method's doc comment:

```java
    /**
     * Applies a plan change. Only reachable via the platform-operator path
     * ({@link com.vanter.ember.platform.service.PlatformRestaurantService#updatePlan}) — the
     * tenant-facing self-service endpoint was removed once plan gating had real consequences.
     */
    public Restaurant updatePlan(UUID restaurantId, RestaurantPlan plan) {
        Restaurant restaurant = getCurrent(restaurantId);
        restaurant.setPlan(plan);
        return restaurantRepository.save(restaurant);
    }
```

(No test change needed — `RestaurantServiceTest.updatePlan_savesNewPlanOnCurrentRestaurant` still calls the method directly and is unaffected by who calls it.)

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, full suite green (this is the point where every prior task's tests run together for the first time).

- [ ] **Step 6: Remove the frontend self-service mutation**

In `frontend/src/lib/api.ts`, delete the `updatePlan` method from `restaurantAdminService` (keep `getPlan`):

```ts
export const restaurantAdminService = {
  getPlan: async (): Promise<RestaurantResponse> => {
    const { data } = await api.get<RestaurantResponse>('/admin/restaurant')
    return data
  },
}
```

Remove the now-unused `export type UpdateRestaurantPlanRequest = components['schemas']['UpdateRestaurantPlanRequest']` (line 101) — confirm nothing else in the file references `UpdateRestaurantPlanRequest` first.

- [ ] **Step 7: Regenerate `backend-types.ts`**

This requires a running backend (it hits `/v1/v3/api-docs`). In one terminal: `cd backend && ./mvnw spring-boot:run` (wait for it to finish starting). In another: `cd frontend && pnpm run openapi`. Stop the backend afterward.

If a live backend isn't available in this environment, hand-patch instead (same fallback this repo already used for the print-agent work, per `PROGRESS.md`): remove the `UpdateUserRoleRequest`... — no, specifically remove any `UpdateRestaurantPlanRequest` schema block and its `/admin/restaurant/plan` path entry from `frontend/src/lib/backend-types.ts`, and add the new `PlatformRestaurantPlanUpdateRequest` schema (`{ plan: "FREE" | "STARTER" | "PRO" | "ENTERPRISE" }`) plus the `/platform/restaurants/{id}/plan` path entry, matching the shape of the neighboring `/platform/restaurants/{id}/status` entry already in the file.

- [ ] **Step 8: Run the frontend build and full test suite**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm vitest run`
Expected: build clean, 0 lint errors, full suite green.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/restaurant/controller/RestaurantAdminController.java \
        backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java \
        backend/src/test/java/com/vanter/ember/restaurant/controller/RestaurantAdminControllerTest.java \
        frontend/src/lib/api.ts frontend/src/lib/backend-types.ts
git status
```

(Confirm `UpdateRestaurantPlanRequest.java`'s deletion shows as `D` in `git status`, then add it explicitly since a bare `git add <dir>` misses deletions outside the paths listed above: `git add backend/src/main/java/com/vanter/ember/restaurant/model/dto/UpdateRestaurantPlanRequest.java`.)

```bash
git commit -m "feat(restaurant): remove unpaid tenant self-service plan-change endpoint"
```

---

## Final Verification (after Task 10)

- [ ] `cd backend && ./mvnw test` — full suite green.
- [ ] `cd frontend && pnpm run build && pnpm run lint && pnpm vitest run` — build clean, 0 lint errors, full suite green.
- [ ] Manually smoke-test in a browser once, per the spec's own testing note: create a FREE tenant from the Console, confirm it can't open a cash shift / can't create a KITCHEN user / gets capped at 1 table / can't save branding / can't export / analytics is stuck on day view — then bump it to STARTER from the Console and confirm each unblocks except export (PRO+ only).
