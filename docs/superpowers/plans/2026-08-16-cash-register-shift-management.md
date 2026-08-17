# Cash Register & Daily Shift Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a cash-register/shift-management module (`EMB-CR` backlog) covering Apertura de Caja, Movimientos Manuales, Arqueo de Turnos (blind close) and Corte Diario (Z-Report), for the WAITER role to operate and the ADMIN role to oversee.

**Architecture:** A new `cashregister` backend module (Postgres/JPA, tenant-scoped like `billing`) with a two-state `CashShift` lifecycle (`OPEN → CLOSED`) enforced by a partial-unique-index single-till rule and `Bill`-style pessimistic locking; `Payment` gains `cashShiftId`/`processedBy` columns closing the codebase's only staff-attribution gap on a money record. Two new frontend pages (waiter operate, admin oversee) reuse existing shadcn/Zustand/TanStack Query conventions.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / Hibernate (`@TenantId` discriminator multi-tenancy) / Flyway / PostgreSQL — React 19 / TypeScript / TanStack Query 5 / Zustand 5 / shadcn (`radix-nova` style) / react-hook-form + zod.

**Spec:** `docs/superpowers/specs/2026-08-16-cash-register-shift-management-design.md`

## Global Constraints

- Base path: `@RequestMapping("/cash-shifts")` — no `/api` prefix (matches every real controller mapping under the global `/v1` context-path; `SecurityAuditTest`'s pre-existing `/api/...` rows are a stale inconsistency, not a convention — do not imitate them for new rows).
- Single shared till per tenant: at most one `OPEN` `CashShift` at a time, enforced by a partial unique index, not application logic alone.
- Blind close: the close request never accepts or echoes an "expected" figure — only `countedCash` goes in; `expected`/`counted`/`variance` come back in the response for immediate reveal.
- No `@ManyToOne` JPA relationship to `User` anywhere in this module (`openedBy`/`closedBy`/`createdBy`/`processedBy` are plain `String` columns holding `User#id`). `User#restaurantId` is `LAZY` and `open-in-view` is `false` — embedding a `User` association risks `LazyInitializationException` on serialization for zero UI benefit. Resolve display names via a batched `UserRepository.findAllById` in the service layer instead.
- No new frontend WebSocket subscription for this module. `websocket.ts` holds exactly one `currentSubscription` slot shared by `subscribeToSession`/`subscribeToKitchen`/`subscribeToWaiter`; `WaiterLayout` already claims it for the whole `/waiter/*` subtree for occupancy updates. Adding `subscribeToCashRegister` from the new waiter page would silently steal that slot and never give it back (the layout's subscribe effect only fires once per mount), breaking the existing floor dashboard's real-time updates. The backend still broadcasts to `/topic/cash-register/{tenantId}` (harmless if unused, ready for a future fix to the shared-slot design) — the frontend instead relies on each mutation's own response to update its own screen, plus TanStack Query's default `refetchOnWindowFocus`. Do not add a frontend subscription as part of this plan.
- Every task below is also one task in the `ember/CLAUDE.md` sense: after the code/test steps, write `/reports/<NNN>-task-EMB-CR-0X-<slug>.md` (find the next number — `reports/` currently ends at 115; if the two tracks below run as parallel subagent streams, the orchestrator assigns numbers centrally as each task actually finishes, since parallel completion order isn't fixed at plan-writing time), update `PROGRESS.md`'s three sections, then make exactly one squashed commit (Conventional Commits, no `Co-authored-by`/AI signature, scoped `git add` — never `-A`/`.`). Verify with `cd backend && ./mvnw test` (backend tasks) or `cd frontend && pnpm run build` (frontend tasks) before committing.
- Backend tasks (EMB-CR-01 → EMB-CR-04) are strictly sequential (each `Consumes` the previous). Frontend tasks (EMB-CR-05 → EMB-CR-07) are strictly sequential for the same reason (05 produces shared prep; 06 and 07 both touch `FloatingNav.tsx`/`App.tsx`, so they must not run concurrently with each other). The backend stream and frontend stream have no file overlap and no interface dependency on each other, so the two *streams* — not the seven tasks individually — are what can run in parallel.

---

### Task 1: EMB-CR-01 — Data layer (entities, migration, repositories)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/package-info.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/CashShiftStatus.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/CashMovementType.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/CashMovement.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/repository/CashShiftRepository.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/repository/CashMovementRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/model/Payment.java` (add `cashShiftId`, `processedBy`)
- Modify: `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java` (add 2 queries)
- Create: `backend/src/main/resources/db/migration/V7__cash_shifts.sql`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/repository/CashShiftRepositoryTenantIsolationTest.java`

**Interfaces:**
- Consumes: `com.vanter.ember.config.AbstractTenantIsolationTest` (`TENANT_A`/`TENANT_B`, `asTenant`/`readAs`, `@AfterEach` `deleteAll`) — existing, unmodified. `org.hibernate.annotations.TenantId` — existing Hibernate annotation used by `Bill`/`Payment`.
- Produces: `CashShiftStatus{OPEN,CLOSED}`, `CashMovementType{CASH_IN,CASH_OUT}`; `CashShift` (fields: `id:Long`, `tenantId:UUID`, `shiftNumber:int`, `status:CashShiftStatus`, `openingFloat:BigDecimal`, `openedBy:String`, `openedAt:LocalDateTime`, `closedBy:String`, `closedAt:LocalDateTime`, `expectedCash/countedCash/variance/totalCashSales/totalDigitalSales/totalCashIn/totalCashOut:BigDecimal`, all Lombok `@Data @Builder`); `CashMovement` (fields: `id:Long`, `tenantId:UUID`, `cashShiftId:Long`, `type:CashMovementType`, `amount:BigDecimal`, `reason:String`, `createdBy:String`, `createdAt:LocalDateTime`); `CashShiftRepository` methods `findByTenantIdAndStatus(UUID,CashShiftStatus):Optional<CashShift>`, `findByIdForUpdate(Long):Optional<CashShift>`, `findOpenForUpdate(UUID):Optional<CashShift>`, `findMaxShiftNumber(UUID):int`, `findByTenantIdAndOpenedAtBetweenOrderByOpenedAtDesc(UUID,LocalDateTime,LocalDateTime,Pageable):Page<CashShift>`, `findByTenantIdAndStatusAndClosedAtBetween(UUID,CashShiftStatus,LocalDateTime,LocalDateTime):List<CashShift>`; `CashMovementRepository` methods `findByCashShiftIdOrderByCreatedAtAsc(Long):List<CashMovement>`, `sumCashIn(Long):BigDecimal`, `sumCashOut(Long):BigDecimal`; `Payment.getCashShiftId():Long`/`getProcessedBy():String` (+ setters via `@Data`); `PaymentRepository.sumConfirmedPhysicalForShift(UUID,Long):BigDecimal`, `sumConfirmedDigitalInWindow(UUID,LocalDateTime,LocalDateTime):BigDecimal`.

- [ ] **Step 1: Write the failing repository test**

```java
package com.vanter.ember.cashregister.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CashShiftRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired CashShiftRepository cashShiftRepository;
    @Autowired CashMovementRepository cashMovementRepository;

    @Override
    protected void deleteAll() {
        cashMovementRepository.deleteAll();
        cashShiftRepository.deleteAll();
    }

    private CashShift openShiftFor(UUID tenantId, int shiftNumber) {
        return readAs(
                tenantId,
                () -> cashShiftRepository.save(
                        CashShift.builder()
                                .shiftNumber(shiftNumber)
                                .status(CashShiftStatus.OPEN)
                                .openingFloat(new BigDecimal("100.00"))
                                .openedBy("user-1")
                                .openedAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        CashShift saved = openShiftFor(TENANT_A, 1);

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void findByTenantIdAndStatus_doesNotLeakAnotherTenantsOpenShift() {
        openShiftFor(TENANT_A, 1);

        assertThat(readAs(TENANT_B,
                () -> cashShiftRepository.findByTenantIdAndStatus(TENANT_B, CashShiftStatus.OPEN)))
                .isEmpty();
        assertThat(readAs(TENANT_A,
                () -> cashShiftRepository.findByTenantIdAndStatus(TENANT_A, CashShiftStatus.OPEN)))
                .isPresent();
    }

    @Test
    void findMaxShiftNumber_isZeroWhenTenantHasNoShiftsYet() {
        assertThat(readAs(TENANT_B, () -> cashShiftRepository.findMaxShiftNumber(TENANT_B)))
                .isZero();
    }

    @Test
    void findMaxShiftNumber_ignoresAnotherTenantsShifts() {
        openShiftFor(TENANT_A, 5);

        assertThat(readAs(TENANT_B, () -> cashShiftRepository.findMaxShiftNumber(TENANT_B)))
                .isZero();
        assertThat(readAs(TENANT_A, () -> cashShiftRepository.findMaxShiftNumber(TENANT_A)))
                .isEqualTo(5);
    }

    private CashMovement movementOn(CashShift shift, UUID tenantId, CashMovementType type, String amount) {
        return readAs(
                tenantId,
                () -> cashMovementRepository.save(
                        CashMovement.builder()
                                .cashShiftId(shift.getId())
                                .type(type)
                                .amount(new BigDecimal(amount))
                                .reason("test movement")
                                .createdBy("user-1")
                                .createdAt(LocalDateTime.now())
                                .build()));
    }

    @Test
    void sumCashInAndSumCashOut_aggregateOnlyThatShiftsMovements() {
        CashShift shift = openShiftFor(TENANT_A, 1);
        movementOn(shift, TENANT_A, CashMovementType.CASH_IN, "20.00");
        movementOn(shift, TENANT_A, CashMovementType.CASH_IN, "5.00");
        movementOn(shift, TENANT_A, CashMovementType.CASH_OUT, "8.00");

        assertThat(readAs(TENANT_A, () -> cashMovementRepository.sumCashIn(shift.getId())))
                .isEqualByComparingTo("25.00");
        assertThat(readAs(TENANT_A, () -> cashMovementRepository.sumCashOut(shift.getId())))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void findByCashShiftIdOrderByCreatedAtAsc_returnsOldestFirst() {
        CashShift shift = openShiftFor(TENANT_A, 1);
        LocalDateTime now = LocalDateTime.now();
        readAs(TENANT_A, () -> cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shift.getId()).type(CashMovementType.CASH_IN)
                .amount(new BigDecimal("5.00")).reason("second").createdBy("user-1")
                .createdAt(now.plusMinutes(5)).build()));
        readAs(TENANT_A, () -> cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shift.getId()).type(CashMovementType.CASH_IN)
                .amount(new BigDecimal("5.00")).reason("first").createdBy("user-1")
                .createdAt(now).build()));

        List<CashMovement> movements = readAs(TENANT_A,
                () -> cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(shift.getId()));

        assertThat(movements).extracting(CashMovement::getReason).containsExactly("first", "second");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftRepositoryTenantIsolationTest`
Expected: FAIL to compile — `com.vanter.ember.cashregister.model.CashShift` and friends do not exist yet.

- [ ] **Step 3: Create the enums**

```java
package com.vanter.ember.cashregister.model;

public enum CashShiftStatus {
    OPEN,
    CLOSED
}
```

```java
package com.vanter.ember.cashregister.model;

public enum CashMovementType {
    CASH_IN,
    CASH_OUT
}
```

- [ ] **Step 4: Create the `cashregister` package-info and entities**

```java
/**
 * Cash register / daily shift management (Apertura de Caja, Movimientos Manuales, Arqueo de
 * Turnos, Corte Diario) — Postgres/JPA, tenant-scoped through {@code @TenantId} like {@code
 * billing}. See {@code docs/superpowers/specs/2026-08-16-cash-register-shift-management-design.md}.
 */
package com.vanter.ember.cashregister;
```

```java
package com.vanter.ember.cashregister.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A single shared till's lifecycle for one tenant: {@code OPEN} while trading, {@code CLOSED}
 * once its blind-count arqueo has run. At most one {@code OPEN} row may exist per tenant — see
 * {@code uk_cash_shifts_tenant_open} in {@code V7__cash_shifts.sql}. The financial columns below
 * {@code openedAt} are written exactly once, at close, and never revisited afterward — a {@code
 * CLOSED} row is this module's immutable Z-record; there is no separate report table.
 */
@Entity
@Table(name = "cash_shifts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "shift_number", nullable = false)
    private int shiftNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashShiftStatus status;

    @Column(name = "opening_float", nullable = false, precision = 10, scale = 2)
    private BigDecimal openingFloat;

    @Column(name = "opened_by", nullable = false)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "expected_cash", precision = 10, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "counted_cash", precision = 10, scale = 2)
    private BigDecimal countedCash;

    @Column(precision = 10, scale = 2)
    private BigDecimal variance;

    @Column(name = "total_cash_sales", precision = 10, scale = 2)
    private BigDecimal totalCashSales;

    @Column(name = "total_digital_sales", precision = 10, scale = 2)
    private BigDecimal totalDigitalSales;

    @Column(name = "total_cash_in", precision = 10, scale = 2)
    private BigDecimal totalCashIn;

    @Column(name = "total_cash_out", precision = 10, scale = 2)
    private BigDecimal totalCashOut;
}
```

```java
package com.vanter.ember.cashregister.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.TenantId;

/**
 * A manual cash in/out entry on a shift (float top-up, drop to safe, petty cash). Holds {@code
 * cashShiftId} as a plain column rather than a {@code @ManyToOne} — nothing here needs to
 * navigate back to the parent {@link CashShift} in Java, only to filter by its id, the same shape
 * {@code Payment#bill} vs. {@code Bill#sessionId} already mixes in this codebase depending on
 * whether navigation is actually needed.
 */
@Entity
@Table(name = "cash_movements", indexes = @Index(name = "idx_cash_movements_shift", columnList = "cash_shift_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "cash_shift_id", nullable = false)
    private Long cashShiftId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashMovementType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Extend `Payment` with the two new columns**

In `backend/src/main/java/com/vanter/ember/billing/model/Payment.java`, add after the existing `status`/`createdAt` fields (before the closing brace):

```java
    /** Set only for {@link PaymentMethod#PHYSICAL} payments — which shared till the cash landed in. */
    @Column(name = "cash_shift_id")
    private Long cashShiftId;

    /** {@code users.id} of whoever registered this payment, for both methods. */
    @Column(name = "processed_by")
    private String processedBy;
```

- [ ] **Step 6: Create the two new repositories**

```java
package com.vanter.ember.cashregister.repository;

import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashShiftRepository extends JpaRepository<CashShift, Long> {

    Optional<CashShift> findByTenantIdAndStatus(UUID tenantId, CashShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CashShift s where s.id = :id")
    Optional<CashShift> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from CashShift s
            where s.tenantId = :tenantId
              and s.status = com.vanter.ember.cashregister.model.CashShiftStatus.OPEN
            """)
    Optional<CashShift> findOpenForUpdate(@Param("tenantId") UUID tenantId);

    @Query("select coalesce(max(s.shiftNumber), 0) from CashShift s where s.tenantId = :tenantId")
    int findMaxShiftNumber(@Param("tenantId") UUID tenantId);

    Page<CashShift> findByTenantIdAndOpenedAtBetweenOrderByOpenedAtDesc(
            UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    List<CashShift> findByTenantIdAndStatusAndClosedAtBetween(
            UUID tenantId, CashShiftStatus status, LocalDateTime from, LocalDateTime to);
}
```

```java
package com.vanter.ember.cashregister.repository;

import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashMovementRepository extends JpaRepository<CashMovement, Long> {

    List<CashMovement> findByCashShiftIdOrderByCreatedAtAsc(Long cashShiftId);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.cashShiftId = :cashShiftId
              and m.type = com.vanter.ember.cashregister.model.CashMovementType.CASH_IN
            """)
    BigDecimal sumCashIn(@Param("cashShiftId") Long cashShiftId);

    @Query("""
            select coalesce(sum(m.amount), 0) from CashMovement m
            where m.cashShiftId = :cashShiftId
              and m.type = com.vanter.ember.cashregister.model.CashMovementType.CASH_OUT
            """)
    BigDecimal sumCashOut(@Param("cashShiftId") Long cashShiftId);
}
```

- [ ] **Step 7: Add the two new queries to `PaymentRepository`**

In `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`, add before the closing brace (add `import java.util.UUID;` if not already present — it already is):

```java

    /**
     * Confirmed PHYSICAL payments attributed to one shift — the sales half of that shift's
     * expected-cash figure at close (see {@code CashShiftService#closeShift}).
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId
              and p.cashShiftId = :cashShiftId
              and p.method = com.vanter.ember.billing.model.PaymentMethod.PHYSICAL
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedPhysicalForShift(
            @Param("tenantId") UUID tenantId, @Param("cashShiftId") Long cashShiftId);

    /**
     * Confirmed DIGITAL payments in a time window — DIGITAL payments carry no {@code
     * cashShiftId} (they're not physical cash), so a shift's digital-sales figure is windowed by
     * {@code openedAt}..{@code closedAt} instead of joined by id.
     */
    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.tenantId = :tenantId
              and p.method = com.vanter.ember.billing.model.PaymentMethod.DIGITAL
              and p.status = com.vanter.ember.billing.model.PaymentStatus.CONFIRMED
              and p.createdAt >= :from
              and p.createdAt <= :to
            """)
    BigDecimal sumConfirmedDigitalInWindow(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
```

- [ ] **Step 8: Write the migration, verifying `users.id`'s real column type first**

Run: `docker exec -it ember-postgres-1 psql -U ember -d ember -c "\d users"` (adjust container name if different — check `docker ps`) and confirm the `id` column's type. `User#id` is a Java `String` via `GenerationType.UUID` with no `columnDefinition` override, so Hibernate's default mapping is `varchar(255)` — the migration below assumes that. If the real column is instead `uuid`, change every `varchar(255)` FK-shaped column below (`opened_by`, `closed_by`, `created_by`, `processed_by`) to match before applying.

```sql
-- Cash Register & Daily Shift Management (2026-08-16 design spec) — single shared till per
-- tenant: at most one OPEN cash_shifts row at a time, enforced by the partial unique index below
-- rather than application logic alone. A CLOSED row's financial columns are written exactly once
-- (at close) and never revisited, so that row doubles as the shift's immutable Z-record — there
-- is no separate z_reports table.
--
-- opened_by/closed_by/created_by/processed_by store users.id directly (varchar, matching
-- User#id's GenerationType.UUID-as-String mapping) rather than a JPA @ManyToOne — User carries a
-- LAZY restaurantId association that would risk LazyInitializationException if embedded and
-- serialized here (open-in-view is false), and nothing in this module needs to navigate from a
-- shift/movement/payment back to the full User row in Java, only display a name via a lookup the
-- response-DTO layer performs explicitly.

CREATE TABLE IF NOT EXISTS cash_shifts (
    id                  bigserial PRIMARY KEY,
    tenant_id           uuid NOT NULL,
    shift_number        integer NOT NULL,
    status              varchar(10) NOT NULL,
    opening_float       numeric(10,2) NOT NULL,
    opened_by           varchar(255) NOT NULL,
    opened_at           timestamp NOT NULL,
    closed_by           varchar(255),
    closed_at           timestamp,
    expected_cash       numeric(10,2),
    counted_cash        numeric(10,2),
    variance            numeric(10,2),
    total_cash_sales    numeric(10,2),
    total_digital_sales numeric(10,2),
    total_cash_in       numeric(10,2),
    total_cash_out      numeric(10,2)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_cash_shifts_tenant_open
    ON cash_shifts (tenant_id)
    WHERE status = 'OPEN';

CREATE INDEX IF NOT EXISTS idx_cash_shifts_tenant_closed_at ON cash_shifts (tenant_id, closed_at);

CREATE TABLE IF NOT EXISTS cash_movements (
    id            bigserial PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    cash_shift_id bigint NOT NULL REFERENCES cash_shifts(id),
    type          varchar(10) NOT NULL,
    amount        numeric(10,2) NOT NULL,
    reason        varchar(255) NOT NULL,
    created_by    varchar(255) NOT NULL,
    created_at    timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cash_movements_shift ON cash_movements (cash_shift_id);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS cash_shift_id bigint REFERENCES cash_shifts(id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS processed_by varchar(255);
CREATE INDEX IF NOT EXISTS idx_payments_cash_shift ON payments (cash_shift_id);
```

Save as `backend/src/main/resources/db/migration/V7__cash_shifts.sql`.

- [ ] **Step 9: Run the test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CashShiftRepositoryTenantIsolationTest`
Expected: PASS (6 tests green). Note `spring.flyway.enabled=false` in the H2 test profile — this test exercises the entities via `ddl-auto=create-drop`, not the migration file itself; the migration's correctness against real Postgres is only proven by Step 8's manual verification and/or a real `./mvnw spring-boot:run` boot.

- [ ] **Step 10: Report, update PROGRESS.md, and commit**

Write `reports/<NNN>-task-EMB-CR-01-cash-shift-data-layer.md` per the CLAUDE.md report structure (Identification/Objective/Modified Files/What Changed/Why It Changed). Update `PROGRESS.md`'s three sections (mark EMB-CR-01 done, note the `users.id` column type actually found in Step 8, set Current Active Task to EMB-CR-02).

```bash
git add backend/src/main/java/com/vanter/ember/cashregister backend/src/main/java/com/vanter/ember/billing/model/Payment.java backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java backend/src/main/resources/db/migration/V7__cash_shifts.sql backend/src/test/java/com/vanter/ember/cashregister PROGRESS.md reports/<NNN>-task-EMB-CR-01-cash-shift-data-layer.md
git commit -m "feat(backend): add cash shift data layer and payment attribution columns"
```

---

### Task 2: EMB-CR-02 — `CashShiftService`, domain events, WebSocket broadcast

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashMovementResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftDetailResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/DailyReportResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftOpened.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftClosed.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/event/CashMovementRecorded.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/listener/CashRegisterWebSocketListener.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`

**Interfaces:**
- Consumes: Task 1's `CashShift`/`CashMovement`/`CashShiftStatus`/`CashMovementType`, `CashShiftRepository`, `CashMovementRepository`, `com.vanter.ember.billing.repository.PaymentRepository.sumConfirmedPhysicalForShift`/`sumConfirmedDigitalInWindow`; existing `com.vanter.ember.config.ResourceNotFoundException`, `com.vanter.ember.config.TenantContextHolder`, `com.vanter.ember.identity.repository.UserRepository` (`findAllById`), `com.vanter.ember.identity.model.User` (`getId`/`getName`).
- Produces: `CashShiftResponse` record (`id:Long, shiftNumber:int, status:String, openingFloat:BigDecimal, openedByName:String, openedAt:LocalDateTime, closedByName:String, closedAt:LocalDateTime, expectedCash/countedCash/variance/totalCashSales/totalDigitalSales/totalCashIn/totalCashOut:BigDecimal`); `CashMovementResponse` record (`id:Long, type:String, amount:BigDecimal, reason:String, createdByName:String, createdAt:LocalDateTime`); `CashShiftDetailResponse(shift:CashShiftResponse, movements:List<CashMovementResponse>)`; `DailyReportResponse(date:LocalDate, totalCashSales/totalDigitalSales/totalVariance/totalCashIn/totalCashOut:BigDecimal, shifts:List<CashShiftResponse>)`; events `CashShiftOpened(tenantId:UUID, shiftId:Long)`, `CashShiftClosed(tenantId:UUID, shiftId:Long)`, `CashMovementRecorded(tenantId:UUID, shiftId:Long)`; `CashShiftService` public methods — `openShift(String openedByUserId, BigDecimal openingFloat):CashShift`, `recordMovement(Long shiftId, String createdByUserId, CashMovementType type, BigDecimal amount, String reason):CashMovement`, `closeShift(Long shiftId, String closedByUserId, BigDecimal countedCash):CashShift`, `getCurrentOpenShift(UUID tenantId):CashShift`, `getById(Long id):CashShift`, `getDetail(Long id):CashShiftDetailResponse`, `getHistory(UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable):Page<CashShift>`, `getDailyReport(UUID tenantId, LocalDate date):DailyReportResponse`, `toResponse(CashShift shift):CashShiftResponse`, `toMovementResponse(CashMovement movement):CashMovementResponse`.

- [ ] **Step 1: Write the failing service test**

```java
package com.vanter.ember.cashregister.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CashShiftServiceTest {

    @Mock CashShiftRepository cashShiftRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks CashShiftService cashShiftService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private CashShift openShift() {
        return CashShift.builder()
                .id(1L).tenantId(TENANT_ID).shiftNumber(3).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now().minusHours(2)).build();
    }

    @Test
    void openShift_throwsWhenAShiftIsAlreadyOpen() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.of(openShift()));

        assertThatThrownBy(() -> cashShiftService.openShift("user-1", new BigDecimal("50.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_throwsWhenShiftIsNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recordMovement_savesAndPublishesEvent() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(cashMovementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashMovement movement = cashShiftService.recordMovement(
                1L, "user-1", CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop");

        assertThat(movement.getCashShiftId()).isEqualTo(1L);
        assertThat(movement.getType()).isEqualTo(CashMovementType.CASH_OUT);
    }

    @Test
    void closeShift_computesExpectedCashAndVariance() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(cashMovementRepository.sumCashIn(1L)).thenReturn(new BigDecimal("20.00"));
        when(cashMovementRepository.sumCashOut(1L)).thenReturn(new BigDecimal("5.00"));
        when(paymentRepository.sumConfirmedPhysicalForShift(TENANT_ID, 1L))
                .thenReturn(new BigDecimal("150.00"));
        when(paymentRepository.sumConfirmedDigitalInWindow(any(), any(), any()))
                .thenReturn(new BigDecimal("40.00"));
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashShift closed = cashShiftService.closeShift(1L, "user-2", new BigDecimal("260.00"));

        // expected = 100 (float) + 20 (in) - 5 (out) + 150 (cash sales) = 265
        assertThat(closed.getExpectedCash()).isEqualByComparingTo("265.00");
        assertThat(closed.getCountedCash()).isEqualByComparingTo("260.00");
        assertThat(closed.getVariance()).isEqualByComparingTo("-5.00");
        assertThat(closed.getTotalDigitalSales()).isEqualByComparingTo("40.00");
        assertThat(closed.getStatus()).isEqualTo(CashShiftStatus.CLOSED);
        assertThat(closed.getClosedBy()).isEqualTo("user-2");
    }

    @Test
    void closeShift_throwsWhenShiftIsNotOpen() {
        CashShift closed = openShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> cashShiftService.closeShift(1L, "user-2", new BigDecimal("0.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getCurrentOpenShift_throwsResourceNotFoundWhenNoneOpen() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashShiftService.getCurrentOpenShift(TENANT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`
Expected: FAIL to compile — `CashShiftService` does not exist yet.

- [ ] **Step 3: Write the response DTOs**

```java
package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        BigDecimal totalCashOut) {}
```

```java
package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CashMovementResponse(
        Long id, String type, BigDecimal amount, String reason, String createdByName,
        LocalDateTime createdAt) {}
```

```java
package com.vanter.ember.cashregister.dto;

import java.util.List;

public record CashShiftDetailResponse(CashShiftResponse shift, List<CashMovementResponse> movements) {}
```

```java
package com.vanter.ember.cashregister.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportResponse(
        LocalDate date,
        BigDecimal totalCashSales,
        BigDecimal totalDigitalSales,
        BigDecimal totalVariance,
        BigDecimal totalCashIn,
        BigDecimal totalCashOut,
        List<CashShiftResponse> shifts) {}
```

- [ ] **Step 4: Write the domain events and WebSocket listener**

```java
package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftOpened(UUID tenantId, Long shiftId) {}
```

```java
package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashShiftClosed(UUID tenantId, Long shiftId) {}
```

```java
package com.vanter.ember.cashregister.event;

import java.util.UUID;

public record CashMovementRecorded(UUID tenantId, Long shiftId) {}
```

```java
package com.vanter.ember.cashregister.listener;

import com.vanter.ember.cashregister.event.CashMovementRecorded;
import com.vanter.ember.cashregister.event.CashShiftClosed;
import com.vanter.ember.cashregister.event.CashShiftOpened;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts cash-shift lifecycle events tenant-wide, mirroring {@code WaiterWebSocketListener}'s
 * shape. No frontend page subscribes to this topic yet — see the "No new frontend WebSocket
 * subscription" global constraint in this plan — but the broadcast is harmless to ship ahead of
 * that and unblocks a future fix to the shared-subscription-slot limitation in {@code
 * websocket.ts}.
 */
@Component
@RequiredArgsConstructor
public class CashRegisterWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onShiftOpened(CashShiftOpened event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }

    @EventListener
    public void onShiftClosed(CashShiftClosed event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }

    @EventListener
    public void onMovementRecorded(CashMovementRecorded event) {
        messagingTemplate.convertAndSend("/topic/cash-register/" + event.tenantId(), event);
    }
}
```

- [ ] **Step 5: Write `CashShiftService`**

```java
package com.vanter.ember.cashregister.service;

import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.cashregister.dto.CashMovementResponse;
import com.vanter.ember.cashregister.dto.CashShiftDetailResponse;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.DailyReportResponse;
import com.vanter.ember.cashregister.event.CashMovementRecorded;
import com.vanter.ember.cashregister.event.CashShiftClosed;
import com.vanter.ember.cashregister.event.CashShiftOpened;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashShiftService {

    private static final LocalDateTime EPOCH_FLOOR = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final CashShiftRepository cashShiftRepository;
    private final CashMovementRepository cashMovementRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CashShift openShift(String openedByUserId, BigDecimal openingFloat) {
        UUID tenantId = com.vanter.ember.config.TenantContextHolder.requireTenantId();
        if (cashShiftRepository.findByTenantIdAndStatus(tenantId, CashShiftStatus.OPEN).isPresent()) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }

        int nextShiftNumber = cashShiftRepository.findMaxShiftNumber(tenantId) + 1;

        CashShift shift;
        try {
            shift = cashShiftRepository.save(CashShift.builder()
                    .shiftNumber(nextShiftNumber)
                    .status(CashShiftStatus.OPEN)
                    .openingFloat(openingFloat)
                    .openedBy(openedByUserId)
                    .openedAt(LocalDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }

        eventPublisher.publishEvent(new CashShiftOpened(tenantId, shift.getId()));
        return shift;
    }

    @Transactional
    public CashMovement recordMovement(
            Long shiftId, String createdByUserId, CashMovementType type, BigDecimal amount, String reason) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }

        CashMovement movement = cashMovementRepository.save(CashMovement.builder()
                .cashShiftId(shiftId)
                .type(type)
                .amount(amount)
                .reason(reason)
                .createdBy(createdByUserId)
                .createdAt(LocalDateTime.now())
                .build());

        eventPublisher.publishEvent(new CashMovementRecorded(shift.getTenantId(), shiftId));
        return movement;
    }

    @Transactional
    public CashShift closeShift(Long shiftId, String closedByUserId, BigDecimal countedCash) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }

        BigDecimal cashIn = cashMovementRepository.sumCashIn(shiftId);
        BigDecimal cashOut = cashMovementRepository.sumCashOut(shiftId);
        BigDecimal cashSales = paymentRepository.sumConfirmedPhysicalForShift(shift.getTenantId(), shiftId);
        LocalDateTime closedAt = LocalDateTime.now();
        BigDecimal digitalSales = paymentRepository.sumConfirmedDigitalInWindow(
                shift.getTenantId(), shift.getOpenedAt(), closedAt);

        BigDecimal expectedCash = shift.getOpeningFloat().add(cashIn).subtract(cashOut).add(cashSales);
        BigDecimal variance = countedCash.subtract(expectedCash);

        shift.setStatus(CashShiftStatus.CLOSED);
        shift.setClosedBy(closedByUserId);
        shift.setClosedAt(closedAt);
        shift.setExpectedCash(expectedCash);
        shift.setCountedCash(countedCash);
        shift.setVariance(variance);
        shift.setTotalCashSales(cashSales);
        shift.setTotalDigitalSales(digitalSales);
        shift.setTotalCashIn(cashIn);
        shift.setTotalCashOut(cashOut);

        CashShift saved = cashShiftRepository.save(shift);
        eventPublisher.publishEvent(new CashShiftClosed(shift.getTenantId(), shiftId));
        return saved;
    }

    public CashShift getCurrentOpenShift(UUID tenantId) {
        return cashShiftRepository.findByTenantIdAndStatus(tenantId, CashShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("No open cash shift for this tenant"));
    }

    public CashShift getById(Long id) {
        return cashShiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + id));
    }

    public CashShiftDetailResponse getDetail(Long id) {
        CashShift shift = getById(id);
        List<CashMovement> movements = cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(id);

        Set<String> userIds = new HashSet<>();
        userIds.add(shift.getOpenedBy());
        if (shift.getClosedBy() != null) userIds.add(shift.getClosedBy());
        movements.forEach(m -> userIds.add(m.getCreatedBy()));
        Map<String, String> names = resolveNames(userIds);

        List<CashMovementResponse> movementResponses =
                movements.stream().map(m -> toMovementResponse(m, names)).toList();

        return new CashShiftDetailResponse(toResponse(shift, names), movementResponses);
    }

    public Page<CashShift> getHistory(UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        LocalDateTime resolvedFrom = from != null ? from : EPOCH_FLOOR;
        LocalDateTime resolvedTo = to != null ? to : LocalDateTime.now();
        return cashShiftRepository.findByTenantIdAndOpenedAtBetweenOrderByOpenedAtDesc(
                tenantId, resolvedFrom, resolvedTo, pageable);
    }

    public DailyReportResponse getDailyReport(UUID tenantId, LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay().minusNanos(1);
        List<CashShift> shifts = cashShiftRepository
                .findByTenantIdAndStatusAndClosedAtBetween(tenantId, CashShiftStatus.CLOSED, from, to);

        return new DailyReportResponse(
                date,
                sumField(shifts, CashShift::getTotalCashSales),
                sumField(shifts, CashShift::getTotalDigitalSales),
                sumField(shifts, CashShift::getVariance),
                sumField(shifts, CashShift::getTotalCashIn),
                sumField(shifts, CashShift::getTotalCashOut),
                shifts.stream().map(this::toResponse).toList());
    }

    public CashShiftResponse toResponse(CashShift shift) {
        Set<String> userIds = new HashSet<>();
        userIds.add(shift.getOpenedBy());
        if (shift.getClosedBy() != null) userIds.add(shift.getClosedBy());
        return toResponse(shift, resolveNames(userIds));
    }

    public CashMovementResponse toMovementResponse(CashMovement movement) {
        return toMovementResponse(movement, resolveNames(Set.of(movement.getCreatedBy())));
    }

    private CashShiftResponse toResponse(CashShift shift, Map<String, String> names) {
        return new CashShiftResponse(
                shift.getId(), shift.getShiftNumber(), shift.getStatus().name(), shift.getOpeningFloat(),
                names.getOrDefault(shift.getOpenedBy(), shift.getOpenedBy()), shift.getOpenedAt(),
                shift.getClosedBy() == null ? null : names.getOrDefault(shift.getClosedBy(), shift.getClosedBy()),
                shift.getClosedAt(), shift.getExpectedCash(), shift.getCountedCash(), shift.getVariance(),
                shift.getTotalCashSales(), shift.getTotalDigitalSales(), shift.getTotalCashIn(),
                shift.getTotalCashOut());
    }

    private CashMovementResponse toMovementResponse(CashMovement movement, Map<String, String> names) {
        return new CashMovementResponse(
                movement.getId(), movement.getType().name(), movement.getAmount(), movement.getReason(),
                names.getOrDefault(movement.getCreatedBy(), movement.getCreatedBy()), movement.getCreatedAt());
    }

    private Map<String, String> resolveNames(Set<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }

    private BigDecimal sumField(List<CashShift> shifts, Function<CashShift, BigDecimal> extractor) {
        return shifts.stream().map(extractor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`
Expected: PASS (6 tests green).

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/dto backend/src/main/java/com/vanter/ember/cashregister/event backend/src/main/java/com/vanter/ember/cashregister/listener backend/src/main/java/com/vanter/ember/cashregister/service backend/src/test/java/com/vanter/ember/cashregister/service PROGRESS.md reports/<NNN>-task-EMB-CR-02-cash-shift-service.md
git commit -m "feat(backend): add cash shift service, events and websocket broadcast"
```

---

### Task 3: EMB-CR-03 — `CashShiftController` and request DTOs

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/OpenShiftRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/RecordMovementRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/dto/CloseShiftRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` (add new rows)

**Interfaces:**
- Consumes: Task 2's `CashShiftService` (all public methods) and DTOs; existing `com.vanter.ember.identity.repository.UserRepository.findByEmail`, `com.vanter.ember.config.TenantContextHolder.requireTenantId`, `com.vanter.ember.config.ResourceNotFoundException`.
- Produces: `OpenShiftRequest(openingFloat:BigDecimal)`, `RecordMovementRequest(type:CashMovementType, amount:BigDecimal, reason:String)`, `CloseShiftRequest(countedCash:BigDecimal)`; `CashShiftController` at `/cash-shifts` with `POST /open`, `GET /current`, `GET` (list), `GET /{id}`, `POST /{id}/movements`, `POST /{id}/close`, `GET /daily-report`.

- [ ] **Step 1: Write the failing controller test**

```java
package com.vanter.ember.cashregister.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.CloseShiftRequest;
import com.vanter.ember.cashregister.dto.OpenShiftRequest;
import com.vanter.ember.cashregister.dto.RecordMovementRequest;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CashShiftController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class CashShiftControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean CashShiftService cashShiftService;
    @MockBean UserRepository userRepository;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private User sampleUser(String email) {
        return User.builder().id("user-1").email(email).name("Alice").role(Role.WAITER).build();
    }

    private CashShift sampleShift() {
        return CashShift.builder().id(1L).shiftNumber(1).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now()).build();
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void open_returnsCreatedForWaiter() throws Exception {
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(sampleUser("waiter@ember.local")));
        when(cashShiftService.openShift(eq("user-1"), any(BigDecimal.class))).thenReturn(sampleShift());
        when(cashShiftService.toResponse(any())).thenReturn(new CashShiftResponse(
                1L, 1, "OPEN", new BigDecimal("100.00"), "Alice", LocalDateTime.now(),
                null, null, null, null, null, null, null, null, null));

        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("100.00"));
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void open_forbiddenForAdmin() throws Exception {
        OpenShiftRequest request = new OpenShiftRequest(new BigDecimal("100.00"));
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void recordMovement_forbiddenForAdmin() throws Exception {
        RecordMovementRequest request =
                new RecordMovementRequest(CashMovementType.CASH_OUT, new BigDecimal("10.00"), "safe drop");
        mockMvc.perform(post("/cash-shifts/1/movements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void close_returnsOkAndRevealsVarianceForWaiter() throws Exception {
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(sampleUser("waiter@ember.local")));
        CashShift closed = sampleShift();
        closed.setStatus(CashShiftStatus.CLOSED);
        when(cashShiftService.closeShift(eq(1L), eq("user-1"), any(BigDecimal.class))).thenReturn(closed);
        when(cashShiftService.toResponse(any())).thenReturn(new CashShiftResponse(
                1L, 1, "CLOSED", new BigDecimal("100.00"), "Alice", LocalDateTime.now(), "Alice",
                LocalDateTime.now(), new BigDecimal("265.00"), new BigDecimal("260.00"),
                new BigDecimal("-5.00"), new BigDecimal("150.00"), new BigDecimal("0.00"),
                new BigDecimal("20.00"), new BigDecimal("5.00")));

        CloseShiftRequest request = new CloseShiftRequest(new BigDecimal("260.00"));
        mockMvc.perform(post("/cash-shifts/1/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedCash").value(265.00))
                .andExpect(jsonPath("$.variance").value(-5.00));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void dailyReport_forbiddenForWaiter() throws Exception {
        mockMvc.perform(get("/cash-shifts/daily-report").param("date", "2026-08-16"))
                .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerTest`
Expected: FAIL to compile — `CashShiftController` and the request DTOs do not exist yet.

- [ ] **Step 3: Write the request DTOs**

```java
package com.vanter.ember.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OpenShiftRequest(@NotNull @DecimalMin("0.00") BigDecimal openingFloat) {}
```

```java
package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.CashMovementType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RecordMovementRequest(
        @NotNull CashMovementType type,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String reason) {}
```

```java
package com.vanter.ember.cashregister.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CloseShiftRequest(@NotNull @DecimalMin("0.00") BigDecimal countedCash) {}
```

- [ ] **Step 4: Write `CashShiftController`**

```java
package com.vanter.ember.cashregister.controller;

import com.vanter.ember.cashregister.dto.CashMovementResponse;
import com.vanter.ember.cashregister.dto.CashShiftDetailResponse;
import com.vanter.ember.cashregister.dto.CashShiftResponse;
import com.vanter.ember.cashregister.dto.CloseShiftRequest;
import com.vanter.ember.cashregister.dto.DailyReportResponse;
import com.vanter.ember.cashregister.dto.OpenShiftRequest;
import com.vanter.ember.cashregister.dto.RecordMovementRequest;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.service.CashShiftService;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Cash Register", description = "Apertura de Caja, Movimientos Manuales, Arqueo de Turnos, Corte Diario")
@RestController
@RequestMapping("/cash-shifts")
@RequiredArgsConstructor
public class CashShiftController {

    private final CashShiftService cashShiftService;
    private final UserRepository userRepository;

    @Operation(summary = "Open a new cash shift — Apertura de Caja (WAITER)")
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public CashShiftResponse open(@Valid @RequestBody OpenShiftRequest request, Authentication authentication) {
        CashShift shift = cashShiftService.openShift(resolveUserId(authentication), request.openingFloat());
        return cashShiftService.toResponse(shift);
    }

    @Operation(summary = "Get the tenant's currently open shift (WAITER/ADMIN)")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public CashShiftResponse current() {
        return cashShiftService.toResponse(
                cashShiftService.getCurrentOpenShift(TenantContextHolder.requireTenantId()));
    }

    @Operation(summary = "List cash shift history (WAITER/ADMIN)")
    @GetMapping
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public Page<CashShiftResponse> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(sort = "openedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return cashShiftService
                .getHistory(TenantContextHolder.requireTenantId(), from, to, pageable)
                .map(cashShiftService::toResponse);
    }

    @Operation(summary = "Get one shift's detail including its movements (WAITER/ADMIN)")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public CashShiftDetailResponse detail(@PathVariable Long id) {
        return cashShiftService.getDetail(id);
    }

    @Operation(summary = "Record a manual cash movement on an open shift (WAITER)")
    @PostMapping("/{id}/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public CashMovementResponse recordMovement(
            @PathVariable Long id,
            @Valid @RequestBody RecordMovementRequest request,
            Authentication authentication) {
        CashMovement movement = cashShiftService.recordMovement(
                id, resolveUserId(authentication), request.type(), request.amount(), request.reason());
        return cashShiftService.toMovementResponse(movement);
    }

    @Operation(summary = "Close a shift with a blind cash count — Arqueo de Turno (WAITER)")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('WAITER')")
    public CashShiftResponse close(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request,
            Authentication authentication) {
        CashShift shift = cashShiftService.closeShift(id, resolveUserId(authentication), request.countedCash());
        return cashShiftService.toResponse(shift);
    }

    @Operation(summary = "Corte Diario: roll up every shift closed on the given business day (ADMIN)")
    @GetMapping("/daily-report")
    @PreAuthorize("hasRole('ADMIN')")
    public DailyReportResponse dailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return cashShiftService.getDailyReport(TenantContextHolder.requireTenantId(), date);
    }

    private String resolveUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + authentication.getName()));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=CashShiftControllerTest`
Expected: PASS (5 tests green).

- [ ] **Step 6: Add the new routes to `SecurityAuditTest`'s 401 matrix**

In `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`, add these rows to the `@CsvSource` array (real controller paths — see this plan's Global Constraints re: the pre-existing `/api/...` rows being a stale inconsistency, not something to imitate):

```java
        "POST, /cash-shifts/open",
        "GET,  /cash-shifts/current",
        "GET,  /cash-shifts",
        "GET,  /cash-shifts/1",
        "POST, /cash-shifts/1/movements",
        "POST, /cash-shifts/1/close",
        "GET,  /cash-shifts/daily-report"
```

Run: `cd backend && ./mvnw test -Dtest=SecurityAuditTest`
Expected: PASS.

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/dto backend/src/main/java/com/vanter/ember/cashregister/controller backend/src/test/java/com/vanter/ember/cashregister/controller backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java PROGRESS.md reports/<NNN>-task-EMB-CR-03-cash-shift-controller.md
git commit -m "feat(backend): add cash shift REST endpoints"
```

---

### Task 4: EMB-CR-04 — Physical payments require an open shift; stamp attribution

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`

**Interfaces:**
- Consumes: Task 1's `CashShiftRepository.findOpenForUpdate(UUID):Optional<CashShift>`, `Payment.cashShiftId`/`processedBy`; existing `com.vanter.ember.identity.repository.UserRepository.findByEmail`, `com.vanter.ember.config.TenantContextHolder.requireTenantId`.
- Produces: `PaymentService.registerPhysicalPayment(Long billId, String participantName, BigDecimal amount, String processedByEmail):Payment` (signature change — 4th param added), `PaymentService.initiateDigitalPayment(Long billId, String participantName, BigDecimal amount, String processedByEmail):Payment` (signature change).

- [ ] **Step 1: Update the failing/changed tests first**

In `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`:

Add these imports:
```java
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.model.CashShiftStatus;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
```

Add to the mock fields:
```java
    @Mock CashShiftRepository cashShiftRepository;
    @Mock UserRepository userRepository;
```

Add a helper and stub it into every `registerPhysicalPayment`/`initiateDigitalPayment` test above the existing `when(...)` lines (the tests below show the pattern for the two that need it most; apply the same two stub lines to every other `registerPhysicalPayment_*`/`initiateDigitalPayment_*` test in the file before its `paymentService.registerPhysicalPayment(...)`/`initiateDigitalPayment(...)` call, and add `, "alice@ember.local"` as the new final argument to every such call):

```java
    private static final UUID TENANT_ID = UUID.randomUUID();

    private CashShift openShift() {
        return CashShift.builder().id(9L).tenantId(TENANT_ID).status(CashShiftStatus.OPEN)
                .openingFloat(BigDecimal.TEN).openedBy("user-1").build();
    }
```

Update `registerPhysicalPayment_createsConfirmedPhysicalPayment`:
```java
    @Test
    void registerPhysicalPayment_createsConfirmedPhysicalPayment() {
        Bill bill = sampleBill();
        BillSplit split = unpaidSplit(bill, "Alice", "12.50");
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(userRepository.findByEmail("alice@ember.local"))
                .thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(billSplitRepository.findByBillId(1L))
                .thenReturn(List.of(unpaidSplit(bill, "Bob", "10.00")));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.registerPhysicalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local");

        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.PHYSICAL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.getAmount()).isEqualByComparingTo("12.50");
        assertThat(payment.getBill().getId()).isEqualTo(1L);
        assertThat(payment.getCashShiftId()).isEqualTo(9L);
        assertThat(payment.getProcessedBy()).isEqualTo("user-1");
        assertThat(payment.getCreatedAt()).isNotNull();
    }
```

Add a new test for the guard:
```java
    @Test
    void registerPhysicalPayment_throwsWhenNoOpenShift() {
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.registerPhysicalPayment(
                1L, "Alice", new BigDecimal("12.50"), "alice@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }
```

`@ExtendWith(MockitoExtension.class)` runs at the default `Strictness.STRICT_STUBS` in this codebase (no `@MockitoSettings`/`lenient()` override anywhere) — a `when(...)` stub that a test never actually exercises fails that test with `UnnecessaryStubbingException`. `registerPhysicalPayment` now calls `cashShiftRepository.findOpenForUpdate` first, then `billRepository`/`billSplitRepository`, and only calls `userRepository.findByEmail` (inside `resolveUserId`) at the very end, while building the `Payment`. So which stubs a given test needs depends on how far into the method it actually runs:

- Tests that reach the end of the happy path — `registerPhysicalPayment_marksSplitAsPaid`, `registerPhysicalPayment_publishesPaymentCompletedWhenAllSplitsPaid`, `registerPhysicalPayment_doesNotPublishEventWhenSplitsStillUnpaid` — need **both** `when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));` and `when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));` added, and `, "alice@ember.local"` appended to their `paymentService.registerPhysicalPayment(...)` call — same shape as `registerPhysicalPayment_createsConfirmedPhysicalPayment` above.
- Tests that throw before reaching the end — `registerPhysicalPayment_throwsWhenBillNotFound`, `registerPhysicalPayment_throwsWhenSplitNotFound`, `registerPhysicalPayment_throwsWhenAmountDoesNotMatchSplit` — need **only** the `cashShiftRepository.findOpenForUpdate` stub added (the shift check runs before whichever failure they're testing) and `, "alice@ember.local"` appended to the call. Do **not** add a `userRepository.findByEmail` stub to these three — `resolveUserId` is never reached, so that stub would go unused and trigger `UnnecessaryStubbingException`.

For every `initiateDigitalPayment_*` test, stub only `userRepository.findByEmail` (not `cashShiftRepository` — DIGITAL doesn't check for an open shift, and `initiateDigitalPayment` calls `resolveUserId` only after its own bill/split checks pass, so apply the same reach-based judgment: the two `throwsWhen*` tests for `initiateDigitalPayment` need no `userRepository` stub either), adding `, "alice@ember.local"` to each call.

In `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`, the existing `@WithMockUser(roles = "WAITER")`/`@WithMockUser(roles = "CUSTOMER")` tests for `/billing/payments/physical` and `/billing/payments/digital` need `username = "..."` added to the `@WithMockUser` annotations (e.g. `@WithMockUser(username = "waiter@ember.local", roles = "WAITER")`) so `authentication.getName()` resolves to a non-null value — the `PaymentService` calls are already `@MockBean`-mocked so the exact stub value doesn't matter here, only that a username is present.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=PaymentServiceTest,BillingControllerTest`
Expected: FAIL to compile — `PaymentService` methods don't accept a 4th argument yet.

- [ ] **Step 3: Update `PaymentService`**

In `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`:

Add imports:
```java
import com.vanter.ember.cashregister.model.CashShift;
import com.vanter.ember.cashregister.repository.CashShiftRepository;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.config.TenantContextHolder;
```

Add two constructor-injected fields (Lombok `@RequiredArgsConstructor` picks them up automatically):
```java
    private final CashShiftRepository cashShiftRepository;
    private final UserRepository userRepository;
```

Replace `registerPhysicalPayment`:
```java
    @Transactional
    public Payment registerPhysicalPayment(
            Long billId, String participantName, BigDecimal amount, String processedByEmail) {
        CashShift shift = cashShiftRepository.findOpenForUpdate(TenantContextHolder.requireTenantId())
                .orElseThrow(() -> new IllegalStateException(
                        "No open cash shift; open one before registering a physical payment"));

        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        split.setPaid(true);
        billSplitRepository.save(split);

        Payment payment = paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.PHYSICAL)
                .status(PaymentStatus.CONFIRMED)
                .cashShiftId(shift.getId())
                .processedBy(resolveUserId(processedByEmail))
                .createdAt(LocalDateTime.now())
                .build());

        List<BillSplit> allSplits = billSplitRepository.findByBillId(billId);
        boolean allPaid = allSplits.stream().allMatch(BillSplit::isPaid);
        if (allPaid) {
            UUID tableId = sessionService.findById(bill.getSessionId()).getTableId();
            eventPublisher.publishEvent(new PaymentCompleted(bill.getSessionId(), tableId, billId));
        }

        return payment;
    }
```

Replace `initiateDigitalPayment`:
```java
    @Transactional
    public Payment initiateDigitalPayment(
            Long billId, String participantName, BigDecimal amount, String processedByEmail) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));

        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        if (amount.compareTo(split.getAmount()) != 0) {
            throw new IllegalArgumentException(
                    "Payment amount " + amount + " does not match split amount " + split.getAmount());
        }

        return paymentRepository.save(Payment.builder()
                .bill(bill)
                .participantName(participantName)
                .amount(amount)
                .method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.PENDING)
                .gatewayRef("STUB-" + UUID.randomUUID())
                .processedBy(resolveUserId(processedByEmail))
                .createdAt(LocalDateTime.now())
                .build());
    }
```

Add the private helper (place near the bottom of the class, before the closing brace):
```java
    private String resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
```

Leave `confirmDigitalPayment` unchanged — it only flips status on an existing `Payment`, no new actor to stamp.

- [ ] **Step 4: Update `BillingController`**

In `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`, add `import org.springframework.security.core.Authentication;` and change the two methods:

```java
    @Operation(summary = "Register physical payment (WAITER)")
    @PostMapping("/payments/physical")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Payment registerPhysicalPayment(
            @Valid @RequestBody PhysicalPaymentRequest request, Authentication authentication) {
        return paymentService.registerPhysicalPayment(
                request.billId(), request.participantName(), request.amount(), authentication.getName());
    }

    @Operation(summary = "Initiate digital payment (WAITER/CUSTOMER)")
    @PostMapping("/payments/digital")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")
    public Payment initiateDigitalPayment(
            @Valid @RequestBody DigitalPaymentRequest request, Authentication authentication) {
        return paymentService.initiateDigitalPayment(
                request.billId(), request.participantName(), request.amount(), authentication.getName());
    }
```

- [ ] **Step 5: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green, including the updated `PaymentServiceTest`/`BillingControllerTest` and everything from Tasks 1–3.

- [ ] **Step 6: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java PROGRESS.md reports/<NNN>-task-EMB-CR-04-physical-payment-shift-gate.md
git commit -m "feat(backend): require an open cash shift for physical payments"
```

This is the last backend task — the backend stream ends here.

---

### Task 5: EMB-CR-05 — Frontend shared prep (shadcn components, formatCurrency, API client)

**Files:**
- Create (via shadcn CLI): `frontend/src/components/ui/tabs.tsx`, `frontend/src/components/ui/select.tsx`, `frontend/src/components/ui/alert-dialog.tsx`
- Create: `frontend/src/lib/format.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/store/uiStore.ts`
- Test: `frontend/src/lib/format.test.ts`

**Interfaces:**
- Consumes: existing `frontend/src/lib/api.ts`'s `api` axios instance and `Page<T>` interface.
- Produces: `formatCurrency(value: number): string`; `cashShiftService` object with `open`, `current`, `history`, `detail`, `recordMovement`, `close`, `dailyReport`; types `CashShiftStatus`, `CashMovementType`, `CashShiftResponse`, `CashMovementResponse`, `CashShiftDetailResponse`, `DailyReportResponse`; `uiStore`'s `ModalType` gains `'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT'`.

- [ ] **Step 1: Write the failing `formatCurrency` test**

Check whether a frontend test runner is already configured before writing this — run `cd frontend && cat package.json | grep -A2 '"scripts"'` (or open `package.json`) and check for a `test` script and `vitest`/`jest` devDependency. If none exists, skip Steps 1–2 and Step 4 of this file below (there is no existing frontend unit-test convention in this repo to extend — verification for this task is `pnpm run build` only, same as every other frontend task in this codebase's history per `PROGRESS.md`), and go directly to Step 3.

If a test runner does exist:

```ts
import { describe, it, expect } from 'vitest'
import { formatCurrency } from './format'

describe('formatCurrency', () => {
  it('formats a whole number with two decimals and a dollar sign', () => {
    expect(formatCurrency(100)).toBe('$100.00')
  })

  it('formats a decimal value rounded to two places', () => {
    expect(formatCurrency(265.5)).toBe('$265.50')
  })

  it('inserts a thousands separator', () => {
    expect(formatCurrency(1234.5)).toBe('$1,234.50')
  })

  it('formats a negative value (variance can go negative)', () => {
    expect(formatCurrency(-5)).toBe('-$5.00')
  })
})
```

- [ ] **Step 2: Run test to verify it fails (only if a test runner exists)**

Run: `cd frontend && pnpm test format` (or the equivalent script name found in Step 1)
Expected: FAIL — `frontend/src/lib/format.ts` does not exist yet.

- [ ] **Step 3: Write `formatCurrency`**

```ts
export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
  }).format(value)
}
```

Save as `frontend/src/lib/format.ts`.

- [ ] **Step 4: Run test to verify it passes (only if a test runner exists)**

Run: `cd frontend && pnpm test format`
Expected: PASS.

- [ ] **Step 5: Add the shadcn components**

Run: `cd frontend && npx shadcn@latest add tabs select alert-dialog`
Expected: three new files appear under `frontend/src/components/ui/` (`tabs.tsx`, `select.tsx`, `alert-dialog.tsx`), matching this project's existing `radix-nova` style (see `frontend/components.json`). If the CLI prompts interactively, accept the defaults — this project's `components.json` already pins `style`/`baseColor`/`iconLibrary`/aliases, so no per-component choices should be needed.

- [ ] **Step 6: Add `cashShiftService` and its types to `api.ts`**

In `frontend/src/lib/api.ts`, add near the bottom (after `staffService`, following the file's existing "types just above the service that uses them" convention):

```ts
export type CashShiftLifecycleStatus = 'OPEN' | 'CLOSED'
export type CashMovementType = 'CASH_IN' | 'CASH_OUT'

export interface CashShiftResponse {
  id: number
  shiftNumber: number
  status: CashShiftLifecycleStatus
  openingFloat: number
  openedByName: string
  openedAt: string
  closedByName: string | null
  closedAt: string | null
  expectedCash: number | null
  countedCash: number | null
  variance: number | null
  totalCashSales: number | null
  totalDigitalSales: number | null
  totalCashIn: number | null
  totalCashOut: number | null
}

export interface CashMovementResponse {
  id: number
  type: CashMovementType
  amount: number
  reason: string
  createdByName: string
  createdAt: string
}

export interface CashShiftDetailResponse {
  shift: CashShiftResponse
  movements: CashMovementResponse[]
}

export interface DailyReportResponse {
  date: string
  totalCashSales: number
  totalDigitalSales: number
  totalVariance: number
  totalCashIn: number
  totalCashOut: number
  shifts: CashShiftResponse[]
}

export const cashShiftService = {
  open: async (openingFloat: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>('/cash-shifts/open', { openingFloat })
    return data
  },
  current: async (): Promise<CashShiftResponse | null> => {
    try {
      const { data } = await api.get<CashShiftResponse>('/cash-shifts/current')
      return data
    } catch (error: any) {
      if (error.response?.status === 404) return null
      throw error
    }
  },
  history: async (
    params: { from?: string; to?: string; page?: number; size?: number } = {}
  ): Promise<Page<CashShiftResponse>> => {
    const { data } = await api.get<Page<CashShiftResponse>>('/cash-shifts', { params })
    return data
  },
  detail: async (id: number): Promise<CashShiftDetailResponse> => {
    const { data } = await api.get<CashShiftDetailResponse>(`/cash-shifts/${id}`)
    return data
  },
  recordMovement: async (
    id: number,
    movement: { type: CashMovementType; amount: number; reason: string }
  ): Promise<CashMovementResponse> => {
    const { data } = await api.post<CashMovementResponse>(`/cash-shifts/${id}/movements`, movement)
    return data
  },
  close: async (id: number, countedCash: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>(`/cash-shifts/${id}/close`, { countedCash })
    return data
  },
  dailyReport: async (date: string): Promise<DailyReportResponse> => {
    const { data } = await api.get<DailyReportResponse>('/cash-shifts/daily-report', { params: { date } })
    return data
  },
}
```

Note: these types are hand-written, not `components['schemas'][...]` aliases from `backend-types.ts` — the codebase's established caveat (see `PROGRESS.md`'s EMB-PC-10/EMB-LP entries) is that regenerating `backend-types.ts` needs a live backend via `pnpm run openapi`, which isn't available while this task runs in parallel with the backend track. Follow-up: once the backend track lands and a live backend is available, run `pnpm run openapi` and replace these hand-written types with `components['schemas']['CashShiftResponse']`-style aliases, matching every other service in this file.

- [ ] **Step 7: Extend `uiStore`'s `ModalType`**

In `frontend/src/store/uiStore.ts`, change:

```ts
export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |null;
```

to:

```ts
export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |
                         'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT' | null;
```

- [ ] **Step 8: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS (`tsc -b && vite build` succeeds with no new errors).

- [ ] **Step 9: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/components/ui/tabs.tsx frontend/src/components/ui/select.tsx frontend/src/components/ui/alert-dialog.tsx frontend/src/lib/format.ts frontend/src/lib/api.ts frontend/src/store/uiStore.ts PROGRESS.md reports/<NNN>-task-EMB-CR-05-frontend-shared-prep.md
git commit -m "feat(frontend): add cash register shared utilities and API client"
```

(Add `frontend/src/lib/format.test.ts` to the `git add` line too if Steps 1–2/4 applied.)

---

### Task 6: EMB-CR-06 — Waiter operate page

**Files:**
- Create: `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- Create: `frontend/src/pages/waiter/cashRegister/components/OpenShiftDialog.tsx`
- Create: `frontend/src/pages/waiter/cashRegister/components/MovementDialog.tsx`
- Create: `frontend/src/pages/waiter/cashRegister/components/CloseShiftDialog.tsx`
- Modify: `frontend/src/components/FloatingNav.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: Task 5's `cashShiftService`, `formatCurrency`, `uiStore`'s `'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT'` modal types, the new `select.tsx`/`dialog.tsx`/`form.tsx` components.
- Produces: route `/waiter/cash-register`; no other task consumes this task's output.

This task has no meaningful "write a failing test first" step — there is no existing frontend test runner wired to component-level tests in this repo (confirmed in Task 5 Step 1), and this codebase's established frontend verification is `pnpm run build` (type-check) plus manual browser testing per `ember/CLAUDE.md` §"For UI or frontend changes, start the dev server and use the feature in a browser before reporting the task as complete." Steps below build the feature directly, then verify via build + a manual dev-server check.

- [ ] **Step 1: Create the three dialog components**

```tsx
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'

const openShiftSchema = z.object({
  openingFloat: z.coerce.number().min(0, 'El fondo inicial no puede ser negativo'),
})

type OpenShiftInputs = z.infer<typeof openShiftSchema>

export const OpenShiftDialog = () => {
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()

  const form = useForm<OpenShiftInputs>({
    resolver: zodResolver(openShiftSchema),
    defaultValues: { openingFloat: 0 },
  })

  const mutation = useMutation({
    mutationFn: (data: OpenShiftInputs) => cashShiftService.open(data.openingFloat),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success('Caja abierta correctamente.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo abrir la caja.')
    },
  })

  return (
    <Dialog open={activeModal === 'OPEN_SHIFT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Apertura de caja</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
            <FormField
              control={form.control}
              name="openingFloat"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Fondo inicial</FormLabel>
                  <FormControl>
                    <Input type="number" step="0.01" min="0" className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? 'Abriendo...' : 'Abrir caja'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
```

Save as `frontend/src/pages/waiter/cashRegister/components/OpenShiftDialog.tsx`.

```tsx
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'

const movementSchema = z.object({
  type: z.enum(['CASH_IN', 'CASH_OUT']),
  amount: z.coerce.number().positive('El monto debe ser mayor a cero'),
  reason: z.string().min(3, 'Escribe un motivo'),
})

type MovementInputs = z.infer<typeof movementSchema>

export const MovementDialog = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined

  const form = useForm<MovementInputs>({
    resolver: zodResolver(movementSchema),
    defaultValues: { type: 'CASH_IN', amount: 0, reason: '' },
  })

  const mutation = useMutation({
    mutationFn: (data: MovementInputs) => cashShiftService.recordMovement(shiftId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftDetail', shiftId] })
      toast.success('Movimiento registrado.')
      form.reset()
      closeModal()
    },
    onError: () => {
      toast.error('No se pudo registrar el movimiento.')
    },
  })

  return (
    <Dialog open={activeModal === 'CASH_MOVEMENT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Movimiento manual</DialogTitle>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
            <FormField
              control={form.control}
              name="type"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Tipo</FormLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <FormControl>
                      <SelectTrigger className="w-full rounded-xl">
                        <SelectValue />
                      </SelectTrigger>
                    </FormControl>
                    <SelectContent>
                      <SelectItem value="CASH_IN">Entrada</SelectItem>
                      <SelectItem value="CASH_OUT">Salida</SelectItem>
                    </SelectContent>
                  </Select>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="amount"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Monto</FormLabel>
                  <FormControl>
                    <Input type="number" step="0.01" min="0.01" className="rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="reason"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Motivo</FormLabel>
                  <FormControl>
                    <Textarea className="resize-none h-20 rounded-xl" {...field} />
                  </FormControl>
                </FormItem>
              )}
            />

            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                Cancelar
              </Button>
              <Button type="submit" disabled={mutation.isPending || !shiftId}>
                {mutation.isPending ? 'Guardando...' : 'Registrar'}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  )
}
```

Save as `frontend/src/pages/waiter/cashRegister/components/MovementDialog.tsx`.

```tsx
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { z } from 'zod'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService, type CashShiftResponse } from '@/lib/api'
import { formatCurrency } from '@/lib/format'

const closeShiftSchema = z.object({
  countedCash: z.coerce.number().min(0, 'El monto contado no puede ser negativo'),
})

type CloseShiftInputs = z.infer<typeof closeShiftSchema>

export const CloseShiftDialog = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined
  const [result, setResult] = useState<CashShiftResponse | null>(null)

  const form = useForm<CloseShiftInputs>({
    resolver: zodResolver(closeShiftSchema),
    defaultValues: { countedCash: 0 },
  })

  const mutation = useMutation({
    mutationFn: (data: CloseShiftInputs) => cashShiftService.close(shiftId!, data.countedCash),
    onSuccess: (closed) => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      setResult(closed)
    },
    onError: () => {
      toast.error('No se pudo cerrar la caja.')
    },
  })

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) {
      form.reset()
      setResult(null)
      closeModal()
    }
  }

  return (
    <Dialog open={activeModal === 'CLOSE_SHIFT'} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Arqueo de turno</DialogTitle>
        </DialogHeader>

        {!result ? (
          <Form {...form}>
            <form onSubmit={form.handleSubmit((data) => mutation.mutate(data))} className="space-y-5">
              <p className="text-sm text-muted-foreground">
                Cuenta el efectivo en caja y escribe el total. El sistema mostrará la diferencia
                después de registrar el conteo.
              </p>
              <FormField
                control={form.control}
                name="countedCash"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Efectivo contado</FormLabel>
                    <FormControl>
                      <Input type="number" step="0.01" min="0" className="rounded-xl" {...field} />
                    </FormControl>
                  </FormItem>
                )}
              />

              <DialogFooter>
                <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                  Cancelar
                </Button>
                <Button type="submit" disabled={mutation.isPending || !shiftId}>
                  {mutation.isPending ? 'Cerrando...' : 'Confirmar conteo'}
                </Button>
              </DialogFooter>
            </form>
          </Form>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs text-muted-foreground">Esperado</p>
                <p className="text-lg font-bold">{formatCurrency(result.expectedCash ?? 0)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">Contado</p>
                <p className="text-lg font-bold">{formatCurrency(result.countedCash ?? 0)}</p>
              </div>
              <div className="col-span-2">
                <p className="text-xs text-muted-foreground">Diferencia</p>
                <p
                  className={`text-lg font-bold ${
                    (result.variance ?? 0) === 0
                      ? 'text-primary'
                      : (result.variance ?? 0) > 0
                        ? 'text-emerald-600'
                        : 'text-destructive'
                  }`}
                >
                  {formatCurrency(result.variance ?? 0)}
                </p>
              </div>
            </div>
            <DialogFooter>
              <Button onClick={() => handleOpenChange(false)}>Cerrar</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
```

Save as `frontend/src/pages/waiter/cashRegister/components/CloseShiftDialog.tsx`.

- [ ] **Step 2: Create the page**

```tsx
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { OpenShiftDialog } from './components/OpenShiftDialog'
import { MovementDialog } from './components/MovementDialog'
import { CloseShiftDialog } from './components/CloseShiftDialog'

export const CashRegister = () => {
  const { openModal } = useUIStore()

  const { data: shift, isLoading } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
  })

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', shift?.id],
    queryFn: () => cashShiftService.detail(shift!.id),
    enabled: !!shift?.id,
  })

  if (isLoading) {
    return <div className="p-6 text-zinc-500">Cargando caja...</div>
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Caja</h1>
        <p className="text-sm text-muted-foreground">Apertura, movimientos y arqueo del turno.</p>
      </div>

      {!shift ? (
        <Card className="border border-border/40 bg-background py-6 shadow-sm">
          <CardContent className="flex flex-col items-center gap-4 py-10">
            <p className="text-sm text-muted-foreground">No hay un turno de caja abierto.</p>
            <Button onClick={() => openModal('OPEN_SHIFT')}>Abrir caja</Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader className="flex flex-row items-center justify-between">
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Turno #{shift.shiftNumber}
              </CardTitle>
              <Badge variant="secondary">{shift.status === 'OPEN' ? 'Abierto' : 'Cerrado'}</Badge>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div>
                  <p className="text-xs text-muted-foreground">Fondo inicial</p>
                  <p className="text-lg font-bold text-primary">{formatCurrency(shift.openingFloat)}</p>
                </div>
                <div>
                  <p className="text-xs text-muted-foreground">Abierto por</p>
                  <p className="text-sm font-medium">{shift.openedByName}</p>
                </div>
              </div>
              <div className="flex gap-2">
                <Button variant="outline" onClick={() => openModal('CASH_MOVEMENT', { shiftId: shift.id })}>
                  Registrar movimiento
                </Button>
                <Button onClick={() => openModal('CLOSE_SHIFT', { shiftId: shift.id })}>
                  Cerrar caja (Arqueo)
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="border border-border/40 bg-background py-6 shadow-sm">
            <CardHeader>
              <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Movimientos
              </CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Tipo</TableHead>
                    <TableHead>Monto</TableHead>
                    <TableHead>Motivo</TableHead>
                    <TableHead>Registrado por</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(detail?.movements ?? []).length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={4} className="text-center text-sm text-muted-foreground">
                        Sin movimientos registrados.
                      </TableCell>
                    </TableRow>
                  ) : (
                    detail!.movements.map((movement) => (
                      <TableRow key={movement.id}>
                        <TableCell>{movement.type === 'CASH_IN' ? 'Entrada' : 'Salida'}</TableCell>
                        <TableCell>{formatCurrency(movement.amount)}</TableCell>
                        <TableCell>{movement.reason}</TableCell>
                        <TableCell>{movement.createdByName}</TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}

      <OpenShiftDialog />
      <MovementDialog />
      <CloseShiftDialog />
    </div>
  )
}
```

Save as `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`.

- [ ] **Step 3: Add the `FloatingNav` icon**

In `frontend/src/components/FloatingNav.tsx`, add `Banknote` to the `lucide-react` import list (line 3–14 block), and insert this block immediately after the existing `{(role === 'KITCHEN' || role === 'ADMIN') && (...)}` block (before the `{role === 'ADMIN' && (...)}` block):

```tsx
      {role === 'WAITER' && (
        <Link
          to="/waiter/cash-register"
          className={navItemClass('/waiter/cash-register')}
          title="Caja"
        >
          <Banknote strokeWidth={1.5} size={24} />
        </Link>
      )}
```

- [ ] **Step 4: Register the route**

In `frontend/src/App.tsx`, add the import:

```tsx
import { CashRegister as WaiterCashRegister } from '@/pages/waiter/cashRegister/CashRegister'
```

and add the route inside the existing `/waiter` block:

```tsx
        <Route element={<ProtectedRoute allowedRoles={['WAITER', 'ADMIN']} />}>
          <Route path="/waiter" element={<WaiterLayout />}>
            <Route path="tables" element={<Tables />} />
            <Route path="tables/:id" element={<TableInformation />} />
            <Route path="cash-register" element={<WaiterCashRegister />} />
          </Route>
        </Route>
```

- [ ] **Step 5: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS.

- [ ] **Step 6: Manual browser check**

Run: `cd frontend && pnpm run dev` (and `cd backend && ./mvnw spring-boot:run` in another terminal if not already running). Log in as a WAITER, navigate to `/waiter/cash-register` via the new FloatingNav icon, open a shift, add a cash-in and a cash-out movement, then close the shift and confirm the expected/counted/variance numbers appear only after submitting the count (not before). Stop the dev server when done.

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/pages/waiter/cashRegister frontend/src/components/FloatingNav.tsx frontend/src/App.tsx PROGRESS.md reports/<NNN>-task-EMB-CR-06-waiter-cash-register-page.md
git commit -m "feat(waiter): add cash register operate page"
```

---

### Task 7: EMB-CR-07 — Admin oversight + Z-report page

**Files:**
- Create: `frontend/src/pages/admin/cashRegister/CashRegister.tsx`
- Create: `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- Create: `frontend/src/pages/admin/cashRegister/components/DailyZReportPanel.tsx`
- Modify: `frontend/src/components/FloatingNav.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: Task 5's `cashShiftService`, `formatCurrency`, the new `tabs.tsx` component; existing `frontend/src/components/PaginationControls.tsx` (`page:number, totalPages:number, onPageChange:(page:number)=>void`).
- Produces: route `/admin/cash-register`; no other task consumes this task's output.

- [ ] **Step 1: Create `ShiftHistoryTable`**

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent } from '@/components/ui/card'
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/format'
import { PaginationControls } from '@/components/PaginationControls'

export const ShiftHistoryTable = () => {
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftHistory', page],
    queryFn: () => cashShiftService.history({ page, size: 20 }),
  })

  if (isLoading) {
    return <div className="p-6 text-sm text-muted-foreground">Cargando turnos...</div>
  }

  if (isError || !data) {
    return <div className="p-6 text-sm text-destructive">Error al cargar el historial.</div>
  }

  return (
    <>
      <Card className="border border-border/40 bg-background py-6 shadow-sm">
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Turno</TableHead>
                <TableHead>Estado</TableHead>
                <TableHead>Abierto por</TableHead>
                <TableHead>Cerrado por</TableHead>
                <TableHead>Esperado</TableHead>
                <TableHead>Contado</TableHead>
                <TableHead>Diferencia</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center text-sm text-muted-foreground">
                    Sin turnos registrados.
                  </TableCell>
                </TableRow>
              ) : (
                data.content.map((shift) => (
                  <TableRow key={shift.id}>
                    <TableCell>#{shift.shiftNumber}</TableCell>
                    <TableCell>
                      <Badge variant={shift.status === 'OPEN' ? 'default' : 'secondary'}>
                        {shift.status === 'OPEN' ? 'Abierto' : 'Cerrado'}
                      </Badge>
                    </TableCell>
                    <TableCell>{shift.openedByName}</TableCell>
                    <TableCell>{shift.closedByName ?? '—'}</TableCell>
                    <TableCell>{shift.expectedCash != null ? formatCurrency(shift.expectedCash) : '—'}</TableCell>
                    <TableCell>{shift.countedCash != null ? formatCurrency(shift.countedCash) : '—'}</TableCell>
                    <TableCell>{shift.variance != null ? formatCurrency(shift.variance) : '—'}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
      <PaginationControls page={page} totalPages={data.totalPages} onPageChange={setPage} />
    </>
  )
}
```

Save as `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`.

- [ ] **Step 2: Create `DailyZReportPanel`**

```tsx
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { DollarSign, CreditCard, Scale, ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { formatCurrency } from '@/lib/format'

const today = () => new Date().toISOString().slice(0, 10)

export const DailyZReportPanel = () => {
  const [date, setDate] = useState(today())

  const { data, isLoading, isError } = useQuery({
    queryKey: ['cashShiftDailyReport', date],
    queryFn: () => cashShiftService.dailyReport(date),
  })

  const cards = data
    ? [
        { label: 'Ventas en efectivo', value: formatCurrency(data.totalCashSales), icon: DollarSign },
        { label: 'Ventas digitales', value: formatCurrency(data.totalDigitalSales), icon: CreditCard },
        { label: 'Diferencia total', value: formatCurrency(data.totalVariance), icon: Scale },
        { label: 'Entradas manuales', value: formatCurrency(data.totalCashIn), icon: ArrowDownCircle },
        { label: 'Salidas manuales', value: formatCurrency(data.totalCashOut), icon: ArrowUpCircle },
      ]
    : []

  return (
    <div className="flex flex-col gap-6">
      <Input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
        className="w-fit rounded-xl"
      />

      {isLoading && <div className="text-sm text-muted-foreground">Cargando corte diario...</div>}
      {isError && <div className="text-sm text-destructive">Error al cargar el corte diario.</div>}

      {data && (
        <div className="grid grid-cols-1 gap-6 md:grid-cols-3 lg:grid-cols-5">
          {cards.map(({ label, value, icon: Icon }) => (
            <Card key={label} className="border border-border/40 bg-background py-6 shadow-sm">
              <CardHeader className="flex flex-row items-center justify-start gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10">
                  <Icon className="h-4 w-4 text-primary" strokeWidth={2} />
                </div>
                <CardTitle className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {label}
                </CardTitle>
              </CardHeader>
              <CardContent>
                <p className="text-2xl font-bold tracking-tight tabular-nums text-primary">{value}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
```

Save as `frontend/src/pages/admin/cashRegister/components/DailyZReportPanel.tsx`.

- [ ] **Step 3: Create the page**

```tsx
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { ShiftHistoryTable } from './components/ShiftHistoryTable'
import { DailyZReportPanel } from './components/DailyZReportPanel'

export const CashRegister = () => {
  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-col gap-1">
        <h1 className="text-3xl font-bold tracking-tight text-foreground">Caja</h1>
        <p className="text-sm text-muted-foreground">Historial de turnos y corte diario de caja.</p>
      </div>

      <Tabs defaultValue="history">
        <TabsList>
          <TabsTrigger value="history">Historial de turnos</TabsTrigger>
          <TabsTrigger value="daily-report">Corte diario (Z)</TabsTrigger>
        </TabsList>
        <TabsContent value="history" className="mt-6">
          <ShiftHistoryTable />
        </TabsContent>
        <TabsContent value="daily-report" className="mt-6">
          <DailyZReportPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}
```

Save as `frontend/src/pages/admin/cashRegister/CashRegister.tsx`.

- [ ] **Step 4: Add the `FloatingNav` icon**

In `frontend/src/components/FloatingNav.tsx` (already has `Banknote` imported from Task 6), insert this `Link` inside the existing `{role === 'ADMIN' && (...)}` block, immediately after the `/admin/employees` link and before the `<div className="w-px h-8 ...">` divider:

```tsx
          <Link
            to="/admin/cash-register"
            className={navItemClass('/admin/cash-register')}
            title="Caja"
          >
            <Banknote strokeWidth={1.5} size={24} />
          </Link>
```

- [ ] **Step 5: Register the route**

In `frontend/src/App.tsx`, add the import:

```tsx
import { CashRegister as AdminCashRegister } from '@/pages/admin/cashRegister/CashRegister'
```

and add the route inside the existing `/admin` block:

```tsx
        <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
          <Route path="/admin" element={<AdminLayout />}>
            <Route path="categories" element={<Category />} />
            <Route path="categories/:id/items" element={<ListMenuItem />} />
            <Route path="settings" element={<Settings />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="employees" element={<Staff />} />
            <Route path="cash-register" element={<AdminCashRegister />} />
          </Route>
        </Route>
```

- [ ] **Step 6: Verify the build**

Run: `cd frontend && pnpm run build`
Expected: PASS.

- [ ] **Step 7: Manual browser check**

Run: `cd frontend && pnpm run dev` (backend running too). Log in as ADMIN, navigate to `/admin/cash-register` via the new FloatingNav icon, confirm the shift-history tab lists shifts (open one as a WAITER first if the list is empty) and the Z-report tab shows KPI cards for a chosen date. Stop the dev server when done.

- [ ] **Step 8: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/pages/admin/cashRegister frontend/src/components/FloatingNav.tsx frontend/src/App.tsx PROGRESS.md reports/<NNN>-task-EMB-CR-07-admin-cash-register-page.md
git commit -m "feat(admin): add cash register oversight and daily z-report page"
```

This is the last frontend task — the frontend stream ends here. Once both streams are done, the `EMB-CR` backlog is complete.
