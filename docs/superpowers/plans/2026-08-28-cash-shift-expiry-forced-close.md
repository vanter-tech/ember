# Cash Shift Expiry & Forced Daily Close Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give a `CashShift` a computed expiry anchored to the tenant's configured closing time, warn before/after it lapses, allow unlimited 1-hour prolongs, block *new* cash operations once overdue, and walk the first user of a new day through closing a stale shift.

**Architecture:** Backend adds a pure `CashShiftDeadlineService` (no dependencies) that turns `openedAt` + `businessHours` into `expiresAt`, plus four nullable columns on `cash_shifts`. `overdue` is a derived boolean over an `OPEN` row — no new enum value, no scheduler; it is evaluated lazily on every read and every guarded write. Enforcement lives at two existing write paths (`PaymentService.registerPhysicalPayment`, `CashShiftService.recordMovement`) which throw a new `CashShiftOverdueException` mapped to HTTP 409. Frontend adds one `<CashShiftSentinel/>` mounted in the waiter/admin shells that polls the existing `cashShiftCurrent` query every 60 s and renders one of three modals from a pure `deriveCashShiftAlert()` helper.

**Tech Stack:** Java 17, Spring Boot 3.5.14, Spring Data JPA, Flyway, JUnit 5 + Mockito + AssertJ. React 19, TypeScript, TanStack Query 5, Zustand 5, shadcn/ui, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-08-28-cash-shift-expiry-forced-close-design.md`

## Global Constraints

- **No Kafka.** Internal sync events only (`ApplicationEventPublisher` / `@EventListener`).
- **No new admin settings.** The only configuration input is the existing `businessHours.schedule`. All timing values are code constants: `GRACE = 2h`, `CLOSED_DAY_FALLBACK = 12h`, `PROLONG_STEP = 1h` (backend); `PRE_WARNING = 30min`, `REMINDER_INTERVAL = 15min` (frontend).
- **No new `CashShiftStatus` value.** `overdue` is derived.
- **No scheduler / background job.** Expiry evaluated on read and on write.
- **No new frontend WebSocket subscription.** The sentinel polls; it does not subscribe to `/topic/cash-register/*`.
- **Digital payments are never blocked** by expiry — only `registerPhysicalPayment` and `recordMovement`.
- **`closeShift` is unchanged** — it still rejects close while `activeTables > 0`.
- **Backend build/verify:** `cd backend && ./mvnw test` (never `mvn`). **Frontend build/verify:** `cd frontend && pnpm run build` then `pnpm run lint`.
- **Commits:** lowercase Conventional Commits, scoped `git add <paths>` only (never `git add -A`/`.`), no `Co-authored-by:` / `Signed-off-by:` / AI signatures. Per-task commits in this plan are local checkpoints; the final task squashes them into one atomic commit and writes one report (`reports/259-...`), per CLAUDE.md §4/§7.
- **i18n:** every user-facing string added as a key in both `frontend/src/locales/es/<ns>.ts` and `frontend/src/locales/en/<ns>.ts`. Spanish is the product's primary locale; write natural Spanish, mirror the key into English.
- **Tenant/settings access:** `settingService.getSettings(TenantContextHolder.requireTenantId()).getPayload().getBusinessHours()`.

---

## File Structure

**Backend — new files**
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java` — pure deadline math + constants + `isOverdue` / `prolong` helpers. No injected dependencies.
- `backend/src/main/java/com/vanter/ember/cashregister/exception/CashShiftOverdueException.java` — thrown by the two guarded write paths.
- `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftProlonged.java` — audit-symmetry event.
- `backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql` — 4 columns + backfill.
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftDeadlineServiceTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java`
- `backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerOverdueTest.java`

**Backend — modified files**
- `cashregister/model/CashShift.java` — 4 columns + `effectiveDeadline()` / `businessDay()` helpers.
- `cashregister/service/CashShiftService.java` — `openShift` stamps `expiresAt`; new `prolongShift`; `recordMovement` overdue guard; `toResponse` fills new fields; gains `SettingService` + `CashShiftDeadlineService` deps.
- `cashregister/controller/CashShiftController.java` — `POST /{id}/prolong`.
- `cashregister/dto/CashShiftResponse.java` — 5 new record components.
- `cashregister/listener/CashRegisterWebSocketListener.java` — broadcast `CashShiftProlonged` (mirrors the other three).
- `billing/service/PaymentService.java` — overdue guard in `registerPhysicalPayment`; gains `CashShiftDeadlineService` dep.
- `config/GlobalExceptionHandler.java` — `@ExceptionHandler(CashShiftOverdueException.class)` → 409 + `code` property.
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java` — new cases + mock wiring.
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java` — overdue cases.

**Frontend — new files**
- `frontend/src/lib/cashShiftAlert.ts` — pure `deriveCashShiftAlert(shift, now)` + exported constants.
- `frontend/src/lib/cashShiftAlert.test.ts` — state-machine unit tests.
- `frontend/src/components/CashShiftSentinel.tsx` — the mounted watcher + its three modals.

**Frontend — modified files**
- `frontend/src/lib/api.ts` — `cashShiftService.prolong`.
- `frontend/src/lib/backend-types.ts` — regenerated from OpenAPI.
- `frontend/src/layouts/WaiterLayout.tsx`, `frontend/src/layouts/AdminLayout.tsx` — mount `<CashShiftSentinel/>`.
- `frontend/src/components/FloatingNav.tsx` — logout warning when `overdue`.
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx` — disable "Registrar movimiento" when `overdue`; bump shared query `refetchInterval`.
- `frontend/src/pages/waiter/TableInformation.tsx` — physical-payment `onError` recognises `code: "CASH_SHIFT_OVERDUE"`.
- `frontend/src/locales/{es,en}/waiter.ts`, `frontend/src/locales/{es,en}/common.ts` — keys.

---

## Task 1: `CashShiftDeadlineService` (pure deadline math)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftDeadlineServiceTest.java`

**Interfaces:**
- Consumes: `com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings` (existing — has `List<DaySchedule> getSchedule()`; `DaySchedule` has `DayOfWeek getDay()`, `boolean isClosed()`, `String getCloseTime()`).
- Produces:
  - `LocalDateTime computeExpiresAt(LocalDateTime openedAt, BusinessHoursSettings hours)`
  - `boolean isOverdue(CashShift shift, LocalDateTime now)`
  - `LocalDateTime prolong(CashShift shift, LocalDateTime now)`
  - public constants `Duration GRACE`, `Duration CLOSED_DAY_FALLBACK`, `Duration PROLONG_STEP`

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.cashregister.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashShiftDeadlineServiceTest {

    private final CashShiftDeadlineService service = new CashShiftDeadlineService();

    private BusinessHoursSettings hoursWith(DayOfWeek day, boolean closed, String closeTime) {
        DaySchedule d = new DaySchedule();
        d.setDay(day);
        d.setClosed(closed);
        d.setOpenTime("09:00");
        d.setCloseTime(closeTime);
        BusinessHoursSettings h = new BusinessHoursSettings();
        h.setSchedule(List.of(d));
        return h;
    }

    private CashShift shiftExpiring(LocalDateTime expiresAt, LocalDateTime prolongedUntil) {
        return CashShift.builder()
                .id(1L).status(CashShiftStatus.OPEN)
                .openedAt(expiresAt.minusHours(5))
                .expiresAt(expiresAt).prolongedUntil(prolongedUntil)
                .build();
    }

    @Test
    void computeExpiresAt_openDuringTrading_isCloseTimePlusTwoHours() {
        // Monday, close 23:00, opened 18:00 -> candidate 23:00 same day -> +2h -> 01:00 Tuesday
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 18, 0); // a Monday
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 1, 1, 0));
    }

    @Test
    void computeExpiresAt_openedAfterCloseTime_rollsToNextDay() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 23, 30);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 2, 1, 0));
    }

    @Test
    void computeExpiresAt_overnightCloseTime_rollsForwardThenAddsGrace() {
        // close 02:00, opened 20:00 -> candidate 02:00 same day is before openedAt -> +1 day -> 02:00 next -> +2h
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "02:00"));
        assertThat(expiresAt).isEqualTo(LocalDateTime.of(2026, 9, 1, 4, 0));
    }

    @Test
    void computeExpiresAt_dayClosed_fallsBackToTwelveHours() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, true, "23:00"));
        assertThat(expiresAt).isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void computeExpiresAt_noScheduleEntry_fallsBackToTwelveHours() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        LocalDateTime expiresAt =
                service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.SUNDAY, false, "23:00"));
        assertThat(expiresAt).isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void computeExpiresAt_malformedCloseTime_fallsBackAndDoesNotThrow() {
        LocalDateTime openedAt = LocalDateTime.of(2026, 8, 31, 20, 0);
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "25:99")))
                .isEqualTo(openedAt.plusHours(12));
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, "")))
                .isEqualTo(openedAt.plusHours(12));
        assertThat(service.computeExpiresAt(openedAt, hoursWith(DayOfWeek.MONDAY, false, null)))
                .isEqualTo(openedAt.plusHours(12));
    }

    @Test
    void isOverdue_trueOnlyWhenOpenAndPastEffectiveDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        assertThat(service.isOverdue(shift, deadline.minusMinutes(1))).isFalse();
        assertThat(service.isOverdue(shift, deadline.plusMinutes(1))).isTrue();

        CashShift prolonged = shiftExpiring(deadline, deadline.plusHours(1));
        assertThat(service.isOverdue(prolonged, deadline.plusMinutes(1))).isFalse();

        CashShift closed = shiftExpiring(deadline, null);
        closed.setStatus(CashShiftStatus.CLOSED);
        assertThat(service.isOverdue(closed, deadline.plusHours(9))).isFalse();
    }

    @Test
    void prolong_fromBeforeDeadline_addsOneHourToDeadline() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        assertThat(service.prolong(shift, deadline.minusMinutes(30)))
                .isEqualTo(deadline.plusHours(1));
    }

    @Test
    void prolong_fromAfterDeadline_addsOneHourToNow() {
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = shiftExpiring(deadline, null);
        LocalDateTime now = deadline.plusHours(3);
        assertThat(service.prolong(shift, now)).isEqualTo(now.plusHours(1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftDeadlineServiceTest`
Expected: FAIL — `CashShiftDeadlineService` / `CashShift.getExpiresAt` / `getProlongedUntil` do not exist yet (compilation failure). That is an acceptable "red" for this step; Task 2 adds the entity fields. If you are running tasks in order and Task 2 is not done, temporarily add the getters is NOT needed — just proceed to Step 3 and the entity fields in Task 2; re-run at the end of Task 2.

> Note for the executor: Tasks 1 and 2 compile together. Implement Task 1's class now (Step 3), then Task 2's entity fields, then run both test classes green. The per-task commit for Task 1 happens after Task 2's fields exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.vanter.ember.cashregister.service;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings;
import com.vanter.ember.settings.model.SettingsPayload.BusinessHoursSettings.DaySchedule;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;

/**
 * Pure, dependency-free deadline math for a {@link CashShift}. The caller supplies {@code now}
 * and the tenant's {@link BusinessHoursSettings}; this class never reads a clock or a repository,
 * so every branch is unit-testable in isolation. A malformed or missing {@code closeTime} must
 * degrade to the 12h fallback, never throw — a bad settings value must not block payments.
 */
@Service
public class CashShiftDeadlineService {

    public static final Duration GRACE = Duration.ofHours(2);
    public static final Duration CLOSED_DAY_FALLBACK = Duration.ofHours(12);
    public static final Duration PROLONG_STEP = Duration.ofHours(1);

    public LocalDateTime computeExpiresAt(LocalDateTime openedAt, BusinessHoursSettings hours) {
        LocalTime closeTime = resolveCloseTime(openedAt, hours);
        if (closeTime == null) {
            return openedAt.plus(CLOSED_DAY_FALLBACK);
        }
        LocalDateTime candidate = openedAt.toLocalDate().atTime(closeTime);
        if (!candidate.isAfter(openedAt)) {
            candidate = candidate.plusDays(1);
        }
        return candidate.plus(GRACE);
    }

    public boolean isOverdue(CashShift shift, LocalDateTime now) {
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            return false;
        }
        LocalDateTime deadline = shift.effectiveDeadline();
        return deadline != null && now.isAfter(deadline);
    }

    public LocalDateTime prolong(CashShift shift, LocalDateTime now) {
        LocalDateTime base = shift.effectiveDeadline();
        if (base == null || now.isAfter(base)) {
            base = now;
        }
        return base.plus(PROLONG_STEP);
    }

    private LocalTime resolveCloseTime(LocalDateTime openedAt, BusinessHoursSettings hours) {
        if (hours == null || hours.getSchedule() == null) {
            return null;
        }
        DaySchedule day = hours.getSchedule().stream()
                .filter(d -> d.getDay() == openedAt.getDayOfWeek())
                .findFirst()
                .orElse(null);
        if (day == null || day.isClosed() || day.getCloseTime() == null || day.getCloseTime().isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(day.getCloseTime().trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
```

- [ ] **Step 4: (deferred) run after Task 2** — `cd backend && ./mvnw test -Dtest=CashShiftDeadlineServiceTest` → PASS once `CashShift` has the new fields/helpers.

- [ ] **Step 5: Commit** (after Task 2's entity fields compile)

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java \
        backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftDeadlineServiceTest.java
git commit -m "feat(cashregister): add pure cash shift deadline service"
```

---

## Task 2: `CashShift` schema & entity fields

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/model/CashShiftDeadlineFieldsTest.java` (create)

**Interfaces:**
- Consumes: nothing.
- Produces on `CashShift`: `LocalDateTime getExpiresAt()/setExpiresAt(...)`, `LocalDateTime getProlongedUntil()/setProlongedUntil(...)`, `String getProlongedBy()/setProlongedBy(...)`, `int getProlongCount()/setProlongCount(int)`, and derived `LocalDateTime effectiveDeadline()`, `LocalDate businessDay()`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.cashregister.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CashShiftDeadlineFieldsTest {

    @Test
    void effectiveDeadline_prefersProlongedUntilWhenPresent() {
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 31, 1, 0);
        CashShift shift = CashShift.builder().expiresAt(expiresAt).build();
        assertThat(shift.effectiveDeadline()).isEqualTo(expiresAt);

        shift.setProlongedUntil(expiresAt.plusHours(1));
        assertThat(shift.effectiveDeadline()).isEqualTo(expiresAt.plusHours(1));
    }

    @Test
    void businessDay_isOpenedAtDate() {
        CashShift shift = CashShift.builder()
                .openedAt(LocalDateTime.of(2026, 8, 31, 23, 30)).build();
        assertThat(shift.businessDay()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void prolongCount_defaultsToZero() {
        assertThat(CashShift.builder().build().getProlongCount()).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftDeadlineFieldsTest`
Expected: FAIL — `effectiveDeadline()` / `businessDay()` / `getExpiresAt()` undefined (compilation failure).

- [ ] **Step 3a: Add the migration**

Create `backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql`:

```sql
ALTER TABLE cash_shifts
    ADD COLUMN expires_at      TIMESTAMP,
    ADD COLUMN prolonged_until TIMESTAMP,
    ADD COLUMN prolonged_by    VARCHAR(255),
    ADD COLUMN prolong_count   INTEGER NOT NULL DEFAULT 0;

-- Give any shift that is OPEN at deploy time a finite deadline so the new
-- guards and the frontend sentinel have something to evaluate.
UPDATE cash_shifts
   SET expires_at = opened_at + INTERVAL '12 hours'
 WHERE status = 'OPEN' AND expires_at IS NULL;
```

- [ ] **Step 3b: Add the entity fields + derived helpers**

In `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java`, add imports `java.time.LocalDate`, then after the `total_cash_out` column add:

```java
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "prolonged_until")
    private LocalDateTime prolongedUntil;

    @Column(name = "prolonged_by")
    private String prolongedBy;

    @Column(name = "prolong_count", nullable = false)
    @Builder.Default
    private int prolongCount = 0;

    /** The deadline in force right now: the last prolong if any, else the base expiry. */
    public LocalDateTime effectiveDeadline() {
        return prolongedUntil != null ? prolongedUntil : expiresAt;
    }

    /** The calendar day this till belongs to, for daily-report attribution. */
    public LocalDate businessDay() {
        return openedAt == null ? null : openedAt.toLocalDate();
    }
```

> `@Builder.Default` is required so `CashShift.builder().build()` yields `prolongCount == 0` rather than leaving the primitive at its uninitialised-in-builder default. `lombok.Builder` is already imported.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=CashShiftDeadlineFieldsTest,CashShiftDeadlineServiceTest`
Expected: PASS (both). This also compiles Task 1's class + test.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql \
        backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java \
        backend/src/test/java/com/vanter/ember/cashregister/model/CashShiftDeadlineFieldsTest.java \
        backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java \
        backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftDeadlineServiceTest.java
git commit -m "feat(cashregister): add expiry/prolong columns and deadline service"
```

---

## Task 3: Overdue exception, prolong event, and 409 mapping

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/exception/CashShiftOverdueException.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftProlonged.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/listener/CashRegisterWebSocketListener.java`
- Test: `backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerOverdueTest.java` (create)

**Interfaces:**
- Produces:
  - `class CashShiftOverdueException extends RuntimeException` with `CashShiftOverdueException(String message)`.
  - `record CashShiftProlonged(UUID tenantId, Long shiftId)`.
  - `GlobalExceptionHandler.handleCashShiftOverdue(...)` → `ProblemDetail` status 409, `detail = ex.getMessage()`, property `code = "CASH_SHIFT_OVERDUE"`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.exception.CashShiftOverdueException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerOverdueTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void cashShiftOverdue_maps_to_409_with_stable_code() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/billing/payments/physical");

        ProblemDetail problem = handler.handleCashShiftOverdue(
                new CashShiftOverdueException("Cash shift is overdue; prolong or close it"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("Cash shift is overdue; prolong or close it");
        assertThat(problem.getProperties()).containsEntry("code", "CASH_SHIFT_OVERDUE");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerOverdueTest`
Expected: FAIL — `CashShiftOverdueException` and `handleCashShiftOverdue` do not exist.

- [ ] **Step 3a: Create the exception**

```java
package com.vanter.ember.cashregister.exception;

/**
 * Raised by the two write paths that stamp a payment/movement onto the open till
 * ({@code PaymentService.registerPhysicalPayment}, {@code CashShiftService.recordMovement})
 * when that till is past its effective deadline and has not been prolonged. Mapped to HTTP 409
 * with a stable {@code code} so the frontend can tell it apart from the "tables still open" 409.
 */
public class CashShiftOverdueException extends RuntimeException {
    public CashShiftOverdueException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3b: Create the event**

```java
package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftProlonged(UUID tenantId, Long shiftId) {}
```

- [ ] **Step 3c: Map the exception in `GlobalExceptionHandler`**

Add the import `import com.vanter.ember.cashregister.exception.CashShiftOverdueException;` and, next to `handleIllegalState`:

```java
    @ExceptionHandler(CashShiftOverdueException.class)
    public ProblemDetail handleCashShiftOverdue(CashShiftOverdueException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        problem.setProperty("code", "CASH_SHIFT_OVERDUE");
        return problem;
    }
```

- [ ] **Step 3d: Broadcast the event in `CashRegisterWebSocketListener`**

Add import `import com.vanter.ember.cashregister.event.CashShiftProlonged;` and a listener method mirroring the others:

```java
    @EventListener
    public void onShiftProlonged(CashShiftProlonged event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GlobalExceptionHandlerOverdueTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/exception/CashShiftOverdueException.java \
        backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftProlonged.java \
        backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java \
        backend/src/main/java/com/vanter/ember/cashregister/listener/CashRegisterWebSocketListener.java \
        backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerOverdueTest.java
git commit -m "feat(cashregister): add overdue exception, prolong event, 409 mapping"
```

---

## Task 4: `CashShiftService` wiring + `CashShiftResponse` fields

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`

**Interfaces:**
- Consumes: `CashShiftDeadlineService` (Task 1), `SettingService.getSettings(UUID).getPayload().getBusinessHours()`, `CashShiftProlonged` (Task 3), `CashShiftOverdueException` (Task 3).
- Produces:
  - `CashShift prolongShift(Long shiftId, String userId)` — pessimistic-locked, rejects non-`OPEN` with `IllegalStateException`, sets `prolongedUntil`/`prolongedBy`, increments `prolongCount`, publishes `CashShiftProlonged`, returns the saved shift.
  - `openShift(...)` now stamps `expiresAt`.
  - `recordMovement(...)` now throws `CashShiftOverdueException` when the shift is overdue.
  - `CashShiftResponse` gains trailing components: `LocalDateTime expiresAt`, `LocalDateTime effectiveDeadline`, `boolean overdue`, `LocalDate businessDay`, `int prolongCount`.

- [ ] **Step 1: Write the failing tests**

Add to `CashShiftServiceTest`. First widen the mock wiring — add fields:

```java
    @Mock com.vanter.ember.settings.service.SettingService settingService;
    @Mock com.vanter.ember.cashregister.service.CashShiftDeadlineService deadlineService;
```

(`@InjectMocks CashShiftService` picks these up once they are constructor args.)

Then add cases:

```java
    @Test
    void openShift_stampsExpiresAtFromDeadlineService() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(cashShiftRepository.findMaxShiftNumber(TENANT_ID)).thenReturn(0);
        var settings = new com.vanter.ember.settings.model.RestaurantSettings();
        settings.setPayload(new com.vanter.ember.settings.model.SettingsPayload());
        when(settingService.getSettings(TENANT_ID)).thenReturn(settings);
        LocalDateTime stamped = LocalDateTime.now().plusHours(9);
        when(deadlineService.computeExpiresAt(any(), any())).thenReturn(stamped);
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashShift shift = cashShiftService.openShift(TENANT_ID, "user-1", new BigDecimal("50.00"));

        assertThat(shift.getExpiresAt()).isEqualTo(stamped);
    }

    @Test
    void prolongShift_pushesDeadlineIncrementsCountAndPublishes() {
        CashShift open = openShift();
        open.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LocalDateTime pushed = LocalDateTime.now().plusHours(1);
        when(deadlineService.prolong(any(), any())).thenReturn(pushed);

        CashShift result = cashShiftService.prolongShift(1L, "user-7");

        assertThat(result.getProlongedUntil()).isEqualTo(pushed);
        assertThat(result.getProlongedBy()).isEqualTo("user-7");
        assertThat(result.getProlongCount()).isEqualTo(1);
        org.mockito.Mockito.verify(eventPublisher)
                .publishEvent(any(com.vanter.ember.cashregister.event.CashShiftProlonged.class));
    }

    @Test
    void prolongShift_throwsWhenShiftNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.prolongShift(1L, "user-7"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_throwsWhenShiftIsOverdue() {
        CashShift open = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(open));
        when(deadlineService.isOverdue(org.mockito.ArgumentMatchers.eq(open), any())).thenReturn(true);

        assertThatThrownBy(() -> cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_IN, new BigDecimal("10.00"), "tip"))
                .isInstanceOf(com.vanter.ember.cashregister.exception.CashShiftOverdueException.class);
    }

    @Test
    void toResponse_fillsOverdueAndBusinessDay() {
        CashShift open = openShift();
        open.setExpiresAt(open.getOpenedAt().plusHours(9));
        when(deadlineService.isOverdue(org.mockito.ArgumentMatchers.eq(open), any())).thenReturn(true);
        when(userRepository.findAllById(anyList())).thenReturn(List.of());

        var response = cashShiftService.toResponse(open);

        assertThat(response.overdue()).isTrue();
        assertThat(response.businessDay()).isEqualTo(open.getOpenedAt().toLocalDate());
        assertThat(response.effectiveDeadline()).isEqualTo(open.getExpiresAt());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`
Expected: FAIL — `prolongShift` undefined, `CashShiftResponse.overdue()` undefined, constructor arity mismatch.

- [ ] **Step 3a: Extend `CashShiftResponse`**

Append the five components (keep existing order; add at the end), add imports `java.time.LocalDate`:

```java
public record CashShiftResponse(
        Long id,
        int shiftNumber,
        String status,
        BigDecimal openingFloat,
        String openedByName,
        LocalDateTime openedAt,
        String closedByName,
        LocalDateTime closedAt,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        BigDecimal totalCashSales,
        BigDecimal totalDigitalSales,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        LocalDateTime expiresAt,
        LocalDateTime effectiveDeadline,
        boolean overdue,
        LocalDate businessDay,
        int prolongCount) {}
```

- [ ] **Step 3b: Wire `CashShiftService`**

Add fields to the constructor (via the existing `@RequiredArgsConstructor` — just add `private final`):

```java
    private final com.vanter.ember.settings.service.SettingService settingService;
    private final CashShiftDeadlineService deadlineService;
```

In `openShift`, before `cashShiftRepository.save(...)`, compute the expiry and set it on the builder:

```java
        var businessHours = settingService.getSettings(tenantId).getPayload().getBusinessHours();
        LocalDateTime openedAt = LocalDateTime.now();
        LocalDateTime expiresAt = deadlineService.computeExpiresAt(openedAt, businessHours);
        ...
            shift = cashShiftRepository.save(CashShift.builder()
                    .shiftNumber(nextShiftNumber)
                    .status(CashShiftStatus.OPEN)
                    .openingFloat(openingFloat)
                    .openedBy(openedByUserId)
                    .openedAt(openedAt)
                    .expiresAt(expiresAt)
                    .build());
```

In `recordMovement`, after the `status != OPEN` guard:

```java
        if (deadlineService.isOverdue(shift, LocalDateTime.now())) {
            throw new com.vanter.ember.cashregister.exception.CashShiftOverdueException(
                    "Cash shift " + shiftId + " is overdue; prolong or close it before recording a movement");
        }
```

Add `prolongShift`:

```java
    @Transactional
    public CashShift prolongShift(Long shiftId, String userId) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }
        shift.setProlongedUntil(deadlineService.prolong(shift, LocalDateTime.now()));
        shift.setProlongedBy(userId);
        shift.setProlongCount(shift.getProlongCount() + 1);
        CashShift saved = cashShiftRepository.save(shift);
        eventPublisher.publishEvent(
                new com.vanter.ember.cashregister.event.CashShiftProlonged(shift.getTenantId(), shiftId));
        return saved;
    }
```

In **both** `toResponse(CashShift)` overloads' final `new CashShiftResponse(...)` call, append the five values:

```java
                shift.getTotalCashIn(), shift.getTotalCashOut(),
                shift.getExpiresAt(), shift.effectiveDeadline(),
                deadlineService.isOverdue(shift, LocalDateTime.now()),
                shift.businessDay(), shift.getProlongCount());
```

(There is one private `toResponse(CashShift, Map)` that builds the record — update that single call site; the public `toResponse(CashShift)` delegates to it.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest,CashShiftDeadlineServiceTest,CashShiftDeadlineFieldsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java \
        backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java \
        backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java
git commit -m "feat(cashregister): stamp expiry on open, add prolong, guard movements"
```

---

## Task 5: `POST /cash-shifts/{id}/prolong` endpoint

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java` (create)

**Interfaces:**
- Consumes: `CashShiftService.prolongShift(Long, String)` (Task 4), `CashShiftService.toResponse(CashShift)`.
- Produces: `POST /cash-shifts/{id}/prolong` → `200 CashShiftResponse`, `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.cashregister.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.vanter.ember.config.GlobalExceptionHandler;

@WebMvcTest(controllers = CashShiftController.class)
@Import(GlobalExceptionHandler.class)
class CashShiftControllerProlongTest {

    @Autowired MockMvc mockMvc;
    @MockBean CashShiftService cashShiftService;
    @MockBean UserRepository userRepository;

    private CashShift openShift() {
        return CashShift.builder().id(5L).status(CashShiftStatus.OPEN)
                .shiftNumber(1).openingFloat(new BigDecimal("0.00"))
                .openedBy("u1").openedAt(LocalDateTime.now().minusHours(2)).build();
    }

    @Test
    void prolong_returns200AndResponse() throws Exception {
        when(userRepository.findByEmail("waiter@x.com"))
                .thenReturn(Optional.of(User.builder().id("u1").build()));
        when(cashShiftService.prolongShift(eq(5L), eq("u1"))).thenReturn(openShift());
        when(cashShiftService.toResponse(org.mockito.ArgumentMatchers.any(CashShift.class)))
                .thenReturn(new com.vanter.ember.cashregister.dto.CashShiftResponse(
                        5L, 1, "OPEN", new BigDecimal("0.00"), "Ana",
                        LocalDateTime.now().minusHours(2), null, null, null, null, null,
                        null, null, null, null,
                        LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(1),
                        false, LocalDate.now(), 1));

        mockMvc.perform(post("/cash-shifts/5/prolong")
                        .with(user("waiter@x.com").roles("WAITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prolongCount").value(1));
    }

    @Test
    void prolong_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/cash-shifts/5/prolong")
                        .with(user("cust@x.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
```

> If `@WebMvcTest` slices pull in unrelated security config that fails to load (this repo wires JWT + tenant filters), fall back to a `@SpringBootTest + @AutoConfigureMockMvc` test in the same file shape, still asserting `200` + `jsonPath("$.prolongCount")` and `403` for CUSTOMER. Match whichever style the nearest existing controller test uses (`PrintAgentAuthControllerTest`).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerProlongTest`
Expected: FAIL — no handler mapped for `POST /cash-shifts/5/prolong` (404).

- [ ] **Step 3: Add the endpoint**

In `CashShiftController`, after `recordMovement`:

```java
    @Operation(summary = "Prolong an open shift's deadline by one hour (WAITER/ADMIN)")
    @PostMapping("/{id}/prolong")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public CashShiftResponse prolong(@PathVariable Long id, Authentication authentication) {
        CashShift shift = cashShiftService.prolongShift(id, resolveUserId(authentication));
        return cashShiftService.toResponse(shift);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerProlongTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java \
        backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java
git commit -m "feat(cashregister): add POST /cash-shifts/{id}/prolong"
```

---

## Task 6: `PaymentService` overdue guard on physical payments

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- Test: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: `CashShiftDeadlineService.isOverdue(CashShift, LocalDateTime)` (Task 1).
- Produces: `registerPhysicalPayment(...)` throws `CashShiftOverdueException` when the open shift is overdue; `initiateDigitalPayment(...)` / `confirmDigitalPayment(...)` unchanged.

- [ ] **Step 1: Write the failing test**

Add to `PaymentServiceTest` (match its existing mock-wiring style). Add mock field:

```java
    @Mock com.vanter.ember.cashregister.service.CashShiftDeadlineService deadlineService;
```

Cases:

```java
    @Test
    void registerPhysicalPayment_throwsWhenOpenShiftIsOverdue() {
        var shift = com.vanter.ember.cashregister.model.CashShift.builder()
                .id(9L).status(com.vanter.ember.cashregister.model.CashShiftStatus.OPEN)
                .openedAt(java.time.LocalDateTime.now().minusHours(20)).build();
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(java.util.Optional.of(shift));
        when(deadlineService.isOverdue(org.mockito.ArgumentMatchers.eq(shift), any())).thenReturn(true);

        assertThatThrownBy(() -> paymentService.registerPhysicalPayment(
                1L, "Ana", new java.math.BigDecimal("10.00"), "waiter@x.com"))
                .isInstanceOf(com.vanter.ember.cashregister.exception.CashShiftOverdueException.class);
        verifyNoInteractions(billRepository);
    }

    @Test
    void initiateDigitalPayment_isUnaffectedByOverdueShift() {
        // digital path never calls findOpenForUpdate / deadlineService — a happy-path digital
        // test already exists; assert deadlineService is never consulted here.
        // (Add this assertion to the existing initiateDigitalPayment happy-path test:)
        // verifyNoInteractions(deadlineService);
    }
```

> Adapt names (`billRepository`, `paymentService`, `verifyNoInteractions` import) to what `PaymentServiceTest` already declares. If the existing digital-payment happy-path test is named differently, add `verifyNoInteractions(deadlineService);` to it rather than creating a stub test.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PaymentServiceTest`
Expected: FAIL — constructor arity mismatch / guard not present.

- [ ] **Step 3: Add the guard**

In `PaymentService`, add the dependency (via `@RequiredArgsConstructor`):

```java
    private final com.vanter.ember.cashregister.service.CashShiftDeadlineService deadlineService;
```

In `registerPhysicalPayment`, immediately after the `findOpenForUpdate(...)` lookup:

```java
        if (deadlineService.isOverdue(shift, java.time.LocalDateTime.now())) {
            throw new com.vanter.ember.cashregister.exception.CashShiftOverdueException(
                    "Cash shift is overdue; prolong or close it before registering a physical payment");
        }
```

(Prefer a top-of-file import for `CashShiftOverdueException` and `LocalDateTime` — `LocalDateTime` is already imported. Match the file's existing import style rather than fully-qualified names in the final code.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PaymentServiceTest`
Expected: PASS

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS (all). Fix any fixture that constructs `CashShiftResponse` positionally or `new CashShiftService(...)` / `new PaymentService(...)` directly — add the new trailing args / mocks.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java \
        backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java
git commit -m "feat(billing): reject physical payment when cash shift is overdue"
```

---

## Task 7: OpenAPI regen + `cashShiftService.prolong` client

**Files:**
- Modify: `frontend/src/lib/backend-types.ts` (regenerated)
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Produces: `cashShiftService.prolong(id: number): Promise<CashShiftResponse>` → `POST /cash-shifts/${id}/prolong`.
- `CashShiftResponse` type (from `components['schemas']['CashShiftResponse']`) now carries `expiresAt`, `effectiveDeadline`, `overdue`, `businessDay`, `prolongCount`.

- [ ] **Step 1: Regenerate backend types**

The backend must be running or the OpenAPI JSON reachable. Use the repo's existing generation command — check `frontend/package.json` scripts for one matching `openapi`/`types`/`generate` (e.g. `pnpm run gen:types`). Run it. If there is no script, locate how `backend-types.ts` is currently produced (header comment in the file names the tool) and run that exact command.

Verify the diff adds the five fields to `CashShiftResponse` and nothing unrelated.

- [ ] **Step 2: Add the client method**

In `frontend/src/lib/api.ts`, inside the `cashShiftService` object, after `close`:

```ts
  prolong: async (id: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>(`/cash-shifts/${id}/prolong`)
    return data
  },
```

- [ ] **Step 3: Type-check**

Run: `cd frontend && pnpm run build`
Expected: PASS (tsc clean). If `CashShiftResponse` consumers break on the new non-optional `overdue`, they are generated as optional (`overdue?: boolean`) by the OpenAPI tool for Java primitives — handle with `?? false` at call sites in later tasks, do not hand-edit the generated file.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/backend-types.ts frontend/src/lib/api.ts
git commit -m "feat(frontend): regenerate types, add cashShiftService.prolong"
```

---

## Task 8: `deriveCashShiftAlert` pure helper

**Files:**
- Create: `frontend/src/lib/cashShiftAlert.ts`
- Test: `frontend/src/lib/cashShiftAlert.test.ts`

**Interfaces:**
- Produces:
  - `type CashShiftAlert = 'IDLE' | 'PRE_WARNING' | 'OVERDUE' | 'STALE'`
  - `deriveCashShiftAlert(shift: CashShiftResponse | null, now: Date): CashShiftAlert`
  - `const PRE_WARNING_MS = 30 * 60_000`, `const REMINDER_INTERVAL_MS = 15 * 60_000`

Precedence (highest first): `STALE` (businessDay before today's local date) → `OVERDUE` (`shift.overdue === true`) → `PRE_WARNING` (`now >= effectiveDeadline - PRE_WARNING_MS` and `now < effectiveDeadline`) → `IDLE`. Null shift → `IDLE`.

- [ ] **Step 1: Write the failing test**

```ts
import { describe, it, expect } from 'vitest'
import { deriveCashShiftAlert, PRE_WARNING_MS } from './cashShiftAlert'
import type { CashShiftResponse } from './api'

const base = (over: Partial<CashShiftResponse>): CashShiftResponse =>
  ({
    id: 1,
    status: 'OPEN',
    overdue: false,
    businessDay: '2026-08-28',
    effectiveDeadline: '2026-08-28T23:00:00',
    expiresAt: '2026-08-28T23:00:00',
    prolongCount: 0,
    ...over,
  }) as CashShiftResponse

describe('deriveCashShiftAlert', () => {
  const now = new Date('2026-08-28T12:00:00')

  it('returns IDLE when there is no shift', () => {
    expect(deriveCashShiftAlert(null, now)).toBe('IDLE')
  })

  it('returns IDLE well before the deadline', () => {
    expect(deriveCashShiftAlert(base({}), now)).toBe('IDLE')
  })

  it('returns PRE_WARNING inside the 30-minute window before the deadline', () => {
    const deadline = new Date(now.getTime() + PRE_WARNING_MS - 60_000).toISOString()
    expect(deriveCashShiftAlert(base({ effectiveDeadline: deadline }), now)).toBe('PRE_WARNING')
  })

  it('returns OVERDUE when the server flags it, regardless of clock', () => {
    expect(deriveCashShiftAlert(base({ overdue: true }), now)).toBe('OVERDUE')
  })

  it('returns STALE when businessDay is before today (local), outranking OVERDUE', () => {
    expect(
      deriveCashShiftAlert(base({ businessDay: '2026-08-27', overdue: true }), now)
    ).toBe('STALE')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm run test -- cashShiftAlert`
Expected: FAIL — module not found.

- [ ] **Step 3: Write the implementation**

```ts
import type { CashShiftResponse } from './api'

export type CashShiftAlert = 'IDLE' | 'PRE_WARNING' | 'OVERDUE' | 'STALE'

export const PRE_WARNING_MS = 30 * 60_000
export const REMINDER_INTERVAL_MS = 15 * 60_000

const localYmd = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const deriveCashShiftAlert = (
  shift: CashShiftResponse | null,
  now: Date
): CashShiftAlert => {
  if (!shift) return 'IDLE'

  if (shift.businessDay && shift.businessDay < localYmd(now)) return 'STALE'
  if (shift.overdue) return 'OVERDUE'

  if (shift.effectiveDeadline) {
    const deadline = new Date(shift.effectiveDeadline).getTime()
    if (now.getTime() >= deadline - PRE_WARNING_MS && now.getTime() < deadline) {
      return 'PRE_WARNING'
    }
  }
  return 'IDLE'
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm run test -- cashShiftAlert`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/cashShiftAlert.ts frontend/src/lib/cashShiftAlert.test.ts
git commit -m "feat(frontend): add pure cash shift alert state helper"
```

---

## Task 9: `<CashShiftSentinel/>` component + layout mount

**Files:**
- Create: `frontend/src/components/CashShiftSentinel.tsx`
- Modify: `frontend/src/layouts/WaiterLayout.tsx`, `frontend/src/layouts/AdminLayout.tsx`
- Modify: `frontend/src/pages/waiter/cashRegister/CashRegister.tsx` (shared query `refetchInterval`)
- Modify: `frontend/src/locales/{es,en}/waiter.ts`

**Interfaces:**
- Consumes: `deriveCashShiftAlert`, `REMINDER_INTERVAL_MS` (Task 8); `cashShiftService.current`, `cashShiftService.prolong`, `cashShiftService.detail` (Task 7); `useUIStore().openModal` with `'CLOSE_SHIFT'` / `'OPEN_SHIFT'` (existing).
- Produces: `<CashShiftSentinel/>` (default export not required; named export). Renders nothing when alert is `IDLE`.

**Behaviour:**
- `useQuery(['cashShiftCurrent'], cashShiftService.current, { refetchInterval: 60_000, refetchOnWindowFocus: true })`.
- Local `now` state advanced every 30 s via `setInterval` so the modal appears between refetches.
- `alert = deriveCashShiftAlert(shift ?? null, now)`.
- `dismissedUntil` ref (a timestamp). `PRE_WARNING` and `OVERDUE` modals are closable; on close set `dismissedUntil = Date.now() + REMINDER_INTERVAL_MS`; suppress those two states while `Date.now() < dismissedUntil`. `STALE` is never dismissible and ignores `dismissedUntil`.
- `prolongMutation` = `useMutation(() => cashShiftService.prolong(shift!.id!))`, `onSuccess` → `queryClient.invalidateQueries(['cashShiftCurrent'])`, clear `dismissedUntil`, `toast.success(t('cashShiftProlongedToast'))`; `onError` → `toast.error(t('cashShiftProlongErrorToast'))`.
- "Cerrar caja" button → `openModal('CLOSE_SHIFT', { shiftId: shift.id })`.
- **STALE** modal body: fetch `useQuery(['cashShiftDetail', shift.id], () => cashShiftService.detail(shift.id!), { enabled: alert === 'STALE' })` and render movement + payment counts and a compact list (reuse the plain `<table>` markup from `CashRegister.tsx`, read-only). After the close modal succeeds and `cashShiftService.current` returns `null`, show a follow-up dialog with a single **`t('cashShiftOpenTodayButton')`** action → `openModal('OPEN_SHIFT')`.

- [ ] **Step 1: Write the component**

```tsx
import { useEffect, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'
import { deriveCashShiftAlert, REMINDER_INTERVAL_MS } from '@/lib/cashShiftAlert'

export const CashShiftSentinel = () => {
  const { t } = useTranslation('waiter')
  const { openModal } = useUIStore()
  const queryClient = useQueryClient()
  const [now, setNow] = useState(() => new Date())
  const dismissedUntil = useRef(0)

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 30_000)
    return () => clearInterval(id)
  }, [])

  const { data: shift } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
  })

  const alert = deriveCashShiftAlert(shift ?? null, now)

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', shift?.id],
    queryFn: () => cashShiftService.detail(shift!.id!),
    enabled: alert === 'STALE' && !!shift?.id,
  })

  const prolong = useMutation({
    mutationFn: () => cashShiftService.prolong(shift!.id!),
    onSuccess: () => {
      dismissedUntil.current = 0
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success(t('cashShiftProlongedToast'))
    },
    onError: () => toast.error(t('cashShiftProlongErrorToast')),
  })

  const snooze = () => { dismissedUntil.current = Date.now() + REMINDER_INTERVAL_MS }
  const closeShift = () => shift && openModal('CLOSE_SHIFT', { shiftId: shift.id })

  const suppressed = Date.now() < dismissedUntil.current
  const showPreWarning = alert === 'PRE_WARNING' && !suppressed
  const showOverdue = alert === 'OVERDUE' && !suppressed
  const showStale = alert === 'STALE'

  if (!shift) return null

  const deadlineLabel = shift.effectiveDeadline
    ? new Date(shift.effectiveDeadline).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    : ''

  return (
    <>
      <AlertDialog open={showPreWarning} onOpenChange={(o) => !o && snooze()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('cashShiftPreWarningTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftPreWarningBody', { time: deadlineLabel })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={snooze}>{t('cashShiftLaterButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => prolong.mutate()} disabled={prolong.isPending}>
              {t('cashShiftProlongButton')}
            </AlertDialogAction>
            <AlertDialogAction onClick={closeShift}>{t('cashShiftCloseButton')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showOverdue} onOpenChange={(o) => !o && snooze()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('cashShiftOverdueTitle')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftOverdueBody', { time: deadlineLabel })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={snooze}>{t('cashShiftLaterButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={() => prolong.mutate()} disabled={prolong.isPending}>
              {t('cashShiftProlongButton')}
            </AlertDialogAction>
            <AlertDialogAction onClick={closeShift}>{t('cashShiftCloseButton')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={showStale}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t('cashShiftStaleTitle', { date: shift.businessDay ?? '' })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t('cashShiftStaleBody', { date: shift.businessDay ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="max-h-64 overflow-y-auto text-sm">
            <p className="font-medium">
              {t('cashShiftStaleMovementsCount', { count: detail?.movements?.length ?? 0 })}
            </p>
            <p className="font-medium">
              {t('cashShiftStalePaymentsCount', { count: detail?.payments?.length ?? 0 })}
            </p>
          </div>
          <AlertDialogFooter>
            <AlertDialogAction onClick={closeShift}>
              {t('cashShiftStaleCloseButton', { date: shift.businessDay ?? '' })}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
```

> Verify `@/components/ui/alert-dialog` exists (shadcn). If the repo only has `@/components/ui/dialog`, use that instead with `onOpenChange` guarded, matching `ChargeTableModal.tsx`. Do not add a new shadcn dependency — reuse what `frontend/src/components/ui/` already ships.

> The "open today" follow-up: after `closeShift` the `CLOSE_SHIFT` modal (existing `CloseShiftDialog`) already invalidates `cashShiftCurrent`. When `shift` becomes `null` the sentinel returns `null` and no follow-up shows. Add a minimal follow-up only if the user confirms they want the auto-prompt — otherwise the waiter opens the new shift from the existing empty-state button in `CashRegister.tsx`. Mark this sub-feature as **deferred pending confirmation** (see Open Questions in the spec — actually resolved: keep it simple, so the follow-up prompt is OUT for v1).

- [ ] **Step 2: Mount in layouts**

`WaiterLayout.tsx` — add import and place `<CashShiftSentinel />` next to `<FloatingNav />`:

```tsx
import { CashShiftSentinel } from '@/components/CashShiftSentinel'
// ...
      <FloatingNav />
      <CashShiftSentinel />
```

`AdminLayout.tsx` — same, in the returned tree after `<FloatingNav />` (only in the non-onboarding branch).

- [ ] **Step 3: Bump the shared query interval in `CashRegister.tsx`**

Change the `['cashShiftCurrent']` query to include `refetchInterval: 60_000`. (Harmless duplication of the sentinel's config; keeps the page fresh when open.)

- [ ] **Step 4: Add i18n keys**

Add to `frontend/src/locales/es/waiter.ts` (and English mirror in `frontend/src/locales/en/waiter.ts`):

```ts
  cashShiftPreWarningTitle: 'La caja está por cerrarse',
  cashShiftPreWarningBody: 'La caja se cerrará a las {{time}}. Recuerda cerrarla o prolongarla.',
  cashShiftOverdueTitle: 'La caja venció',
  cashShiftOverdueBody:
    'La caja venció a las {{time}}. Los cobros en efectivo y los movimientos están bloqueados hasta que la cierres. La atención de mesas sigue disponible.',
  cashShiftStaleTitle: 'La caja del {{date}} nunca se cerró',
  cashShiftStaleBody:
    'Todo lo registrado desde entonces se está sumando al turno del {{date}}. Ciérralo antes de empezar el día.',
  cashShiftStaleMovementsCount: '{{count}} movimiento(s) acumulado(s)',
  cashShiftStalePaymentsCount: '{{count}} pago(s) acumulado(s)',
  cashShiftStaleCloseButton: 'Cerrar caja del {{date}}',
  cashShiftProlongButton: 'Prolongar 1 h',
  cashShiftCloseButton: 'Cerrar caja',
  cashShiftLaterButton: 'Ahora no',
  cashShiftProlongedToast: 'Caja prolongada una hora más.',
  cashShiftProlongErrorToast: 'No se pudo prolongar la caja.',
  cashShiftOpenTodayButton: 'Abrir turno de hoy',
```

English mirror (same keys):

```ts
  cashShiftPreWarningTitle: 'The cash register is about to close',
  cashShiftPreWarningBody: 'The register closes at {{time}}. Close it or extend it.',
  cashShiftOverdueTitle: 'The cash register has expired',
  cashShiftOverdueBody:
    'The register expired at {{time}}. Cash payments and movements are blocked until you close it. Table service still works.',
  cashShiftStaleTitle: "The {{date}} register was never closed",
  cashShiftStaleBody:
    "Everything recorded since then is being added to the {{date}} shift. Close it before starting the day.",
  cashShiftStaleMovementsCount: '{{count}} movement(s) accumulated',
  cashShiftStalePaymentsCount: '{{count}} payment(s) accumulated',
  cashShiftStaleCloseButton: 'Close the {{date}} register',
  cashShiftProlongButton: 'Extend 1 h',
  cashShiftCloseButton: 'Close register',
  cashShiftLaterButton: 'Not now',
  cashShiftProlongedToast: 'Register extended by one hour.',
  cashShiftProlongErrorToast: 'Could not extend the register.',
  cashShiftOpenTodayButton: "Open today's shift",
```

- [ ] **Step 5: Verify**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: PASS both.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/CashShiftSentinel.tsx \
        frontend/src/layouts/WaiterLayout.tsx frontend/src/layouts/AdminLayout.tsx \
        frontend/src/pages/waiter/cashRegister/CashRegister.tsx \
        frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): add cash shift sentinel with pre-warning/overdue/stale modals"
```

---

## Task 10: Logout warning when overdue

**Files:**
- Modify: `frontend/src/components/FloatingNav.tsx`
- Modify: `frontend/src/locales/{es,en}/common.ts`

**Interfaces:**
- Consumes: `useQueryClient().getQueryData(['cashShiftCurrent'])` typed as `CashShiftResponse | undefined`.
- Produces: logout is intercepted by a confirm `AlertDialog` **only** when `shift?.overdue === true`; confirming runs the existing `logout()` + `navigate('/login')`.

- [ ] **Step 1: Write the change**

In `FloatingNav.tsx`, add state + read the cached shift:

```tsx
import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import type { CashShiftResponse } from '@/lib/api'
// ...
  const queryClient = useQueryClient()
  const [confirmLogout, setConfirmLogout] = useState(false)

  const doLogout = () => {
    logout()
    navigate('/login')
  }

  const handleLogout = () => {
    const shift = queryClient.getQueryData<CashShiftResponse | null>(['cashShiftCurrent'])
    if (shift?.overdue) {
      setConfirmLogout(true)
      return
    }
    doLogout()
  }
```

Add the dialog near the end of the returned JSX (inside the `<nav>` or as a sibling fragment):

```tsx
      <AlertDialog open={confirmLogout} onOpenChange={setConfirmLogout}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('logoutCashShiftOverdueTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{t('logoutCashShiftOverdueBody')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>{t('logoutCashShiftBackButton')}</AlertDialogCancel>
            <AlertDialogAction onClick={doLogout}>{t('logoutCashShiftConfirmButton')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
```

Wrap the `return` in a fragment if it is not already, since the dialog is a sibling of `<nav>`.

- [ ] **Step 2: Add i18n keys**

`frontend/src/locales/es/common.ts`:

```ts
  logoutCashShiftOverdueTitle: 'La caja venció y sigue abierta',
  logoutCashShiftOverdueBody:
    'Debe cerrarse para la jornada de hoy. ¿Cerrar sesión de todos modos?',
  logoutCashShiftConfirmButton: 'Cerrar sesión',
  logoutCashShiftBackButton: 'Volver',
```

`frontend/src/locales/en/common.ts`:

```ts
  logoutCashShiftOverdueTitle: 'The cash register expired and is still open',
  logoutCashShiftOverdueBody:
    "It must be closed for today's business day. Log out anyway?",
  logoutCashShiftConfirmButton: 'Log out',
  logoutCashShiftBackButton: 'Back',
```

- [ ] **Step 3: Verify**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: PASS both. Manually reason through: with no cached shift or `overdue` false, `handleLogout` behaves exactly as before.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/FloatingNav.tsx \
        frontend/src/locales/es/common.ts frontend/src/locales/en/common.ts
git commit -m "feat(frontend): warn on logout while cash shift is overdue"
```

---

## Task 11: Overdue feedback in payment + movement UI

**Files:**
- Modify: `frontend/src/pages/waiter/TableInformation.tsx`
- Modify: `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- Modify: `frontend/src/locales/{es,en}/waiter.ts`

**Interfaces:**
- Consumes: axios error shape — `error.response?.data?.code === 'CASH_SHIFT_OVERDUE'` (from Task 3's ProblemDetail property); `CashShiftResponse.overdue`.
- Produces: physical-payment failures caused by an overdue shift show a specific toast; the "Registrar movimiento" button is disabled with a tooltip while `overdue`.

- [ ] **Step 1: Physical-payment error branch in `TableInformation.tsx`**

Replace the `physicalPaymentMutation` `onError`:

```tsx
    onError: (error) => {
      const code =
        axios.isAxiosError(error) && (error.response?.data as { code?: string })?.code
      toast.error(code === 'CASH_SHIFT_OVERDUE'
        ? t('cashShiftOverduePaymentToast')
        : t('cashPaymentErrorToast'))
    },
```

Ensure `import axios from 'axios'` is present (the file may already import it; if not, add it).

- [ ] **Step 2: Disable movement button in `CashRegister.tsx` while overdue**

Where the `CASH_MOVEMENT` button is rendered:

```tsx
                <Button
                  variant="outline"
                  disabled={shift.overdue ?? false}
                  title={shift.overdue ? t('cashShiftOverdueMovementBlocked') : undefined}
                  onClick={() => openModal('CASH_MOVEMENT', { shiftId: shift.id })}
                >
                  {t('recordMovementButton')}
                </Button>
```

- [ ] **Step 3: Add i18n keys**

`es/waiter.ts`:

```ts
  cashShiftOverduePaymentToast: 'La caja venció. Prolóngala o ciérrala para cobrar.',
  cashShiftOverdueMovementBlocked: 'La caja venció. Prolóngala o ciérrala para registrar movimientos.',
```

`en/waiter.ts`:

```ts
  cashShiftOverduePaymentToast: 'The register expired. Extend or close it to charge.',
  cashShiftOverdueMovementBlocked: 'The register expired. Extend or close it to record movements.',
```

- [ ] **Step 4: Verify**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: PASS both.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/waiter/TableInformation.tsx \
        frontend/src/pages/waiter/cashRegister/CashRegister.tsx \
        frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): specific overdue feedback on payment and movement"
```

---

## Task 12: Full verification, squash, report, PROGRESS.md

**Files:**
- Create: `reports/259-feat-cash-shift-expiry-forced-close.md`
- Modify: `PROGRESS.md`

- [ ] **Step 1: Full backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS (all). Record the pass count.

- [ ] **Step 2: Full frontend verification**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test`
Expected: all PASS.

- [ ] **Step 3: Write the report**

Create `reports/259-feat-cash-shift-expiry-forced-close.md` with the CLAUDE.md §4 structure:
1. **Identification:** Report 259; Task: cash shift expiry & forced daily close; Predecessor: report 258 (hub-portable-minio-manual-verification).
2. **Objective:** one paragraph from this plan's Goal.
3. **Modified Files:** the full list from the File Structure section.
4. **What Changed?:** per-area technical summary (deadline service, columns + migration, 409 guard at two write paths, prolong endpoint, sentinel + three modals, logout warning).
5. **Why It Changed?:** stale shifts contaminate `getDailyReport`'s per-day attribution; staff log out routinely without closing; auto-close would fake the arqueo, so nag + block + guided morning close instead.

- [ ] **Step 4: Update `PROGRESS.md`**

- Set **Last Completed Task** to report 259 with a 2–3 sentence summary + `./mvnw test` count.
- Update **System Health** (frontend build/lint/test, backend test — all green).
- Add/check the Task Queue Status entry for this feature.
- Keep the file under 180 lines; overwrite the oldest obsolete context note if needed.

- [ ] **Step 5: Squash into one atomic commit**

Per CLAUDE.md §4 (one squashed commit per task). The per-task commits from Tasks 1–11 are local checkpoints on `emb-i18n-08`. Squash them plus this task's report/PROGRESS into a single commit:

```bash
git reset --soft <hash-of-commit-before-Task-1>   # the commit 6ecd9bd "docs: add ... design spec"
git add backend/src/main/java/com/vanter/ember/cashregister \
        backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java \
        backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java \
        backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql \
        backend/src/test/java/com/vanter/ember/cashregister \
        backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java \
        backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerOverdueTest.java \
        frontend/src/lib/api.ts frontend/src/lib/backend-types.ts \
        frontend/src/lib/cashShiftAlert.ts frontend/src/lib/cashShiftAlert.test.ts \
        frontend/src/components/CashShiftSentinel.tsx frontend/src/components/FloatingNav.tsx \
        frontend/src/layouts/WaiterLayout.tsx frontend/src/layouts/AdminLayout.tsx \
        frontend/src/pages/waiter/TableInformation.tsx \
        frontend/src/pages/waiter/cashRegister/CashRegister.tsx \
        frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts \
        frontend/src/locales/es/common.ts frontend/src/locales/en/common.ts \
        reports/259-feat-cash-shift-expiry-forced-close.md PROGRESS.md
git commit -m "feat(cashregister): expiry, forced daily close, and overdue guards"
```

Verify `git status` is clean of feature files and `git log --oneline -3` shows exactly one feature commit on top of the spec commit.

- [ ] **Step 6: Final confirmation**

Run: `cd backend && ./mvnw test` and `cd frontend && pnpm run build` once more on the squashed tree.
Expected: PASS both. Report done; remind the user to `/clear`.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Task(s) |
|---|---|
| §4 constants (GRACE, CLOSED_DAY_FALLBACK, PROLONG_STEP, PRE_WARNING, REMINDER_INTERVAL) | 1 (backend), 8 (frontend) |
| §5.1 entity columns + derived helpers | 2 |
| §5.2 migration `V5__cash_shift_expiry.sql` + backfill | 2 |
| §5.3 `CashShiftDeadlineService` (weekday / overnight / closed / opened-after-close / malformed / prolong math) | 1 |
| §5.4 enforcement: `registerPhysicalPayment` 409 | 6 |
| §5.4 enforcement: `recordMovement` 409 | 4 |
| §5.4 `openShift` unchanged / `closeShift` unchanged | respected (no task touches them beyond stamping `expiresAt` on open) |
| §5.4 digital payments not guarded | 6 (explicit `verifyNoInteractions(deadlineService)`) |
| §5.4 `CashShiftOverdueException` → 409 + `code` | 3 |
| §5.5 `POST /cash-shifts/{id}/prolong` | 4 (service) + 5 (endpoint) |
| §5.6 `CashShiftResponse` +5 fields | 4 |
| §5.7 `CashShiftProlonged` event + listener broadcast | 3 |
| §6.1 `<CashShiftSentinel/>` mounted in Waiter/Admin, 60 s poll, 30 s tick | 9 |
| §6.2 PRE_WARNING / OVERDUE / STALE modals + dismiss→reminder | 8 (logic) + 9 (UI) |
| §6.2 STALE shows movements/payments from `detail` | 9 |
| §6.2 disable movement button while overdue | 11 |
| §6.3 logout warning only when `overdue`, non-blocking | 10 |
| §6.4 `cashShiftService.prolong` + type regen | 7 |
| §6.5 i18n es+en | 9, 10, 11 |
| §7 tests (deadline, service, payment, controller, sentinel state machine) | 1, 4, 5, 6, 8 |
| §8 clock authority / recomputed `overdue` per response | 4 (`toResponse` calls `isOverdue` with `now`) |

Gap check: the spec's §6.2 "follow-up: Abrir turno de hoy after close, prefilled with counted cash" is **explicitly dropped for v1** in Task 9 Step 1 (kept simple per the brainstorming decision "go simple"). The `cashShiftOpenTodayButton` key is added but wired only if the user later asks. No other gaps.

**2. Placeholder scan:** No "TBD"/"TODO"/"handle edge cases". Every code step has real code. The two "adapt to existing style" notes (Task 5 `@WebMvcTest` fallback, Task 6 mock field names) are explicit fallbacks with concrete alternatives, not placeholders.

**3. Type consistency:**
- `deriveCashShiftAlert(shift, now)` — same signature in Task 8 definition and Task 9 use.
- `cashShiftService.prolong(id: number): Promise<CashShiftResponse>` — Task 7 defines, Task 9 uses.
- `CashShiftDeadlineService.isOverdue(CashShift, LocalDateTime)` / `computeExpiresAt(LocalDateTime, BusinessHoursSettings)` / `prolong(CashShift, LocalDateTime)` — identical across Tasks 1, 4, 6.
- `CashShift.effectiveDeadline()` / `businessDay()` / `getProlongCount()` — defined Task 2, used Tasks 1, 4.
- `CashShiftProlonged(UUID, Long)` — defined Task 3, used Tasks 3 (listener), 4 (publish).
- ProblemDetail property key `"code"` with value `"CASH_SHIFT_OVERDUE"` — Task 3 sets, Task 11 reads.
- `CashShiftResponse` trailing component order (`expiresAt, effectiveDeadline, overdue, businessDay, prolongCount`) — Task 4 DTO matches Task 5 test's positional constructor call.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-28-cash-shift-expiry-forced-close.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**

> Note on commits: this plan uses per-task local checkpoint commits, then Task 12 squashes them into one atomic commit + one report (`reports/259-…`) per CLAUDE.md §4/§7. If you would rather keep per-task commits in history instead, say so and I will drop Task 12's squash step.
