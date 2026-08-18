# Customer Home Loyalty Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `/customer/home`'s avatar+join-table-only card with a loyalty dashboard (points, tier, visit history with amount paid) for any customer who has already joined a table somewhere, while leaving today's card completely unchanged for a customer who never has (`EMB-CLH` backlog).

**Architecture:** Two backend additions to the existing `loyalty` module — persist the amount a customer paid on each ledger row (new nullable `amount` column on `LoyaltyTransaction`, threaded through `LoyaltyAccountService.credit` and `LoyaltyAccrualListener`), and a new tenant-nullable-safe read endpoint `GET /loyalty/accounts/me/visits` that Home uses both to fetch visit history and to safely detect "has this customer ever joined a table" without ever calling the existing (tenant-`requireTenantId()`-strict) `GET /loyalty/accounts/me` from an unscoped context. `Home.tsx` branches on that query's success: 404 keeps today's card verbatim, success renders the dashboard fed by both endpoints.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / Hibernate (`@TenantId` discriminator multi-tenancy) / Flyway / PostgreSQL — React 19 / TypeScript / TanStack Query 5 / Zustand 5 / shadcn (radix-nova style) / Tailwind CSS 4.

**Spec:** `docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md`

## Global Constraints

- `LoyaltyAccountService.credit`'s signature grows from `(LoyaltyAccount, int, String, Long)` to `(LoyaltyAccount, int, String, Long, BigDecimal)`. Every existing caller and every existing test that verifies a call to it must be updated in the same task (Task 1) — do not leave the old 4-arg call sites anywhere.
- `GET /loyalty/accounts/me` (existing, used by `Bill.tsx`) is NOT modified by this plan — its contract, behavior, and tests stay exactly as they are. The new tenant-nullable-safe behavior lives entirely in the new `GET /loyalty/accounts/me/visits` endpoint.
- No `/api` prefix — matches every other real route in this codebase (`/loyalty/...`, no `/api/loyalty/...`).
- No new component test file for `Home.tsx` — no other customer-facing page in this codebase (`Bill.tsx`, `Menu.tsx`, `ComandaView.tsx`) has one; the two existing frontend test files (`Button.smoke.test.tsx`, `format.test.ts`) are the only precedent and neither is a page test. Verify Task 4 via `pnpm run build` (type-check) plus a manual walkthrough, matching how every other customer page in this codebase has shipped.
- Tasks are strictly sequential: Task 2 needs Task 1's `LoyaltyTransaction.amount` field; Task 3 needs Task 2's endpoint to exist on a **running** backend (its first step boots the backend locally to regenerate `backend-types.ts` against it); Task 4 needs Task 3's generated types and `loyaltyAccountService.visits`.
- Every task below is also one task in the `ember/CLAUDE.md` sense: after the code/test steps, write `/reports/<NNN>-task-EMB-CLH-0X-<slug>.md` (next number is 155 — `reports/` currently ends at 154), update `PROGRESS.md`'s three sections, then make exactly one squashed commit (Conventional Commits, no `Co-authored-by`/AI signature, scoped `git add` — never `-A`/`.`). Verify with `cd backend && ./mvnw test` (backend tasks) or `cd frontend && pnpm run build` (frontend tasks) before committing.

---

### Task 1: EMB-CLH-01 — Persist amount paid on the loyalty ledger

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__loyalty_transaction_amount.sql`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTransaction.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java`
- Modify: `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java`

**Interfaces:**
- Consumes: existing `LoyaltyAccount`/`LoyaltyTransaction` entities, existing `BillSplit.getAmount(): BigDecimal` (`backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java:48`).
- Produces: `LoyaltyTransaction.getAmount()/setAmount(BigDecimal)`; `LoyaltyAccountService.credit(LoyaltyAccount account, int points, String reason, Long billId, BigDecimal amount): void` (new signature, replaces the old 4-arg one everywhere).

- [ ] **Step 1: Write the failing tests**

In `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`, add the import block and a new mock field, then a new test:

```java
import com.vanter.ember.loyalty.model.LoyaltyTransaction;
import com.vanter.ember.loyalty.repository.LoyaltyTransactionRepository;
import java.math.BigDecimal;
```

Add alongside the existing `@Mock LoyaltyAccountRepository loyaltyAccountRepository;`:

```java
    @Mock LoyaltyTransactionRepository loyaltyTransactionRepository;
```

Add this test to the class body:

```java
    @Test
    void credit_persistsAmountOnTheLedgerRow() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(1L).userId(USER_ID).totalPoints(10).build();

        loyaltyAccountService.credit(account, 5, "BILL_SETTLED", 42L, new BigDecimal("30.00"));

        assertThat(account.getTotalPoints()).isEqualTo(15);
        verify(loyaltyAccountRepository).save(account);
        ArgumentCaptor<LoyaltyTransaction> captor = ArgumentCaptor.forClass(LoyaltyTransaction.class);
        verify(loyaltyTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("30.00");
        assertThat(captor.getValue().getBillId()).isEqualTo(42L);
        assertThat(captor.getValue().getPoints()).isEqualTo(5);
        assertThat(captor.getValue().getReason()).isEqualTo("BILL_SETTLED");
    }
```

In `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java`, update the two existing assertions that call `credit` to include the new argument — replace:

```java
        verify(loyaltyAccountService).credit(eq(aliceAccount), eq(30), eq("BILL_SETTLED"), eq(BILL_ID));
        verify(loyaltyAccountService).credit(eq(bobAccount), eq(20), eq("BILL_SETTLED"), eq(BILL_ID));
```

with:

```java
        verify(loyaltyAccountService).credit(eq(aliceAccount), eq(30), eq("BILL_SETTLED"), eq(BILL_ID), eq(aliceSplit.getAmount()));
        verify(loyaltyAccountService).credit(eq(bobAccount), eq(20), eq("BILL_SETTLED"), eq(BILL_ID), eq(bobSplit.getAmount()));
```

and replace:

```java
        verify(loyaltyAccountService, never()).credit(any(), anyInt(), any(), any());
```

with:

```java
        verify(loyaltyAccountService, never()).credit(any(), anyInt(), any(), any(), any());
```

- [ ] **Step 2: Run tests to verify they fail to compile**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountServiceTest,LoyaltyAccrualListenerTest`
Expected: FAIL to compile — `credit` doesn't accept a 5th argument yet, `LoyaltyTransactionRepository` mock field is unused-but-fine, `LoyaltyTransaction` import is unused-but-fine (both become used once Step 3–5 land).

- [ ] **Step 3: Write the migration**

```sql
-- Persists the amount the customer personally paid for this visit (from
-- BillSplit.amount at accrual time) — needed to show payment history on
-- the customer Home loyalty dashboard (see
-- docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md).
-- Nullable: pre-existing rows from before this migration have no
-- recoverable amount.
ALTER TABLE loyalty_transactions ADD COLUMN IF NOT EXISTS amount numeric(10,2);
```

Save as `backend/src/main/resources/db/migration/V10__loyalty_transaction_amount.sql`.

- [ ] **Step 4: Add the `amount` field to `LoyaltyTransaction`**

In `backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTransaction.java`, add `import java.math.BigDecimal;` to the imports, and add this field after `billId`:

```java
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
```

- [ ] **Step 5: Update `LoyaltyAccountService.credit`**

In `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`, add `import java.math.BigDecimal;`, then replace:

```java
    @Transactional
    public void credit(LoyaltyAccount account, int points, String reason, Long billId) {
        account.setTotalPoints(account.getTotalPoints() + points);
        loyaltyAccountRepository.save(account);
        loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .points(points)
                .reason(reason)
                .billId(billId)
                .createdAt(LocalDateTime.now())
                .build());
    }
```

with:

```java
    @Transactional
    public void credit(LoyaltyAccount account, int points, String reason, Long billId, BigDecimal amount) {
        account.setTotalPoints(account.getTotalPoints() + points);
        loyaltyAccountRepository.save(account);
        loyaltyTransactionRepository.save(LoyaltyTransaction.builder()
                .loyaltyAccount(account)
                .points(points)
                .reason(reason)
                .billId(billId)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build());
    }
```

- [ ] **Step 6: Update `LoyaltyAccrualListener.accrue` to pass the amount**

In `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java`, replace:

```java
    private void accrue(
            UUID tenantId,
            String userId,
            BillSplit split,
            SettingsPayload.LoyaltySettings settings,
            Long billId) {
        int points = loyaltyService.computeAccrualPoints(split.getAmount(), settings);
        LoyaltyAccount account = loyaltyAccountService.findOrCreate(tenantId, userId);
        loyaltyAccountService.credit(account, points, REASON_BILL_SETTLED, billId);
    }
```

with:

```java
    private void accrue(
            UUID tenantId,
            String userId,
            BillSplit split,
            SettingsPayload.LoyaltySettings settings,
            Long billId) {
        int points = loyaltyService.computeAccrualPoints(split.getAmount(), settings);
        LoyaltyAccount account = loyaltyAccountService.findOrCreate(tenantId, userId);
        loyaltyAccountService.credit(account, points, REASON_BILL_SETTLED, billId, split.getAmount());
    }
```

- [ ] **Step 7: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green, including the updated `LoyaltyAccountServiceTest` and `LoyaltyAccrualListenerTest`.

- [ ] **Step 8: Report, update PROGRESS.md, and commit**

Write `reports/155-task-EMB-CLH-01-loyalty-transaction-amount.md` per the CLAUDE.md report structure (Identification/Objective/Modified Files/What Changed/Why It Changed). Update `PROGRESS.md`'s three sections (mark EMB-CLH-01 done, set Current Active Task to EMB-CLH-02).

```bash
git add backend/src/main/resources/db/migration/V10__loyalty_transaction_amount.sql backend/src/main/java/com/vanter/ember/loyalty/model/LoyaltyTransaction.java backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListener.java backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccrualListenerTest.java PROGRESS.md reports/155-task-EMB-CLH-01-loyalty-transaction-amount.md
git commit -m "feat(backend): persist amount paid on loyalty ledger rows"
```

---

### Task 2: EMB-CLH-02 — `GET /loyalty/accounts/me/visits`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyVisitResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- Modify: `backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyAccountController.java`
- Modify: `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

**Interfaces:**
- Consumes: Task 1's `LoyaltyTransaction.amount`; existing `LoyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(Long): List<LoyaltyTransaction>` (`backend/src/main/java/com/vanter/ember/loyalty/repository/LoyaltyTransactionRepository.java:9`); existing `TenantContextHolder.getTenantId(): UUID` (nullable getter, `backend/src/main/java/com/vanter/ember/config/TenantContextHolder.java:20`).
- Produces: `LoyaltyVisitResponse(LocalDateTime visitedAt, BigDecimal amountPaid, int pointsEarned)`; `LoyaltyAccountService.getMyVisits(UUID tenantId, String userId): List<LoyaltyVisitResponse>`; endpoint `GET /loyalty/accounts/me/visits` (CUSTOMER role) — 404 (via existing `ResourceNotFoundException`) when no tenant is bound, 200 with a possibly-empty list (capped at 20, newest first) otherwise.

- [ ] **Step 1: Write the failing service tests**

In `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`, add imports:

```java
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

(Note: `Optional`, `when`, `assertThat`, and `LoyaltyTransaction` (added by Task 1) are already imported in this file by this point — only add what's listed above.)

Add these two tests:

```java
    @Test
    void getMyVisits_returnsMostRecentTwentyNewestFirst() {
        LoyaltyAccount account = LoyaltyAccount.builder().id(7L).userId(USER_ID).totalPoints(500).build();
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.of(account));

        LocalDateTime newest = LocalDateTime.of(2026, 8, 17, 12, 0);
        List<LoyaltyTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            transactions.add(LoyaltyTransaction.builder()
                    .id((long) i)
                    .loyaltyAccount(account)
                    .points(10)
                    .reason("BILL_SETTLED")
                    .billId((long) i)
                    .amount(new BigDecimal("15.00"))
                    .createdAt(newest.minusDays(i))
                    .build());
        }
        when(loyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc(7L))
                .thenReturn(transactions);

        List<LoyaltyVisitResponse> visits = loyaltyAccountService.getMyVisits(TENANT_ID, USER_ID);

        assertThat(visits).hasSize(20);
        assertThat(visits.get(0).visitedAt()).isEqualTo(newest);
        assertThat(visits.get(0).amountPaid()).isEqualByComparingTo("15.00");
        assertThat(visits.get(0).pointsEarned()).isEqualTo(10);
    }

    @Test
    void getMyVisits_noAccountForTenant_throwsResourceNotFound() {
        when(loyaltyAccountRepository.findByTenantIdAndUserId(TENANT_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> loyaltyAccountService.getMyVisits(TENANT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 2: Run tests to verify they fail to compile**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountServiceTest`
Expected: FAIL to compile — `getMyVisits` and `LoyaltyVisitResponse` don't exist yet.

- [ ] **Step 3: Create `LoyaltyVisitResponse`**

```java
package com.vanter.ember.loyalty.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LoyaltyVisitResponse(LocalDateTime visitedAt, BigDecimal amountPaid, int pointsEarned) {}
```

Save as `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyVisitResponse.java`.

- [ ] **Step 4: Add `getMyVisits` to `LoyaltyAccountService`**

In `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`, add `import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;`, then add this method after `getMyAccount`:

```java
    /**
     * Caller's most recent visits (up to 20, newest first) for {@code
     * /loyalty/accounts/me/visits} — 404s the same way {@link #getMyAccount} does if the
     * customer has never joined a table at this tenant.
     */
    public List<LoyaltyVisitResponse> getMyVisits(UUID tenantId, String userId) {
        LoyaltyAccount account = loyaltyAccountRepository.findByTenantIdAndUserId(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No loyalty account for this restaurant yet"));

        return loyaltyTransactionRepository
                .findByLoyaltyAccountIdOrderByCreatedAtDesc(account.getId())
                .stream()
                .limit(20)
                .map(tx -> new LoyaltyVisitResponse(tx.getCreatedAt(), tx.getAmount(), tx.getPoints()))
                .toList();
    }
```

- [ ] **Step 5: Run the service tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=LoyaltyAccountServiceTest`
Expected: PASS.

- [ ] **Step 6: Add the controller endpoint**

In `backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyAccountController.java`, add `import java.util.List;`, then add this method after `me`:

```java
    @Operation(summary = "Caller's most recent visits for the current tenant (CUSTOMER); "
            + "404 if no tenant is bound (customer has never joined a table anywhere)")
    @GetMapping("/me/visits")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<LoyaltyVisitResponse> myVisits(Authentication authentication) {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new ResourceNotFoundException("No tenant context — join a table first");
        }
        return loyaltyAccountService.getMyVisits(tenantId, resolveUserId(authentication));
    }
```

Add `import com.vanter.ember.loyalty.dto.LoyaltyVisitResponse;` and `import java.util.UUID;` to the file's imports (the file does not currently import `UUID` directly — it only uses `TenantContextHolder.requireTenantId()` inline).

- [ ] **Step 7: Add the `SecurityAuditTest` row**

In `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`, replace the last line of the `@CsvSource` block:

```java
        "GET,  /loyalty/accounts/me"
    })
```

with:

```java
        "GET,  /loyalty/accounts/me",
        "GET,  /loyalty/accounts/me/visits"
    })
```

- [ ] **Step 8: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green, including `SecurityAuditTest`.

- [ ] **Step 9: Report, update PROGRESS.md, and commit**

Write `reports/156-task-EMB-CLH-02-loyalty-visits-endpoint.md` per the CLAUDE.md report structure. Update `PROGRESS.md`'s three sections (mark EMB-CLH-02 done, set Current Active Task to EMB-CLH-03).

```bash
git add backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyVisitResponse.java backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyAccountController.java backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java PROGRESS.md reports/156-task-EMB-CLH-02-loyalty-visits-endpoint.md
git commit -m "feat(backend): add GET /loyalty/accounts/me/visits"
```

---

### Task 3: EMB-CLH-03 — Frontend data layer (openapi regen + `api.ts`)

**Files:**
- Modify: `frontend/src/lib/backend-types.ts` (regenerated — do not hand-edit)
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: Task 2's finished `GET /loyalty/accounts/me/visits` endpoint and `LoyaltyVisitResponse` schema (regenerated into `backend-types.ts` in Step 1).
- Produces: `export type LoyaltyVisitResponse = components['schemas']['LoyaltyVisitResponse']`; `loyaltyAccountService.visits(): Promise<LoyaltyVisitResponse[]>`.

- [ ] **Step 1: Regenerate `backend-types.ts` against the finished backend**

Run (in one terminal): `cd backend && ./mvnw spring-boot:run` — wait for it to finish booting (watch for `Started EmberApplication`).
Run (in a second terminal): `cd frontend && pnpm run openapi`
Expected: `frontend/src/lib/backend-types.ts` is rewritten and now contains a `LoyaltyVisitResponse` schema and a `/loyalty/accounts/me/visits` path entry. Stop the backend (`Ctrl+C` in the first terminal) once the regen finishes.

- [ ] **Step 2: Add the type alias and service method to `api.ts`**

In `frontend/src/lib/api.ts`, replace:

```ts
export type RewardCatalogEntry = components['schemas']['RewardCatalogEntryResponse']
export type LoyaltyAccountResponse = components['schemas']['LoyaltyAccountResponse']

export const loyaltyAccountService = {
  me: async (): Promise<LoyaltyAccountResponse> => {
    const { data } = await api.get<LoyaltyAccountResponse>('/loyalty/accounts/me')
    return data
  },
}
```

with:

```ts
export type RewardCatalogEntry = components['schemas']['RewardCatalogEntryResponse']
export type LoyaltyAccountResponse = components['schemas']['LoyaltyAccountResponse']
export type LoyaltyVisitResponse = components['schemas']['LoyaltyVisitResponse']

export const loyaltyAccountService = {
  me: async (): Promise<LoyaltyAccountResponse> => {
    const { data } = await api.get<LoyaltyAccountResponse>('/loyalty/accounts/me')
    return data
  },
  visits: async (): Promise<LoyaltyVisitResponse[]> => {
    const { data } = await api.get<LoyaltyVisitResponse[]>('/loyalty/accounts/me/visits')
    return data
  },
}
```

- [ ] **Step 3: Type-check**

Run: `cd frontend && pnpm run build`
Expected: PASS — `tsc -b` succeeds (no consumers of `loyaltyAccountService.visits` exist yet, so this only verifies the new type/method compile cleanly), `vite build` succeeds.

- [ ] **Step 4: Report, update PROGRESS.md, and commit**

Write `reports/157-task-EMB-CLH-03-loyalty-visits-frontend-types.md` per the CLAUDE.md report structure. Update `PROGRESS.md`'s three sections (mark EMB-CLH-03 done, set Current Active Task to EMB-CLH-04).

```bash
git add frontend/src/lib/backend-types.ts frontend/src/lib/api.ts PROGRESS.md reports/157-task-EMB-CLH-03-loyalty-visits-frontend-types.md
git commit -m "feat(frontend): add loyalty visits service and generated types"
```

---

### Task 4: EMB-CLH-04 — `Home.tsx` loyalty dashboard

**Files:**
- Modify: `frontend/src/pages/customer/Home.tsx`

**Interfaces:**
- Consumes: Task 3's `loyaltyAccountService.visits`/`loyaltyAccountService.me`; existing `loyaltyAccountService.me` response fields (`totalPoints`, `tier`, `nextTier`, `pointsToNextTier` — same shape already consumed by `frontend/src/pages/customer/Bill.tsx:22-27,103-119`); existing `TIER_LABELS`/`TIER_BADGE_CLASSNAMES` (`frontend/src/pages/admin/components/settings/loyalty/types.ts`); existing `formatCurrency` (`frontend/src/lib/format.ts`); existing `useUIStore().openModal('JOIN_TABLE')` + `JoinTableModal` (unchanged).
- Produces: no new exports — this is a leaf page component.

- [ ] **Step 1: Replace `Home.tsx`**

Replace the full contents of `frontend/src/pages/customer/Home.tsx` with:

```tsx
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Utensils, Sparkles, History } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/store/authStore'
import { JoinTableModal } from './components/JoinTableModal'
import { useUIStore } from '@/store/uiStore'
import { loyaltyAccountService } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { TIER_LABELS, TIER_BADGE_CLASSNAMES } from '@/pages/admin/components/settings/loyalty/types'
import type { MouseEvent } from 'react'

const formatVisitDate = (isoDateTime: string) =>
  new Date(isoDateTime).toLocaleDateString('es-ES', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

export const Home = () => {
  const { name } = useAuthStore()
  const { openModal } = useUIStore()

  const openJoinModal = (e: MouseEvent) => {
    openModal('JOIN_TABLE')
    e.preventDefault()
    e.stopPropagation()
  }

  const {
    data: visits,
    isSuccess: hasTenant,
    isLoading: visitsLoading,
  } = useQuery({
    queryKey: ['loyaltyVisits', 'me'],
    queryFn: loyaltyAccountService.visits,
    retry: false,
  })

  const { data: loyaltyAccount } = useQuery({
    queryKey: ['loyaltyAccount', 'me'],
    queryFn: loyaltyAccountService.me,
    enabled: hasTenant,
    retry: false,
  })

  const showDashboard = hasTenant && !visitsLoading
  const lastVisitDate = visits && visits.length > 0 ? visits[0].visitedAt : undefined

  if (!showDashboard) {
    return (
      <>
        <Card className="w-full border-none shadow-sm bg-white rounded-2xl">
          <CardContent className="flex flex-col gap-6 md:flex-row items-center justify-evenly p-6 ">
            <div className="flex items-center gap-5 w-full md:w-auto">
              <div className="relative">
                <Avatar className="h-40 w-40 border-2 border-gray-100">
                  <AvatarImage
                    src="https://i.pravatar.cc/150?u=alejandra"
                    alt="Alejandra"
                  />
                  <AvatarFallback>AG</AvatarFallback>
                </Avatar>
              </div>
              <div className="flex flex-col">
                <h2 className="text-2xl font-bold text-gray-900">{name}</h2>
                <p className="text-sm text-gray-500 mt-1">
                  Amante de la gastronomia y mas cosas.
                </p>
              </div>
            </div>
            <div className="w-full md:w-auto flex justify-end">
              <Button
                className="w-full h-20  rounded-3xl md:w-auto hover:bg-[#660000] px-8 py-6 text-xl font-semibold transition-colors"
                onClick={openJoinModal}
              >
                <Utensils className="mr-2 h-5 w-5" />
                Entrar a una mesa.
              </Button>
            </div>
          </CardContent>
        </Card>
        <JoinTableModal />
      </>
    )
  }

  return (
    <>
      <div className="flex flex-col gap-4">
        <Card className="w-full border-none shadow-sm bg-white rounded-2xl">
          <CardContent className="flex items-center justify-between gap-4 p-6">
            <div className="flex items-center gap-4">
              <Avatar className="h-16 w-16 border-2 border-gray-100">
                <AvatarImage
                  src="https://i.pravatar.cc/150?u=alejandra"
                  alt="Alejandra"
                />
                <AvatarFallback>AG</AvatarFallback>
              </Avatar>
              <div className="flex flex-col">
                <h2 className="text-xl font-bold text-gray-900">{name}</h2>
                <p className="text-sm text-gray-500">Bienvenido de vuelta.</p>
              </div>
            </div>
            <Button variant="outline" className="rounded-2xl" onClick={openJoinModal}>
              <Utensils className="mr-2 h-4 w-4" />
              Entrar a una mesa
            </Button>
          </CardContent>
        </Card>

        {loyaltyAccount && (
          <Card className="bg-[#8c1717]/5 border-2 border-[#8c1717]/20 rounded-2xl">
            <CardContent className="py-5 flex items-center gap-3">
              <Sparkles className="w-8 h-8 text-[#8c1717] shrink-0" />
              <div className="flex flex-col gap-1">
                <div className="flex items-center gap-2">
                  <span className="font-semibold">{loyaltyAccount.totalPoints} pts</span>
                  <Badge className={TIER_BADGE_CLASSNAMES[loyaltyAccount.tier!]}>
                    {TIER_LABELS[loyaltyAccount.tier!]}
                  </Badge>
                </div>
                {loyaltyAccount.nextTier && (
                  <span className="text-sm text-gray-500">
                    {loyaltyAccount.pointsToNextTier} pts para {TIER_LABELS[loyaltyAccount.nextTier]}
                  </span>
                )}
                {lastVisitDate && (
                  <span className="text-xs text-gray-400">
                    Última visita: {formatVisitDate(lastVisitDate)}
                  </span>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        <Card className="border-none shadow-sm bg-white rounded-2xl">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-lg">
              <History className="w-5 h-5 text-[#8c1717]" />
              Tus visitas
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            {!visits || visits.length === 0 ? (
              <p className="text-sm text-gray-400 text-center py-6">
                Aún no tienes visitas registradas.
              </p>
            ) : (
              visits.map((visit, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between p-4 rounded-2xl bg-gray-50"
                >
                  <span className="text-sm text-gray-600">
                    {visit.visitedAt ? formatVisitDate(visit.visitedAt) : '—'}
                  </span>
                  <span className="font-semibold">
                    {visit.amountPaid != null ? formatCurrency(visit.amountPaid) : '—'}
                  </span>
                  <span className="text-sm text-[#8c1717] font-semibold">
                    +{visit.pointsEarned} pts
                  </span>
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
      <JoinTableModal />
    </>
  )
}
```

- [ ] **Step 2: Type-check and build**

Run: `cd frontend && pnpm run build`
Expected: PASS — `tsc -b` succeeds, `vite build` succeeds.

- [ ] **Step 3: Manual walkthrough**

Run: `cd frontend && pnpm run dev` (and `cd backend && ./mvnw spring-boot:run` if not already running).
Log in as a CUSTOMER who has never joined a table at any tenant → confirm `/customer/home` shows today's unchanged avatar+join-table card (no console errors from the 404).
Join a table, let a bill settle so a `LoyaltyTransaction` exists (or use one that already has settled visits), navigate back to `/customer/home` → confirm the dashboard renders: points, tier badge, "Última visita" line, and the visit list with date/amount/points. Confirm the compact "Entrar a una mesa" button still opens `JoinTableModal`.
Stop the dev server once confirmed.

- [ ] **Step 4: Report, update PROGRESS.md, and commit**

Write `reports/158-task-EMB-CLH-04-home-loyalty-dashboard.md` per the CLAUDE.md report structure. Update `PROGRESS.md`'s three sections (mark EMB-CLH-04 done, note **EMB-CLH backlog is now COMPLETE**, set Current Active Task to none / awaiting next task).

```bash
git add frontend/src/pages/customer/Home.tsx PROGRESS.md reports/158-task-EMB-CLH-04-home-loyalty-dashboard.md
git commit -m "feat(frontend): show loyalty dashboard on customer home"
```
