# Platform Console Redesign (piece D) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the `/console` operator UI on the tenant SaaS app's visual language — a left-sidebar shell, a real dashboard with KPIs and a platform-wide activity feed, and every screen on shadcn primitives — without changing any behaviour shipped in pieces B and C.

**Architecture:** One new backend endpoint (`GET /platform/stats`) feeds the dashboard KPIs; everything else is frontend. `PlatformLayout` becomes a sidebar shell (`ConsoleSidebar`); `ConsoleDashboard` is rebuilt; `ConsoleRestaurants` / `ConsoleRestaurantDetail` / `ConsoleLogin` / `ConsoleRestaurantCreate` / `ConsolePasswordChange` are re-skinned onto `Table` / `Badge` / `Dialog` / `Switch` / `Card`. Shared primitives `HubBadge` and `ConsolePageHeader` live in `frontend/src/components/console/`.

**Tech Stack:** Backend: Java 17, Spring Boot 3.5.14, Spring Data JPA; JUnit 5 + Mockito + `@WebMvcTest` + `@DataJpaTest`. Frontend: React 19, TypeScript, Vite, react-router-dom 7, TanStack Query 5, shadcn/ui (radix-ui), `lucide-react`, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-06-platform-console-redesign-design.md` — read it alongside this plan.

## Global Constraints

- Branch: `spec/platform-console-redesign`, cut from `spec/platform-console-retire-liveness` (PR #81) — it already contains pieces B and C. The spec commit is already on it. One PR.
- Backend test command (per root `CLAUDE.md` §2): `cd backend && ./mvnw test`. Never bare `mvn`/`tsc`.
- Frontend: `cd frontend && pnpm run build` (`tsc -b && vite build`) and `pnpm run lint` must both be clean (16 pre-existing lint warnings in unrelated files are acceptable; **0 errors**, no *new* warnings in console files). Tests: `pnpm run test:run`.
- Commits: Conventional Commits, lowercase, one squashed atomic commit per task. **No** `Co-authored-by:` / `Signed-off-by:` / AI-signature trailers. Stage only the files the task touched (`git add <paths>`), never `git add -A`/`.`.
- Reports: after the final task, one report in `reports/` — next free number is **385**. Sequential naming per `CLAUDE.md` §4.
- **Brand colour: `#8c1717`** everywhere in the console (replace every `#920703`). Primary button, sidebar active state, login title.
- **All user-facing strings in Spanish, inline. No i18n** — do not add `useTranslation` or `locales/console.*`.
- **No behaviour change** to pieces B/C: the delete-only-when-`SUSPENDED` + type-the-slug flow, restore, `?includeDeleted`, the Hub panel data, the license-key download — all preserved. The B/C Vitest files are updated to the new markup with behaviour-level assertions (roles, visible text, service-call spies), never deleted.
- **No change** to `platformAuthStore`, `PlatformProtectedRoute`, the token/login flow (login screen gets visual polish only).
- `@DataJpaTest` gotcha: annotate every `@DataJpaTest` with `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)` — `@DataJpaTest` scans all `@Entity` project-wide and a `@TenantId` entity elsewhere breaks the Hibernate multi-tenant filter otherwise.

---

## File Structure

**Backend — create**
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformStatsResponse.java` — the stats DTO (record with two nested records).
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformStatsService.java` — computes the counts.
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformStatsController.java` — `GET /platform/stats`.
- Tests: `PlatformStatsServiceTest`, `PlatformStatsControllerTest`, `RestaurantRepositoryCountByStatusTest`.

**Backend — modify**
- `restaurant/repository/RestaurantRepository.java` — `long countByStatus(RestaurantStatus)`.

**Frontend — create**
- `frontend/src/components/console/HubBadge.tsx` (+ `HubBadge.test.tsx`)
- `frontend/src/components/console/ConsolePageHeader.tsx`
- `frontend/src/components/console/ConsoleSidebar.tsx`
- `frontend/src/pages/console/ConsoleLogin.test.tsx`

**Frontend — modify**
- `frontend/src/lib/platformApi.ts` — `PlatformStats` type + `platformStatsService`; `platformAuditLogService.getRecent`.
- `frontend/src/layouts/PlatformLayout.tsx` — sidebar shell.
- `frontend/src/pages/console/ConsoleDashboard.tsx` — real dashboard.
- `frontend/src/pages/console/ConsoleRestaurants.tsx` (+ `.test.tsx`) — re-skin, drop local `hubDot`.
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx` (+ `.test.tsx`) — re-skin, `Dialog` confirm.
- `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`, `ConsolePasswordChange.tsx` — `#8c1717`, Spanish, `ConsolePageHeader`.

---

## Task 1: Backend — `GET /platform/stats`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformStatsResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/platform/service/PlatformStatsService.java`
- Create: `backend/src/main/java/com/vanter/ember/platform/controller/PlatformStatsController.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/service/PlatformStatsServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/controller/PlatformStatsControllerTest.java`
- Test: `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositoryCountByStatusTest.java`

**Interfaces:**
- Consumes: `RestaurantStatus.DELETED`, `HubActivation.getLastHeartbeatAt()`, `HubStatus.from(Instant, Instant)` (pieces B/C, already on this branch).
- Produces:
  - `RestaurantRepository#countByStatus(RestaurantStatus): long`
  - `PlatformStatsResponse` — `record PlatformStatsResponse(TenantCounts tenants, HubCounts hubs)` with `record TenantCounts(long active, long suspended, long deleted)` and `record HubCounts(long online, long stale, long offline, long never)`.
  - `PlatformStatsService#get(): PlatformStatsResponse`
  - `GET /platform/stats` → 200 `PlatformStatsResponse`.

- [ ] **Step 1: Add the repository method**

In `RestaurantRepository.java` (imports for `RestaurantStatus` already present from piece B), add:

```java
    long countByStatus(RestaurantStatus status);
```

- [ ] **Step 2: Write the failing `@DataJpaTest`**

Create `RestaurantRepositoryCountByStatusTest.java`:

```java
package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class RestaurantRepositoryCountByStatusTest {

    @Autowired RestaurantRepository restaurantRepository;

    private void persist(String slug, RestaurantStatus status) {
        restaurantRepository.save(Restaurant.builder().name(slug).slug(slug).status(status).build());
    }

    @Test
    void countByStatus_countsOnlyThatStatus() {
        persist("a1", RestaurantStatus.ACTIVE);
        persist("a2", RestaurantStatus.ACTIVE);
        persist("s1", RestaurantStatus.SUSPENDED);
        persist("d1", RestaurantStatus.DELETED);

        assertThat(restaurantRepository.countByStatus(RestaurantStatus.ACTIVE)).isEqualTo(2);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED)).isEqualTo(1);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.DELETED)).isEqualTo(1);
        assertThat(restaurantRepository.countByStatus(RestaurantStatus.INACTIVE)).isZero();
    }
}
```

- [ ] **Step 3: Run it**

Run: `cd backend && ./mvnw test -Dtest=RestaurantRepositoryCountByStatusTest`
Expected: PASS (Spring Data derives `countByStatus` automatically; the test just pins the behaviour).

- [ ] **Step 4: Create `PlatformStatsResponse`**

```java
package com.vanter.ember.platform.model.dto;

/** Console dashboard KPIs: tenant counts by lifecycle status, Hub counts by liveness. */
public record PlatformStatsResponse(TenantCounts tenants, HubCounts hubs) {

    public record TenantCounts(long active, long suspended, long deleted) {}

    public record HubCounts(long online, long stale, long offline, long never) {}
}
```

- [ ] **Step 5: Write the failing `PlatformStatsServiceTest`**

Create `PlatformStatsServiceTest.java`:

```java
package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStatsServiceTest {

    @Mock RestaurantRepository restaurantRepository;
    @Mock HubActivationRepository hubActivationRepository;
    @InjectMocks PlatformStatsService platformStatsService;

    private HubActivation hub(Instant lastBeat) {
        return HubActivation.builder()
                .restaurantId(UUID.randomUUID()).hardwareFingerprint("fp")
                .activatedAt(Instant.now()).lastHeartbeatAt(lastBeat).build();
    }

    @Test
    void get_countsTenantsByStatusAndHubsByLiveness() {
        when(restaurantRepository.countByStatus(RestaurantStatus.ACTIVE)).thenReturn(5L);
        when(restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED)).thenReturn(2L);
        when(restaurantRepository.countByStatus(RestaurantStatus.DELETED)).thenReturn(1L);
        Instant now = Instant.now();
        when(hubActivationRepository.findAll()).thenReturn(List.of(
                hub(now.minus(Duration.ofMinutes(2))),   // online
                hub(now.minus(Duration.ofMinutes(2))),   // online
                hub(now.minus(Duration.ofHours(3))),     // stale
                hub(now.minus(Duration.ofDays(3))),      // offline
                hub(null)));                              // never

        PlatformStatsResponse stats = platformStatsService.get();

        assertThat(stats.tenants().active()).isEqualTo(5);
        assertThat(stats.tenants().suspended()).isEqualTo(2);
        assertThat(stats.tenants().deleted()).isEqualTo(1);
        assertThat(stats.hubs().online()).isEqualTo(2);
        assertThat(stats.hubs().stale()).isEqualTo(1);
        assertThat(stats.hubs().offline()).isEqualTo(1);
        assertThat(stats.hubs().never()).isEqualTo(1);
    }

    @Test
    void get_allZeroesWhenEmpty() {
        when(restaurantRepository.countByStatus(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(hubActivationRepository.findAll()).thenReturn(List.of());

        PlatformStatsResponse stats = platformStatsService.get();

        assertThat(stats.hubs().online()).isZero();
        assertThat(stats.hubs().never()).isZero();
    }
}
```

- [ ] **Step 6: Run it — verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PlatformStatsServiceTest`
Expected: FAIL — `PlatformStatsService` does not exist.

- [ ] **Step 7: Implement `PlatformStatsService`**

```java
package com.vanter.ember.platform.service;

import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.platform.model.dto.HubStatus;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.HubCounts;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.TenantCounts;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read-only KPI aggregation for the operator console dashboard. */
@Service
@RequiredArgsConstructor
public class PlatformStatsService {

    private final RestaurantRepository restaurantRepository;
    private final HubActivationRepository hubActivationRepository;

    public PlatformStatsResponse get() {
        TenantCounts tenants = new TenantCounts(
                restaurantRepository.countByStatus(RestaurantStatus.ACTIVE),
                restaurantRepository.countByStatus(RestaurantStatus.SUSPENDED),
                restaurantRepository.countByStatus(RestaurantStatus.DELETED));

        Instant now = Instant.now();
        long online = 0, stale = 0, offline = 0, never = 0;
        for (HubActivation a : hubActivationRepository.findAll()) {
            switch (HubStatus.from(a.getLastHeartbeatAt(), now)) {
                case ONLINE -> online++;
                case STALE -> stale++;
                case OFFLINE -> offline++;
                case NEVER -> never++;
            }
        }
        return new PlatformStatsResponse(tenants, new HubCounts(online, stale, offline, never));
    }
}
```

- [ ] **Step 8: Run it — verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PlatformStatsServiceTest`
Expected: PASS (2 tests).

- [ ] **Step 9: Create `PlatformStatsController`**

```java
package com.vanter.ember.platform.controller;

import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.service.PlatformStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Platform Stats", description = "Operator dashboard KPIs")
@RestController
@RequestMapping("/platform/stats")
@RequiredArgsConstructor
public class PlatformStatsController {

    private final PlatformStatsService platformStatsService;

    @Operation(summary = "Tenant counts by status + Hub counts by liveness")
    @GetMapping
    public ResponseEntity<PlatformStatsResponse> get() {
        return ResponseEntity.ok(platformStatsService.get());
    }
}
```

- [ ] **Step 10: Write + run the controller slice test**

Create `PlatformStatsControllerTest.java` (mirror `PlatformRestaurantControllerTest`'s auth setup):

```java
package com.vanter.ember.platform.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.platform.config.PlatformSecurityConfig;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.HubCounts;
import com.vanter.ember.platform.model.dto.PlatformStatsResponse.TenantCounts;
import com.vanter.ember.platform.service.PlatformJwtService;
import com.vanter.ember.platform.service.PlatformOperatorDetailsService;
import com.vanter.ember.platform.service.PlatformStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlatformStatsController.class)
@Import({PlatformSecurityConfig.class, CorsConfig.class})
class PlatformStatsControllerTest {

    private static final String OPERATOR_EMAIL = "operator@ember.local";
    private static final String TOKEN = "valid-token";

    @Autowired MockMvc mockMvc;

    @MockBean PlatformStatsService platformStatsService;
    @MockBean PlatformJwtService platformJwtService;
    @MockBean PlatformOperatorDetailsService platformOperatorDetailsService;

    private void authenticate() {
        when(platformJwtService.isTokenValid(TOKEN)).thenReturn(true);
        when(platformJwtService.extractSubject(TOKEN)).thenReturn(OPERATOR_EMAIL);
        UserDetails userDetails = User.builder()
                .username(OPERATOR_EMAIL).password("ignored").roles("PLATFORM_ADMIN").build();
        when(platformOperatorDetailsService.loadUserByUsername(OPERATOR_EMAIL)).thenReturn(userDetails);
    }

    @Test
    void get_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(get("/platform/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void get_returns200WithCounts() throws Exception {
        authenticate();
        when(platformStatsService.get()).thenReturn(new PlatformStatsResponse(
                new TenantCounts(5, 2, 1), new HubCounts(3, 1, 0, 2)));

        mockMvc.perform(get("/platform/stats").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenants.active").value(5))
                .andExpect(jsonPath("$.hubs.never").value(2));
    }
}
```

Run: `cd backend && ./mvnw test -Dtest=PlatformStatsControllerTest`
Expected: PASS (2 tests).

- [ ] **Step 11: Full backend suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 12: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformStatsResponse.java \
  backend/src/main/java/com/vanter/ember/platform/service/PlatformStatsService.java \
  backend/src/main/java/com/vanter/ember/platform/controller/PlatformStatsController.java \
  backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java \
  backend/src/test/java/com/vanter/ember/platform/service/PlatformStatsServiceTest.java \
  backend/src/test/java/com/vanter/ember/platform/controller/PlatformStatsControllerTest.java \
  backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositoryCountByStatusTest.java
git commit -m "feat(platform): GET /platform/stats — tenant + hub KPI counts"
```

---

## Task 2: Frontend API — `platformStatsService` + `getRecent`

**Files:**
- Modify: `frontend/src/lib/platformApi.ts`

**Interfaces:**
- Consumes: the `GET /platform/stats` shape from Task 1; the existing `GET /platform/audit-log`.
- Produces:
  - `interface PlatformStats { tenants: { active: number; suspended: number; deleted: number }; hubs: { online: number; stale: number; offline: number; never: number } }`
  - `platformStatsService.get(): Promise<PlatformStats>`
  - `platformAuditLogService.getRecent(page?: number, size?: number): Promise<Page<PlatformAuditLogEntry>>`

- [ ] **Step 1: Add the stats interface + service**

In `platformApi.ts`, after the `PlatformAuditLogEntry` interface add:

```ts
// Mirrors PlatformStatsResponse (platform/model/dto).
export interface PlatformStats {
  tenants: { active: number; suspended: number; deleted: number }
  hubs: { online: number; stale: number; offline: number; never: number }
}
```

After `platformAuditLogService` add:

```ts
export const platformStatsService = {
  get: async (): Promise<PlatformStats> => {
    const { data } = await platformApi.get<PlatformStats>('/platform/stats')
    return data
  },
}
```

- [ ] **Step 2: Add `getRecent` to `platformAuditLogService`**

Inside `platformAuditLogService` (alongside `getByRestaurant`):

```ts
  getRecent: async (
    page = 0,
    size = 10
  ): Promise<Page<PlatformAuditLogEntry>> => {
    const { data } = await platformApi.get<Page<PlatformAuditLogEntry>>(
      '/platform/audit-log',
      { params: { page, size } }
    )
    return data
  },
```

- [ ] **Step 3: Type-check + lint**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: both clean (nothing consumes these yet).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/platformApi.ts
git commit -m "feat(console): platformApi — stats + recent-activity services"
```

---

## Task 3: Shared console primitives — `HubBadge` + `ConsolePageHeader`

**Files:**
- Create: `frontend/src/components/console/HubBadge.tsx`
- Create: `frontend/src/components/console/HubBadge.test.tsx`
- Create: `frontend/src/components/console/ConsolePageHeader.tsx`

**Interfaces:**
- Consumes: `HubStatus` from `@/lib/platformApi` (piece C).
- Produces:
  - `HubBadge` — default export? **named export** `export function HubBadge({ status }: { status: HubStatus })`.
  - `ConsolePageHeader` — named export `export function ConsolePageHeader({ title, action }: { title: string; action?: React.ReactNode })`.

- [ ] **Step 1: Write the failing `HubBadge` test**

Create `HubBadge.test.tsx`:

```tsx
import { describe, test, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { HubBadge } from '@/components/console/HubBadge'

describe('HubBadge', () => {
  test('renders the label for a live hub', () => {
    render(<HubBadge status="ONLINE" />)
    expect(screen.getByText('ONLINE')).toBeVisible()
  })

  test('renders a dash for NEVER', () => {
    render(<HubBadge status="NEVER" />)
    expect(screen.getByText('—')).toBeVisible()
  })
})
```

- [ ] **Step 2: Run it — verify it fails**

Run: `cd frontend && pnpm run test:run HubBadge`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `HubBadge`**

```tsx
import type { HubStatus } from '@/lib/platformApi'

const CONFIG: Record<HubStatus, { dot: string; label: string }> = {
  ONLINE: { dot: 'bg-green-500', label: 'ONLINE' },
  STALE: { dot: 'bg-amber-500', label: 'STALE' },
  OFFLINE: { dot: 'bg-zinc-400', label: 'OFFLINE' },
  NEVER: { dot: 'bg-transparent', label: '—' },
}

export function HubBadge({ status }: { status: HubStatus }) {
  const { dot, label } = CONFIG[status]
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-zinc-600">
      <span className={`h-2 w-2 rounded-full ${dot}`} />
      {label}
    </span>
  )
}
```

- [ ] **Step 4: Implement `ConsolePageHeader`**

```tsx
import type { ReactNode } from 'react'

export function ConsolePageHeader({
  title,
  action,
}: {
  title: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-semibold text-zinc-900">{title}</h1>
      {action}
    </div>
  )
}
```

- [ ] **Step 5: Run the test + build**

Run: `cd frontend && pnpm run test:run HubBadge && pnpm run build && pnpm run lint`
Expected: test PASS (2), build + lint clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/console/HubBadge.tsx frontend/src/components/console/HubBadge.test.tsx \
  frontend/src/components/console/ConsolePageHeader.tsx
git commit -m "feat(console): shared HubBadge + ConsolePageHeader primitives"
```

---

## Task 4: Console shell — `PlatformLayout` + `ConsoleSidebar`

**Files:**
- Create: `frontend/src/components/console/ConsoleSidebar.tsx`
- Modify: `frontend/src/layouts/PlatformLayout.tsx`
- Test: `frontend/src/layouts/PlatformLayout.test.tsx` (create)

**Interfaces:**
- Consumes: `usePlatformAuthStore` (`name`, `email`, `logout`), react-router `NavLink`/`useNavigate`.
- Produces: `ConsoleSidebar` — named export `export function ConsoleSidebar({ onNavigate }: { onNavigate?: () => void })` (the `onNavigate` callback closes the mobile drawer on item click).

- [ ] **Step 1: Write the failing layout test**

Create `PlatformLayout.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { PlatformLayout } from '@/layouts/PlatformLayout'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

const wrap = (initial = '/console') =>
  render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/console" element={<PlatformLayout />}>
          <Route index element={<div>DASH</div>} />
          <Route path="restaurants" element={<div>REST</div>} />
        </Route>
        <Route path="/console/login" element={<div>LOGIN</div>} />
      </Routes>
    </MemoryRouter>
  ) as unknown as ReactNode

describe('PlatformLayout', () => {
  beforeEach(() => {
    usePlatformAuthStore.setState({
      token: 't', operatorId: 'o', name: 'Operador Uno', email: 'op@ember.local',
    })
  })

  test('renders the sidebar nav and the routed content', () => {
    wrap()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeVisible()
    expect(screen.getByRole('link', { name: 'Restaurantes' })).toBeVisible()
    expect(screen.getByText('DASH')).toBeVisible()
  })

  test('"Cerrar sesión" clears auth and navigates to login', () => {
    wrap()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar sesión' }))
    expect(usePlatformAuthStore.getState().token).toBeUndefined()
    expect(screen.getByText('LOGIN')).toBeVisible()
  })
})
```

- [ ] **Step 2: Run it — verify it fails**

Run: `cd frontend && pnpm run test:run PlatformLayout`
Expected: FAIL — no "Dashboard"/"Restaurantes" links, no "Cerrar sesión" button (current layout has "Log out").

- [ ] **Step 3: Implement `ConsoleSidebar`**

```tsx
import { NavLink } from 'react-router-dom'
import { LayoutDashboard, Store } from 'lucide-react'
import { usePlatformAuthStore } from '@/store/platformAuthStore'

const NAV = [
  { to: '/console', end: true, label: 'Dashboard', icon: LayoutDashboard },
  { to: '/console/restaurants', end: false, label: 'Restaurantes', icon: Store },
]

export function ConsoleSidebar({ onNavigate }: { onNavigate?: () => void }) {
  const { name, email } = usePlatformAuthStore()

  return (
    <div className="flex h-full w-60 flex-col border-r border-zinc-200 bg-white">
      <div className="flex flex-col gap-1 border-b border-zinc-100 px-5 py-4">
        <span className="text-lg font-bold text-[#8c1717]">Ember Console</span>
        <span className="truncate text-xs text-zinc-500">{name ?? email}</span>
      </div>
      <nav className="flex flex-1 flex-col gap-1 p-3">
        {NAV.map(({ to, end, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${
                isActive
                  ? 'bg-[#8c1717]/10 font-medium text-[#8c1717]'
                  : 'text-zinc-600 hover:bg-zinc-100'
              }`
            }
          >
            <Icon size={18} strokeWidth={2} />
            {label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
```

- [ ] **Step 4: Rewrite `PlatformLayout`**

```tsx
import { useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import { usePlatformAuthStore } from '@/store/platformAuthStore'
import { Button } from '@/components/ui/button'
import { ConsoleSidebar } from '@/components/console/ConsoleSidebar'

export const PlatformLayout = () => {
  const navigate = useNavigate()
  const { name, email, logout } = usePlatformAuthStore()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/console/login', { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-zinc-50/50">
      {/* fixed sidebar (md+) */}
      <aside className="hidden md:block">
        <ConsoleSidebar />
      </aside>

      {/* mobile drawer */}
      {drawerOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden">
          <div className="h-full" onClick={(e) => e.stopPropagation()}>
            <ConsoleSidebar onNavigate={() => setDrawerOpen(false)} />
          </div>
          <div className="flex-1 bg-black/40" onClick={() => setDrawerOpen(false)} />
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-zinc-200 bg-white px-4 py-3 md:px-6">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded-md p-1.5 text-zinc-600 hover:bg-zinc-100 md:hidden"
              aria-label="Abrir menú"
              onClick={() => setDrawerOpen(true)}
            >
              <Menu size={20} />
            </button>
            <span className="text-sm text-zinc-500 md:hidden">Ember Console</span>
          </div>
          <div className="flex items-center gap-4 text-sm text-zinc-500">
            <span className="hidden sm:inline">{name ?? email}</span>
            <Link to="/console/password" className="hover:underline">
              Cambiar contraseña
            </Link>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Cerrar sesión
            </Button>
          </div>
        </header>
        <main className="min-w-0 flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
```

(The unused `X` import — remove it; it is listed only so the drawer close affordance is available if the plan executor wants a visible close button. If unused, delete the import to keep lint clean.)

- [ ] **Step 5: Run the test + build + lint**

Run: `cd frontend && pnpm run test:run PlatformLayout && pnpm run build && pnpm run lint`
Expected: test PASS (2), build clean, lint 0 errors.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/console/ConsoleSidebar.tsx frontend/src/layouts/PlatformLayout.tsx \
  frontend/src/layouts/PlatformLayout.test.tsx
git commit -m "feat(console): sidebar shell for the platform console"
```

---

## Task 5: `ConsoleDashboard` — KPIs + activity feed

**Files:**
- Modify: `frontend/src/pages/console/ConsoleDashboard.tsx`
- Test: `frontend/src/pages/console/ConsoleDashboard.test.tsx` (create)

**Interfaces:**
- Consumes: `platformStatsService.get` (Task 2), `platformAuditLogService.getRecent` (Task 2), `HubBadge` + `ConsolePageHeader` (Task 3).

- [ ] **Step 1: Write the failing test**

Create `ConsoleDashboard.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleDashboard from '@/pages/console/ConsoleDashboard'
import { platformStatsService, platformAuditLogService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformStatsService: { get: vi.fn() },
    platformAuditLogService: { ...actual.platformAuditLogService, getRecent: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

describe('ConsoleDashboard', () => {
  beforeEach(() => vi.clearAllMocks())

  test('renders KPI numbers and a recent-activity row', async () => {
    vi.mocked(platformStatsService.get).mockResolvedValue({
      tenants: { active: 5, suspended: 2, deleted: 1 },
      hubs: { online: 3, stale: 1, offline: 0, never: 2 },
    })
    vi.mocked(platformAuditLogService.getRecent).mockResolvedValue({
      content: [
        {
          id: 'a-1', operatorId: 'o-1', operatorEmail: 'op@ember.local',
          restaurantId: 'r-1', action: 'RESTAURANT_DELETED',
          oldValue: 'SUSPENDED', newValue: 'DELETED', createdAt: '2026-09-06T11:00:00Z',
        },
      ],
      totalElements: 1, totalPages: 1, size: 10, number: 0,
    })

    wrap(<ConsoleDashboard />)

    expect(await screen.findByText('RESTAURANT_DELETED')).toBeVisible()
    expect(screen.getByText('5')).toBeVisible() // tenants.active
    expect(screen.getByText('op@ember.local')).toBeVisible()
  })

  test('shows an error message when stats fail', async () => {
    vi.mocked(platformStatsService.get).mockRejectedValue(new Error('boom'))
    vi.mocked(platformAuditLogService.getRecent).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, size: 10, number: 0,
    })

    wrap(<ConsoleDashboard />)

    expect(await screen.findByText('No se pudieron cargar las métricas.')).toBeVisible()
  })
})
```

- [ ] **Step 2: Run it — verify it fails**

Run: `cd frontend && pnpm run test:run ConsoleDashboard`
Expected: FAIL — the current stub renders none of that.

- [ ] **Step 3: Implement `ConsoleDashboard`**

```tsx
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { LayoutDashboard, Store, PauseCircle, Trash2 } from 'lucide-react'
import { platformStatsService, platformAuditLogService } from '@/lib/platformApi'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { HubBadge } from '@/components/console/HubBadge'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'
import type { HubStatus } from '@/lib/platformApi'

function StatCard({
  label,
  value,
  children,
}: {
  label: string
  value: number
  children?: React.ReactNode
}) {
  return (
    <Card className="rounded-2xl">
      <CardContent className="flex flex-col gap-1 p-4">
        <span className="text-xs text-zinc-500">{label}</span>
        <span className="text-2xl font-semibold text-zinc-900">{value}</span>
        {children}
      </CardContent>
    </Card>
  )
}

const HUB_ORDER: HubStatus[] = ['ONLINE', 'STALE', 'OFFLINE', 'NEVER']

export default function ConsoleDashboard() {
  const stats = useQuery({ queryKey: ['platformStats'], queryFn: platformStatsService.get })
  const activity = useQuery({
    queryKey: ['platformActivity'],
    queryFn: () => platformAuditLogService.getRecent(0, 10),
  })
  const rows = activity.data?.content ?? []

  return (
    <div className="flex flex-col gap-6">
      <ConsolePageHeader title="Panel" />

      {stats.isError ? (
        <div className="text-sm text-red-500">No se pudieron cargar las métricas.</div>
      ) : stats.isLoading ? (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {Array.from({ length: 7 }).map((_, i) => (
            <div key={i} className="h-20 animate-pulse rounded-2xl bg-zinc-100" />
          ))}
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <StatCard label="Tenants activos" value={stats.data!.tenants.active} />
            <StatCard label="Suspendidos" value={stats.data!.tenants.suspended} />
            <StatCard label="Eliminados" value={stats.data!.tenants.deleted} />
          </div>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
            {HUB_ORDER.map((s) => (
              <StatCard key={s} label={`Hubs ${s.toLowerCase()}`} value={hubValue(stats.data!.hubs, s)}>
                <HubBadge status={s} />
              </StatCard>
            ))}
          </div>
        </div>
      )}

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-lg">Actividad reciente</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Fecha</TableHead>
                <TableHead>Operador</TableHead>
                <TableHead>Acción</TableHead>
                <TableHead>Restaurante</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((e) => (
                <TableRow key={e.id}>
                  <TableCell
                    className="text-zinc-500"
                    title={new Date(e.createdAt).toLocaleString()}
                  >
                    {new Date(e.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell className="text-zinc-500">{e.operatorEmail}</TableCell>
                  <TableCell>
                    <Badge variant="outline">{e.action}</Badge>
                  </TableCell>
                  <TableCell>
                    {e.restaurantId ? (
                      <Link
                        to={`/console/restaurants/${e.restaurantId}`}
                        className="text-[#8c1717] hover:underline"
                      >
                        Ver
                      </Link>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {rows.length === 0 && !activity.isLoading && (
                <TableRow>
                  <TableCell colSpan={4} className="py-6 text-center text-zinc-400">
                    Sin actividad registrada.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-3">
        <Button asChild>
          <Link to="/console/restaurants/new">Nuevo restaurante</Link>
        </Button>
        <Button variant="outline" asChild>
          <Link to="/console/restaurants">Ver restaurantes</Link>
        </Button>
      </div>
    </div>
  )
}

function hubValue(hubs: PlatformStats['hubs'], s: HubStatus): number {
  switch (s) {
    case 'ONLINE':
      return hubs.online
    case 'STALE':
      return hubs.stale
    case 'OFFLINE':
      return hubs.offline
    case 'NEVER':
      return hubs.never
  }
}
```

Add `import type { PlatformStats } from '@/lib/platformApi'` at the top (used by `hubValue`). If `Button` does not support `asChild`, wrap the `Link` in a `Button`-styled `<Link>` instead — check `@/components/ui/button`; the tenant app uses `<Button asChild>` (see `ConsoleRestaurantDetail` piece C? no — check `frontend/src/components/ui/button.tsx` for `asChild`). If `asChild` is unsupported, use `onClick={() => navigate(...)}` with `useNavigate`.

- [ ] **Step 4: Run it — verify it passes**

Run: `cd frontend && pnpm run test:run ConsoleDashboard`
Expected: PASS (2).

- [ ] **Step 5: Build + lint**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/console/ConsoleDashboard.tsx frontend/src/pages/console/ConsoleDashboard.test.tsx
git commit -m "feat(console): real dashboard — KPIs + recent-activity feed"
```

---

## Task 6: `ConsoleRestaurants` re-skin

**Files:**
- Modify: `frontend/src/pages/console/ConsoleRestaurants.tsx`
- Modify: `frontend/src/pages/console/ConsoleRestaurants.test.tsx`

**Interfaces:**
- Consumes: `HubBadge` + `ConsolePageHeader` (Task 3), shadcn `Table` / `Badge` / `Switch` / `Label`.
- Behaviour preserved from piece C: `getAll(page, 10, includeDeleted)`, the "Ver eliminados" toggle calling `getAll(0, 10, true)`, muted `DELETED` rows.

- [ ] **Step 1: Update the test to the new markup**

In `ConsoleRestaurants.test.tsx` the two piece-C tests stay, but the toggle selector must survive the `<input type=checkbox>` → `<Switch>` change. `<Switch>` renders a `role="switch"` element; keep the label association. Change:

```tsx
    fireEvent.click(screen.getByLabelText('Ver eliminados'))
```

to

```tsx
    fireEvent.click(screen.getByRole('switch', { name: 'Ver eliminados' }))
```

The `renders the Hub status for each row` test (`screen.findByText('OFFLINE')`) is unchanged — `HubBadge` still renders the label text.

- [ ] **Step 2: Run it — verify it fails**

Run: `cd frontend && pnpm run test:run ConsoleRestaurants`
Expected: FAIL — no `role="switch"` yet.

- [ ] **Step 3: Re-skin `ConsoleRestaurants.tsx`**

Full replacement:

```tsx
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { platformRestaurantService } from '@/lib/platformApi'
import { PaginationControls } from '@/components/PaginationControls'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Switch } from '@/components/ui/switch'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { HubBadge } from '@/components/console/HubBadge'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'

const statusBadgeClass = (status: string) => {
  switch (status) {
    case 'ACTIVE':
      return 'bg-green-100 text-green-700'
    case 'SUSPENDED':
      return 'bg-red-100 text-red-700'
    case 'DELETED':
      return 'bg-zinc-200 text-zinc-500'
    default:
      return 'bg-zinc-100 text-zinc-600'
  }
}

export default function ConsoleRestaurants() {
  const [page, setPage] = useState(0)
  const [includeDeleted, setIncludeDeleted] = useState(false)

  const {
    data: restaurantsPage,
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['platformRestaurants', page, includeDeleted],
    queryFn: () => platformRestaurantService.getAll(page, 10, includeDeleted),
  })
  const restaurants = restaurantsPage?.content ?? []

  return (
    <div className="flex flex-col gap-4">
      <ConsolePageHeader
        title="Restaurantes"
        action={
          <Button asChild>
            <Link to="/console/restaurants/new">Nuevo restaurante</Link>
          </Button>
        }
      />

      <div className="flex items-center gap-2">
        <Switch
          id="include-deleted"
          checked={includeDeleted}
          onCheckedChange={(v) => {
            setIncludeDeleted(v)
            setPage(0)
          }}
        />
        <Label htmlFor="include-deleted" className="text-sm text-zinc-600">
          Ver eliminados
        </Label>
      </div>

      <div className="rounded-lg border border-zinc-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Nombre</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead>Estado</TableHead>
              <TableHead>Hub</TableHead>
              <TableHead>Creado</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading &&
              Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={i}>
                  <TableCell colSpan={6}>
                    <div className="h-4 animate-pulse rounded bg-zinc-100" />
                  </TableCell>
                </TableRow>
              ))}

            {isError && (
              <TableRow>
                <TableCell colSpan={6} className="py-6 text-center text-red-500">
                  Error al cargar los restaurantes.
                </TableCell>
              </TableRow>
            )}

            {!isLoading &&
              !isError &&
              restaurants.map((restaurant) => (
                <TableRow
                  key={restaurant.id}
                  className={restaurant.status === 'DELETED' ? 'opacity-50' : undefined}
                >
                  <TableCell className="font-medium text-zinc-800">
                    <Link to={`/console/restaurants/${restaurant.id}`} className="hover:underline">
                      {restaurant.name}
                    </Link>
                  </TableCell>
                  <TableCell className="text-zinc-500">{restaurant.slug}</TableCell>
                  <TableCell className="text-zinc-500">{restaurant.plan}</TableCell>
                  <TableCell>
                    <Badge className={statusBadgeClass(restaurant.status)}>{restaurant.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <HubBadge status={restaurant.hubStatus} />
                  </TableCell>
                  <TableCell className="text-zinc-500">
                    {new Date(restaurant.createdAt).toLocaleDateString()}
                  </TableCell>
                </TableRow>
              ))}

            {!isLoading && !isError && restaurants.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-6 text-center text-zinc-400">
                  Sin restaurantes registrados.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      <PaginationControls
        page={page}
        totalPages={restaurantsPage?.totalPages ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}
```

Note: the local `hubDot` helper from piece C is **gone** (replaced by `HubBadge`). `Badge` accepts a `className` override — the `statusBadgeClass` string wins over the default variant.

- [ ] **Step 4: Run it — verify it passes**

Run: `cd frontend && pnpm run test:run ConsoleRestaurants`
Expected: PASS (2).

- [ ] **Step 5: Build + lint**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/console/ConsoleRestaurants.tsx frontend/src/pages/console/ConsoleRestaurants.test.tsx
git commit -m "feat(console): re-skin restaurant list onto shadcn table"
```

---

## Task 7: `ConsoleRestaurantDetail` re-skin

**Files:**
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx`

**Interfaces:**
- Consumes: `HubBadge` + `ConsolePageHeader` (Task 3), shadcn `Card` / `Table` / `Badge` / `Dialog`.
- Behaviour preserved from pieces B/C: delete button only when `SUSPENDED`, type-the-slug confirm gating `deleteRestaurant('r-1')`, `DELETED` → "Restaurar restaurante" + hidden status/license controls, Hub panel fields, audit table, license-key download.

- [ ] **Step 1: Update the test — the confirm modal is now a `Dialog`**

The piece-B/C tests already use `getByRole('button', { name: 'Eliminar restaurante' })`, `getByRole('button', { name: 'Confirmar eliminación' })`, `getByLabelText('Escribe el slug para confirmar')`, `getByRole('button', { name: 'Restaurar restaurante' })`, and the `queryByRole` negatives. A shadcn `<Dialog>` renders its content in a portal but Testing Library still finds it by role/label — **no test change is required** as long as the dialog markup keeps the same accessible names. Verify by running the existing test after Step 2; only touch the test if a selector breaks (e.g. the slug input needs `id="delete-slug"` kept so `getByLabelText` resolves).

- [ ] **Step 2: Re-skin `ConsoleRestaurantDetail.tsx`**

Keep the whole hooks section (queries, `deleteRestaurant`/`restoreRestaurant`/`toggleStatus`/`issueHubLicense` mutations, `showDeleteConfirm`/`slugInput` state, `invalidateAll`) **exactly as piece C left it**. Replace only the returned JSX:

```tsx
  return (
    <div className="flex flex-col gap-6">
      <ConsolePageHeader
        title={restaurant.name}
        action={
          <div className="flex items-center gap-2">
            {restaurant.status === 'DELETED' ? (
              <Button disabled={restoreRestaurant.isPending} onClick={() => restoreRestaurant.mutate()}>
                {restoreRestaurant.isPending ? 'Restaurando...' : 'Restaurar restaurante'}
              </Button>
            ) : (
              <>
                <Button
                  variant="outline"
                  disabled={issueHubLicense.isPending}
                  onClick={() => issueHubLicense.mutate()}
                >
                  {issueHubLicense.isPending ? 'Emitiendo...' : 'Emitir licencia Hub'}
                </Button>
                <Button
                  disabled={toggleStatus.isPending}
                  onClick={() => toggleStatus.mutate(nextStatus(restaurant.status))}
                >
                  {restaurant.status === 'SUSPENDED' ? 'Reactivar' : 'Suspender'}
                </Button>
                <Button
                  variant="outline"
                  className="border-red-300 text-red-700 hover:bg-red-50"
                  disabled={restaurant.status !== 'SUSPENDED'}
                  title={restaurant.status !== 'SUSPENDED' ? 'Suspende el restaurante primero' : undefined}
                  onClick={() => setShowDeleteConfirm(true)}
                >
                  Eliminar restaurante
                </Button>
              </>
            )}
          </div>
        }
      />

      <Link to="/console/restaurants" className="text-sm text-[#8c1717] hover:underline">
        &larr; Restaurantes
      </Link>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Datos</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-zinc-500">Slug</div>
            <div className="font-medium text-zinc-800">{restaurant.slug}</div>
          </div>
          <div>
            <div className="text-zinc-500">Plan</div>
            <div className="font-medium text-zinc-800">{restaurant.plan}</div>
          </div>
          <div>
            <div className="text-zinc-500">Estado</div>
            <Badge className={statusBadgeClass(restaurant.status)}>{restaurant.status}</Badge>
          </div>
          <div>
            <div className="text-zinc-500">Creado</div>
            <div className="font-medium text-zinc-800">
              {new Date(restaurant.createdAt).toLocaleDateString()}
            </div>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Hub</CardTitle>
        </CardHeader>
        <CardContent className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="text-zinc-500">Estado</div>
            <HubBadge status={restaurant.hubStatus} />
          </div>
          <div>
            <div className="text-zinc-500">Activado</div>
            <div className="font-medium text-zinc-800">
              {restaurant.hubActivatedAt ? new Date(restaurant.hubActivatedAt).toLocaleString() : '—'}
            </div>
          </div>
          <div>
            <div className="text-zinc-500">Último latido</div>
            <div className="font-medium text-zinc-800">
              {restaurant.lastHeartbeatAt ? new Date(restaurant.lastHeartbeatAt).toLocaleString() : '—'}
            </div>
          </div>
          <div>
            <div className="text-zinc-500">IP</div>
            <div className="font-medium text-zinc-800">{restaurant.lastHeartbeatIp ?? '—'}</div>
          </div>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Administradores</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Nombre</TableHead>
                <TableHead>Email</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {restaurant.admins.map((admin) => (
                <TableRow key={admin.id}>
                  <TableCell className="font-medium text-zinc-800">{admin.name}</TableCell>
                  <TableCell className="text-zinc-500">{admin.email}</TableCell>
                </TableRow>
              ))}
              {restaurant.admins.length === 0 && (
                <TableRow>
                  <TableCell colSpan={2} className="py-6 text-center text-zinc-400">
                    Sin administradores.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card className="rounded-2xl">
        <CardHeader>
          <CardTitle className="text-base">Historial de auditoría</CardTitle>
        </CardHeader>
        <CardContent>
          {isAuditLoading ? (
            <div className="text-zinc-500">Cargando historial...</div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Fecha</TableHead>
                  <TableHead>Operador</TableHead>
                  <TableHead>Acción</TableHead>
                  <TableHead>Anterior</TableHead>
                  <TableHead>Nuevo</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {auditLog.map((entry) => (
                  <TableRow key={entry.id}>
                    <TableCell className="text-zinc-500">
                      {new Date(entry.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-zinc-500">{entry.operatorEmail}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{entry.action}</Badge>
                    </TableCell>
                    <TableCell className="text-zinc-500">{entry.oldValue ?? '-'}</TableCell>
                    <TableCell className="text-zinc-500">{entry.newValue ?? '-'}</TableCell>
                  </TableRow>
                ))}
                {auditLog.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="py-6 text-center text-zinc-400">
                      Sin actividad registrada.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <PaginationControls
        page={auditPage}
        totalPages={auditLogPage?.totalPages ?? 0}
        onPageChange={setAuditPage}
      />

      <Dialog
        open={showDeleteConfirm}
        onOpenChange={(open) => {
          setShowDeleteConfirm(open)
          if (!open) setSlugInput('')
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Eliminar restaurante</DialogTitle>
            <DialogDescription>
              Esto marca <span className="font-medium">{restaurant.name}</span> como eliminado. Se
              puede restaurar después. Escribe <code>{restaurant.slug}</code> para confirmar.
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="delete-slug" className="text-sm text-zinc-600">
              Escribe el slug para confirmar
            </Label>
            <input
              id="delete-slug"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={slugInput}
              onChange={(e) => setSlugInput(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setShowDeleteConfirm(false)
                setSlugInput('')
              }}
            >
              Cancelar
            </Button>
            <Button
              className="bg-red-600 hover:bg-red-700"
              disabled={slugInput !== restaurant.slug || deleteRestaurant.isPending}
              onClick={() => deleteRestaurant.mutate()}
            >
              Confirmar eliminación
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
```

Update the imports block to add:

```tsx
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { HubBadge } from '@/components/console/HubBadge'
import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'
```

Keep the existing `statusBadgeClass` helper and `nextStatus` helper (piece C) as-is.

- [ ] **Step 3: Run the test**

Run: `cd frontend && pnpm run test:run ConsoleRestaurantDetail`
Expected: PASS (3). If the `Dialog` portal breaks a selector, adjust that one assertion to `within(screen.getByRole('dialog'))…` and re-run.

- [ ] **Step 4: Build + lint**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/console/ConsoleRestaurantDetail.tsx frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx
git commit -m "feat(console): re-skin restaurant detail — cards, table, dialog confirm"
```

---

## Task 8: Login / Create / PasswordChange polish

**Files:**
- Modify: `frontend/src/pages/console/ConsoleLogin.tsx`
- Modify: `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`
- Modify: `frontend/src/pages/console/ConsolePasswordChange.tsx`
- Test: `frontend/src/pages/console/ConsoleLogin.test.tsx` (create)

**Interfaces:** none new — copy + colour only.

- [ ] **Step 1: Write the failing login test**

Create `ConsoleLogin.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ConsoleLogin from '@/pages/console/ConsoleLogin'
import { platformAuthService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformAuthService: { ...actual.platformAuthService, login: vi.fn() },
  }
})

const wrap = (ui: ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>)

describe('ConsoleLogin', () => {
  beforeEach(() => vi.clearAllMocks())

  test('shows the Spanish CTA and submits credentials', async () => {
    vi.mocked(platformAuthService.login).mockResolvedValue({ token: 't', name: 'Op' })
    wrap(<ConsoleLogin />)

    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeVisible()
    fireEvent.change(screen.getByPlaceholderText('Ingresá tu email'), {
      target: { value: 'op@ember.local' },
    })
    fireEvent.change(screen.getByPlaceholderText('Ingresá tu contraseña'), {
      target: { value: 'x' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))

    await waitFor(() => expect(platformAuthService.login).toHaveBeenCalled())
  })
})
```

- [ ] **Step 2: Run it — verify it fails**

Run: `cd frontend && pnpm run test:run ConsoleLogin`
Expected: FAIL — button says "Login", placeholder is "Enter your email".

- [ ] **Step 3: Edit `ConsoleLogin.tsx`**

- Title: `text-[#8c1717]` (was `#920703`) — search & replace `#920703` → `#8c1717` in the file.
- Add the wordmark above the `<Card>`: inside the outer flex `div`, before `<Card>`:
  ```tsx
        <div className="mb-6 text-center text-2xl font-bold text-[#8c1717]">Ember</div>
  ```
  (wrap the existing `<Card>` and this in a `<div className="flex flex-col items-center">` if needed — simplest: put the wordmark as the first child of the existing centering `div` and keep the `<Card>` after it.)
- Strings:
  - `CardTitle` text stays "Ember Platform Console" → "Consola de operadores".
  - `CardDescription` "Sign in with your operator account." → "Ingresá con tu cuenta de operador."
  - `Input` placeholder "Enter your email" → "Ingresá tu email"; "Enter your password" → "Ingresá tu contraseña".
  - Submit button: "Logging in..." → "Ingresando..."; "Login" → "Iniciar sesión".
  - `toast.success('Login successful!')` → `toast.success('Sesión iniciada')`.
  - `toast.error('Unauthorized', …)` → `'Credenciales inválidas'`; `'Too many login attempts. Please try again later.'` → `'Demasiados intentos. Probá de nuevo en un rato.'`; `'Login failed'` → `'No se pudo iniciar sesión'`.
  - Zod messages: `'Invalid email address'` → `'Email inválido'`, `'Email is required'` → `'El email es obligatorio'`, `'Password is required'` → `'La contraseña es obligatoria'`.

- [ ] **Step 4: Edit `ConsoleRestaurantCreate.tsx` + `ConsolePasswordChange.tsx`**

- Both: replace the raw `<Link … className="text-sm text-blue-600 …">` back-link + `<CardTitle>` with `<ConsolePageHeader title="Nuevo restaurante" />` / `title="Cambiar contraseña"` above the `<Card>`, and keep a plain back `<Link className="text-sm text-[#8c1717] hover:underline">` (change `text-blue-600` → `text-[#8c1717]`).
- `ConsoleRestaurantCreate`: `toast.success('Restaurant created')` → `'Restaurante creado'`; `toast.error('Slug or admin email already in use', …)` → `'El slug o el email del admin ya están en uso'`; `'Failed to create restaurant'` → `'No se pudo crear el restaurante'`. Zod messages → Spanish (`'Restaurant name is required'` → `'El nombre es obligatorio'`, `'Slug is required'` → `'El slug es obligatorio'`, `'Slug must be lowercase alphanumeric with single hyphens'` → `'El slug debe ser minúsculas y números separados por guiones'`, `'Admin name is required'` → `'El nombre del admin es obligatorio'`, `'Admin email must be valid'` → `'El email del admin no es válido'`, `'Admin email is required'` → `'El email del admin es obligatorio'`, `'Password must be between 8 and 128 characters'` → `'La contraseña debe tener entre 8 y 128 caracteres'`, the pattern message → `'La contraseña necesita mayúscula, minúscula, número y símbolo'`).
- `ConsolePasswordChange`: `toast.success('Password updated')` → `'Contraseña actualizada'`; `toast.error('Current password is incorrect', …)` → `'La contraseña actual es incorrecta'`; `'Failed to update password'` → `'No se pudo actualizar la contraseña'`. Zod: `'Current password is required'` → `'La contraseña actual es obligatoria'`; reuse the two password messages above.
- Both submit buttons already Spanish ("Creando..." / "Actualizando...") — leave.
- Add `import { ConsolePageHeader } from '@/components/console/ConsolePageHeader'` to both.

- [ ] **Step 5: Run the login test + full frontend checks**

Run: `cd frontend && pnpm run test:run ConsoleLogin && pnpm run build && pnpm run lint`
Expected: test PASS, build + lint clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/console/ConsoleLogin.tsx frontend/src/pages/console/ConsoleLogin.test.tsx \
  frontend/src/pages/console/ConsoleRestaurantCreate.tsx frontend/src/pages/console/ConsolePasswordChange.tsx
git commit -m "feat(console): brand colour + spanish copy on auth screens"
```

---

## Task 9: Report + PROGRESS + full verification + PR

**Files:**
- Create: `reports/385-platform-console-redesign.md`
- Modify: `PROGRESS.md`

- [ ] **Step 1: Full verification**

Run: `cd backend && ./mvnw test` → BUILD SUCCESS
Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run` → build clean, lint 0 errors, all tests green

- [ ] **Step 2: Grep for leftovers**

Run: `grep -rn "#920703\|Log out\|Login successful\|Enter your" frontend/src/pages/console frontend/src/layouts/PlatformLayout.tsx frontend/src/components/console`
Expected: **no matches** (all replaced). Fix any that remain, re-run the relevant task's checks.

- [ ] **Step 3: Write `reports/385-platform-console-redesign.md`**

Per `CLAUDE.md` §4 structure: Identification (report 385, task = this plan, predecessor 384),
Objective, Modified Files (the full list from this plan), What Changed (per spec §4.1–§4.7),
Why It Changed (spec §1–§2). Note the single backend addition (`GET /platform/stats`) and that
pieces B/C behaviour is unchanged.

- [ ] **Step 4: Update `PROGRESS.md`**

- `Last Completed Task` → report 385, 2–3 sentence summary.
- Under "Platform console improvements": tick "Piece D".
- `Current Active Task` → none (platform-console effort A–D complete).
- `System Health` → backend + frontend green with counts.

- [ ] **Step 5: Commit + push + PR**

```bash
git add reports/385-platform-console-redesign.md PROGRESS.md
git commit -m "docs(console): report 385 + progress — platform console redesign"
git push -u origin spec/platform-console-redesign
gh pr create --base main --head spec/platform-console-redesign \
  --title "feat(console): platform console redesign (piece D)" \
  --body "Implements docs/superpowers/specs/2026-09-06-platform-console-redesign-design.md. Sidebar shell + real dashboard (KPIs + activity feed, new GET /platform/stats) + all screens re-skinned onto shadcn primitives. No behaviour change to pieces B/C. Depends on #81 (B/C) — rebase onto main once #81 merges."
```

Note: if PR #81 has merged to `main` by now, `git rebase origin/main` first so the PR is a clean diff.

---

## Self-Review

**Spec coverage:**
- §4.1 shell → Task 4 (`PlatformLayout` + `ConsoleSidebar`); `ConsolePageHeader` → Task 3.
- §4.1 `HubBadge` (replaces piece-C `hubDot`) → Task 3 (created), Task 6 (`hubDot` removed).
- §4.2 dashboard → Task 5.
- §4.3 `GET /platform/stats` + `countByStatus` → Task 1; frontend `platformStatsService` + `getRecent` → Task 2.
- §4.4 list re-skin → Task 6. §4.5 detail re-skin + `Dialog` → Task 7.
- §4.6 login/create/password polish → Task 8.
- §4.7 cleanup (`hubDot` gone, one `#8c1717`, `components/console/`) → Tasks 3/6/8 + Task 9 Step 2 grep.
- §5 testing → each task ends with its tests; Task 1 backend tests; Task 9 full run.
- §6 delivery (one PR, rebase on #81) → Task 9.
- §7 open items → resolved in the plan: shadcn APIs pinned from the real component files (`Table`/`Dialog`/`Switch`/`Badge` imports + props shown in Tasks 5–7); `ConsoleSidebar` nav is a local `const NAV`; `Badge` takes `variant` + `className` (className override used for status colours).

**Placeholder scan:** No "TBD"/"handle errors"/"similar to Task N". Task 5 Step 3 flags a conditional (`Button asChild` support) with a concrete fallback (`useNavigate`) — acceptable, it is a verify-then-pick, not a placeholder. Task 8 lists every string change explicitly.

**Type consistency:** `PlatformStats` shape identical in Task 1 (Java record), Task 2 (TS interface), Task 5 (`hubValue` switch). `HubBadge` named export + `{ status: HubStatus }` prop identical in Tasks 3/5/6/7. `ConsolePageHeader` `{ title, action? }` identical in Tasks 3/5/6/7/8. `ConsoleSidebar` `{ onNavigate? }` identical in Tasks 3/4. `platformAuditLogService.getRecent(page?, size?)` identical in Tasks 2 and 5. `countByStatus(RestaurantStatus): long` identical in Tasks 1 (repo, service, tests).
