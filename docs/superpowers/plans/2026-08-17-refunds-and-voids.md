# Refunds & Voids Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add refund/void capability (`EMB-RV` backlog) to the billing pipeline — void a `Bill` before any payment lands, and refund (full or partial) a `CONFIRMED` `Payment` after money moved — for the WAITER role to execute and the ADMIN role to review.

**Architecture:** Both mechanisms are additive and append-only, extending the existing `billing` module rather than mutating history: a new `Refund` entity (never mutates `Payment`), a new terminal `BillStatus.VOIDED`, and `BillSplit.paid` (boolean) becomes `BillSplit.status` (`UNPAID/PARTIALLY_PAID/PAID`) so a partial refund is representable. A physical refund's till impact reuses the existing `CashMovement`/`CASH_OUT` mechanism against whichever shift is open *now*, so a closed shift's stored totals are never touched retroactively. `AnalyticsService`'s revenue queries net out refunds in the same change. Frontend work adds refund/void triggers to the live waiter table view and a payments-lookup affordance to the admin cash-register history view — no new pages or routes.

**Tech Stack:** Java 17 / Spring Boot 3.5.14 / Hibernate (`@TenantId` discriminator multi-tenancy) / Flyway / PostgreSQL — React 19 / TypeScript / TanStack Query 5 / Zustand 5 / shadcn (`radix-nova` style).

**Spec:** `docs/superpowers/specs/2026-08-17-refunds-and-voids-design.md`

## Global Constraints

- Never mutate `Payment` or a `CLOSED` `CashShift`'s stored totals. A refund is always a new `Refund` row; a physical refund's cash-out is always a new `CashMovement` against the shift open *at refund time*, never a rewrite of the shift that recorded the original payment.
- `Refund`/void authorization is WAITER-only, matching the rest of billing (`calculateBill`/`splitBill`/`registerPhysicalPayment` are already WAITER-only) — no ADMIN-approval step.
- No `PaymentStatus.REFUNDED` value. `Payment.status` never changes after creation except the existing PENDING→CONFIRMED digital-payment-confirmation transition; refund state lives only in `Refund` rows and the derived `BillSplit.status`.
- `opened_by`/`closed_by`/`created_by`/`processed_by`-style attribution columns (here: `refunded_by`, `voided_by`) are plain `varchar(255)` columns holding `users.id`, never a JPA `@ManyToOne` to `User` — `User#restaurantId` is `LAZY` and `open-in-view` is `false`; embedding a `User` association risks `LazyInitializationException` for zero benefit (see `V7__cash_shifts.sql`'s rationale, reused verbatim here).
- Base path stays `@RequestMapping("/billing")` — no `/api` prefix. `SecurityAuditTest`'s existing `/api/billing/...` rows are a pre-existing stale inconsistency (not this plan's bug to fix); new rows added by this plan use the real, prefix-less path.
- No circular service dependency between `billing` and `cashregister`. `CashShiftService` gains a dependency on `PaymentService` (to list a shift's payments in Task 4), so `PaymentService` must NOT depend on `CashShiftService` — its refund path builds the `CashMovement` directly via `CashMovementRepository` rather than calling `CashShiftService.recordMovement`, even though that duplicates a few lines of that method's body. This is deliberate; do not "clean up" the duplication by introducing the service-to-service call.
- Every task below is also one task in the `ember/CLAUDE.md` sense: after the code/test steps, write `/reports/<NNN>-task-EMB-RV-0X-<slug>.md` (find the next number — `reports/` currently ends at 136), update `PROGRESS.md`'s three sections, then make exactly one squashed commit (Conventional Commits, no `Co-authored-by`/AI signature, scoped `git add` — never `-A`/`.`). Verify with `cd backend && ./mvnw test` (backend tasks) or `cd frontend && pnpm run build` (frontend tasks) before committing.
- Backend tasks (EMB-RV-01 → EMB-RV-05) are strictly sequential — each later task's service/controller code depends on the previous task's entities/repositories/service methods. Frontend tasks (EMB-RV-06 → EMB-RV-08) are strictly sequential for the same reason and additionally require the backend to be running once (Task 6's `pnpm run openapi` step) to regenerate `backend-types.ts` with the new schemas. The backend stream must fully land (through Task 5) before Task 6 starts, since the OpenAPI regen needs the finished backend schema — unlike the cash-register plan, these two streams are NOT independent and must not run in parallel.

---

### Task 1: EMB-RV-01 — Data layer (Refund entity, BillSplit status rename, Bill void columns, migration, repositories)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/billing/model/BillSplitStatus.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/model/Refund.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/repository/RefundRepository.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/repository/RefundDailyAmount.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/model/BillStatus.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java` (mechanical rename only — `.paid(false)` → `.status(BillSplitStatus.UNPAID)`, no behavior change)
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java` (mechanical rename only — `split.setPaid(true)` → `split.setStatus(BillSplitStatus.PAID)`, no behavior change)
- Create: `backend/src/main/resources/db/migration/V8__refunds_and_voids.sql`
- Create: `backend/src/test/java/com/vanter/ember/billing/repository/RefundRepositoryTenantIsolationTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTenantIsolationTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/listener/BillingEventListenerTest.java`

**Interfaces:**
- Consumes: `com.vanter.ember.config.AbstractTenantIsolationTest` (`TENANT_A`/`TENANT_B`, `asTenant`/`readAs`, `@AfterEach` `deleteAll`); existing `Payment`, `Bill`, `BillSplit` entities.
- Produces: `BillSplitStatus{UNPAID,PARTIALLY_PAID,PAID}`; `BillSplit.getStatus():BillSplitStatus`/`setStatus(BillSplitStatus)` (replaces `isPaid()`/`setPaid(boolean)`); `BillStatus{OPEN,PAID,VOIDED}`; `Bill.getVoidedBy():String`/`getVoidedAt():LocalDateTime`/`getVoidReason():String` (+ setters); `Refund` entity (fields: `id:Long`, `tenantId:UUID`, `payment:Payment`, `amount:BigDecimal`, `reason:String`, `refundedBy:String`, `createdAt:LocalDateTime`, Lombok `@Data @Builder`); `RefundRepository` methods `findByPaymentId(Long):List<Refund>`, `sumByPaymentId(Long):BigDecimal`, `sumRefundsInWindow(UUID,LocalDateTime,LocalDateTime):BigDecimal`, `findRefundsByDay(UUID,LocalDateTime,LocalDateTime):List<RefundDailyAmount>`; `RefundDailyAmount(Integer year, Integer month, Integer day, BigDecimal amount)` with a `date():LocalDate` helper; `BillRepository.findBySessionIdAndStatusNot(String,BillStatus):Optional<Bill>`; `PaymentRepository.existsByBillIdAndStatus(Long,PaymentStatus):boolean`, `findByIdForUpdate(Long):Optional<Payment>`, `findByCashShiftId(Long):List<Payment>`.

- [ ] **Step 1: Write the failing repository test**

```java
package com.vanter.ember.billing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.model.PaymentMethod;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.model.SplitMethod;
import com.vanter.ember.config.AbstractTenantIsolationTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RefundRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired BillRepository billRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired RefundRepository refundRepository;

    @Override
    protected void deleteAll() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        billRepository.deleteAll();
    }

    private Payment paymentFor(UUID tenantId, String amount) {
        Bill bill = readAs(tenantId, () -> billRepository.save(Bill.builder()
                .sessionId("sess-" + tenantId).total(new BigDecimal(amount))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.PAID)
                .createdAt(LocalDateTime.now()).build()));
        return readAs(tenantId, () -> paymentRepository.save(Payment.builder()
                .bill(bill).participantName("Alice").amount(new BigDecimal(amount))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .processedBy("user-1").createdAt(LocalDateTime.now()).build()));
    }

    private Refund refundOf(UUID tenantId, Payment payment, String amount) {
        return readAs(tenantId, () -> refundRepository.save(Refund.builder()
                .payment(payment).amount(new BigDecimal(amount)).reason("test refund")
                .refundedBy("user-2").createdAt(LocalDateTime.now()).build()));
    }

    @Test
    void save_stampsTheBoundTenant() {
        Payment payment = paymentFor(TENANT_A, "20.00");
        Refund saved = refundOf(TENANT_A, payment, "20.00");

        assertThat(saved.getTenantId()).isEqualTo(TENANT_A);
    }

    @Test
    void sumByPaymentId_doesNotLeakAnotherTenantsRefund() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        refundOf(TENANT_A, paymentA, "5.00");
        refundOf(TENANT_A, paymentA, "3.00");

        assertThat(readAs(TENANT_A, () -> refundRepository.sumByPaymentId(paymentA.getId())))
                .isEqualByComparingTo("8.00");
        assertThat(readAs(TENANT_B, () -> refundRepository.sumByPaymentId(paymentA.getId())))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void sumByPaymentId_isZeroWhenNoRefundsExist() {
        Payment payment = paymentFor(TENANT_A, "20.00");

        assertThat(readAs(TENANT_A, () -> refundRepository.sumByPaymentId(payment.getId())))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void findByPaymentId_doesNotReachAnotherTenantsRefund() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        refundOf(TENANT_A, paymentA, "5.00");

        assertThat(readAs(TENANT_B, () -> refundRepository.findByPaymentId(paymentA.getId()))).isEmpty();
        assertThat(readAs(TENANT_A, () -> refundRepository.findByPaymentId(paymentA.getId()))).hasSize(1);
    }

    @Test
    void sumRefundsInWindow_onlyCountsTheBoundTenantsRefundsInRange() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        LocalDateTime now = LocalDateTime.now();
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("5.00")).reason("in window")
                .refundedBy("user-2").createdAt(now).build()));
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("9.00")).reason("out of window")
                .refundedBy("user-2").createdAt(now.minusDays(10)).build()));

        BigDecimal sum = readAs(TENANT_A, () -> refundRepository.sumRefundsInWindow(
                TENANT_A, now.minusHours(1), now.plusHours(1)));

        assertThat(sum).isEqualByComparingTo("5.00");
    }

    @Test
    void findRefundsByDay_groupsByCalendarDay() {
        Payment paymentA = paymentFor(TENANT_A, "20.00");
        LocalDateTime day = LocalDateTime.of(2026, 8, 17, 10, 0);
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("5.00")).reason("r1")
                .refundedBy("user-2").createdAt(day).build()));
        readAs(TENANT_A, () -> refundRepository.save(Refund.builder()
                .payment(paymentA).amount(new BigDecimal("3.00")).reason("r2")
                .refundedBy("user-2").createdAt(day.plusHours(2)).build()));

        List<RefundDailyAmount> rows = readAs(TENANT_A, () -> refundRepository.findRefundsByDay(
                TENANT_A, day.minusDays(1), day.plusDays(1)));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.date()).isEqualTo(java.time.LocalDate.of(2026, 8, 17));
            assertThat(row.amount()).isEqualByComparingTo("8.00");
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RefundRepositoryTenantIsolationTest`
Expected: FAIL to compile — `com.vanter.ember.billing.model.Refund` and friends do not exist yet.

- [ ] **Step 3: Create `BillSplitStatus` and rename `BillSplit.paid` to `status`**

```java
package com.vanter.ember.billing.model;

public enum BillSplitStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID
}
```

In `backend/src/main/java/com/vanter/ember/billing/model/BillSplit.java`, replace:

```java
    @Column(nullable = false)
    private boolean paid;
```

with:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillSplitStatus status;
```

and add `import jakarta.persistence.EnumType;` and `import jakarta.persistence.Enumerated;` to its import list.

- [ ] **Step 4: Add `BillStatus.VOIDED` and `Bill`'s void columns**

In `backend/src/main/java/com/vanter/ember/billing/model/BillStatus.java`:

```java
package com.vanter.ember.billing.model;

public enum BillStatus {
    OPEN,
    PAID,
    VOIDED
}
```

In `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`, add after the existing `createdAt` field (before the closing brace):

```java

    @Column(name = "voided_by")
    private String voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason")
    private String voidReason;
```

- [ ] **Step 5: Create the `Refund` entity**

```java
package com.vanter.ember.billing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * A reversal of some or all of one {@link Payment}'s amount. Never mutates the {@code Payment} it
 * refunds — {@code Payment.status} stays {@code CONFIRMED} forever, an honest record of what was
 * actually collected; how much of it was later given back is answered by summing this table's
 * rows for that payment. Multiple partial refunds against one payment are multiple rows here, each
 * independently who/when/why-attributed — this row IS the audit trail, no separate audit module.
 */
@Entity
@Table(name = "refunds", indexes = @Index(name = "idx_refunds_payment", columnList = "payment_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String reason;

    /** {@code users.id} of whoever issued this refund — same plain-column pattern as {@code Payment#processedBy}. */
    @Column(name = "refunded_by", nullable = false)
    private String refundedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Create `RefundDailyAmount` and `RefundRepository`**

```java
package com.vanter.ember.billing.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One calendar day of refunds for a tenant — mirrors {@link PaymentDailyRevenue}'s shape so
 * {@code AnalyticsService} can net the two together bucket-by-bucket.
 */
public record RefundDailyAmount(Integer year, Integer month, Integer day, BigDecimal amount) {

    public LocalDate date() {
        return LocalDate.of(year, month, day);
    }
}
```

```java
package com.vanter.ember.billing.repository;

import com.vanter.ember.billing.model.Refund;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentId(Long paymentId);

    @Query("select coalesce(sum(r.amount), 0) from Refund r where r.payment.id = :paymentId")
    BigDecimal sumByPaymentId(@Param("paymentId") Long paymentId);

    /** Total refunded in a window — the deduction {@code AnalyticsService#getSummary} nets against revenue. */
    @Query("""
            select coalesce(sum(r.amount), 0) from Refund r
            where r.tenantId = :tenantId
              and r.createdAt >= :from
              and r.createdAt <= :to
            """)
    BigDecimal sumRefundsInWindow(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Same total as {@link #sumRefundsInWindow}, split by calendar day for the sales-series chart. */
    @Query("""
            select new com.vanter.ember.billing.repository.RefundDailyAmount(
                year(r.createdAt), month(r.createdAt), day(r.createdAt), sum(r.amount))
            from Refund r
            where r.tenantId = :tenantId
              and r.createdAt >= :from
              and r.createdAt <= :to
            group by year(r.createdAt), month(r.createdAt), day(r.createdAt)
            order by year(r.createdAt), month(r.createdAt), day(r.createdAt)
            """)
    List<RefundDailyAmount> findRefundsByDay(
            @Param("tenantId") UUID tenantId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
```

- [ ] **Step 7: Add the new methods to `BillRepository` and `PaymentRepository`**

In `backend/src/main/java/com/vanter/ember/billing/repository/BillRepository.java`, add after `findBySessionId`:

```java

    /** Same lookup as {@link #findBySessionId}, excluding a voided bill so a session can be re-billed. */
    Optional<Bill> findBySessionIdAndStatusNot(String sessionId, BillStatus status);
```

In `backend/src/main/java/com/vanter/ember/billing/repository/PaymentRepository.java`, add `import jakarta.persistence.LockModeType;` and `import org.springframework.data.jpa.repository.Lock;`, then add before the closing brace:

```java

    /** Physical payments landed in one shift — the historical-dispute lookup surface for a refund. */
    List<Payment> findByCashShiftId(Long cashShiftId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** Guards {@code BillingService#voidBill} — a bill with a confirmed payment must be refunded, not voided. */
    @Query("select count(p) > 0 from Payment p where p.bill.id = :billId and p.status = :status")
    boolean existsByBillIdAndStatus(@Param("billId") Long billId, @Param("status") PaymentStatus status);
```

Add `import java.util.Optional;` to `PaymentRepository.java` if not already present (it is not — check the existing import list before adding).

- [ ] **Step 8: Mechanical rename in `BillingService` and `PaymentService`**

In `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`, add `import com.vanter.ember.billing.model.BillSplitStatus;`, then replace both occurrences of `.paid(false)` (in `splitByConsumption` and `splitEqually`) with `.status(BillSplitStatus.UNPAID)`.

In `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`, add `import com.vanter.ember.billing.model.BillSplitStatus;`, then replace both occurrences of `split.setPaid(true);` (in `registerPhysicalPayment` and `confirmDigitalPayment`) with `split.setStatus(BillSplitStatus.PAID);`.

- [ ] **Step 9: Write the migration**

```sql
-- Refunds & Voids (2026-08-17 design spec) — append-only reversal of a CONFIRMED Payment via a
-- new `refunds` table (never mutates `payments`), plus a VOIDED terminal `bills.status` for
-- cancelling a bill before any payment lands. A CLOSED cash_shifts row is never touched
-- retroactively by a refund — see PaymentService#refundPayment, which records the till impact as
-- an ordinary CASH_OUT cash_movements row against whichever shift is open *now*.
--
-- refunded_by/voided_by store users.id directly (varchar(255)), same convention as
-- payments.processed_by / cash_shifts.opened_by (see V7__cash_shifts.sql) — no JPA @ManyToOne to
-- User, which carries a LAZY restaurantId association that risks LazyInitializationException if
-- embedded and serialized here.

CREATE TABLE IF NOT EXISTS refunds (
    id           bigserial PRIMARY KEY,
    tenant_id    uuid NOT NULL,
    payment_id   bigint NOT NULL REFERENCES payments(id),
    amount       numeric(10,2) NOT NULL,
    reason       varchar(255) NOT NULL,
    refunded_by  varchar(255) NOT NULL,
    created_at   timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment ON refunds (payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_tenant ON refunds (tenant_id);

ALTER TABLE bills ADD COLUMN IF NOT EXISTS voided_by varchar(255);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS voided_at timestamp;
ALTER TABLE bills ADD COLUMN IF NOT EXISTS void_reason varchar(255);

-- A VOIDED bill must free its session for a fresh calculateBill call, so the old all-statuses
-- unique constraint becomes a partial index that only guards non-VOIDED rows — same technique as
-- uk_cash_shifts_tenant_open in V7__cash_shifts.sql.
ALTER TABLE bills DROP CONSTRAINT IF EXISTS uk_bills_tenant_session;
CREATE UNIQUE INDEX IF NOT EXISTS uk_bills_tenant_session_active
    ON bills (tenant_id, session_id)
    WHERE status <> 'VOIDED';

-- bill_splits.paid (boolean) -> status (UNPAID | PARTIALLY_PAID | PAID): a partial refund can no
-- longer be represented as a single true/false flag.
ALTER TABLE bill_splits ADD COLUMN IF NOT EXISTS status varchar(20);
UPDATE bill_splits SET status = CASE WHEN paid THEN 'PAID' ELSE 'UNPAID' END WHERE status IS NULL;
ALTER TABLE bill_splits ALTER COLUMN status SET NOT NULL;
ALTER TABLE bill_splits DROP COLUMN IF EXISTS paid;
```

Save as `backend/src/main/resources/db/migration/V8__refunds_and_voids.sql`. Verify the constraint name being dropped matches reality first: `docker exec -it ember-postgres-1 psql -U ember -d ember -c "\d bills"` (adjust container name per `docker ps`) and confirm the unique constraint is actually named `uk_bills_tenant_session` (it is explicitly named that in `Bill.java`'s `@UniqueConstraint`, so this should match, but confirm before applying against a real database).

- [ ] **Step 10: Update the existing tests that reference `BillSplit.paid`**

In `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTenantIsolationTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, change `splitSavedFor`'s builder call from `.paid(false)` to `.status(BillSplitStatus.UNPAID)`.

In `backend/src/test/java/com/vanter/ember/billing/repository/BillSplitRepositoryTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, change every `.paid(false)` builder call (3 occurrences) to `.status(BillSplitStatus.UNPAID)`, and change `assertThat(saved.isPaid()).isFalse();` to `assertThat(saved.getStatus()).isEqualTo(BillSplitStatus.UNPAID);`.

In `backend/src/test/java/com/vanter/ember/billing/listener/BillingEventListenerTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, change both `.paid(false)` builder calls in `sampleSplits` to `.status(BillSplitStatus.UNPAID)`.

In `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, change `assertThat(splits).allMatch(s -> !s.isPaid());` (2 occurrences, in `splitByConsumption_splitsAreNotPaidByDefault` and `splitEqually_splitsAreNotPaidByDefault`) to `assertThat(splits).allMatch(s -> s.getStatus() == BillSplitStatus.UNPAID);`.

In `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, change `sampleSplit`'s `.paid(false)` to `.status(BillSplitStatus.UNPAID)`.

In `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`: add `import com.vanter.ember.billing.model.BillSplitStatus;`, then:
- `unpaidSplit` helper: change `.paid(false)` to `.status(BillSplitStatus.UNPAID)`.
- `registerPhysicalPayment_publishesPaymentCompletedWhenAllSplitsPaid`'s `bobSplitPaid`: change `.paid(true)` to `.status(BillSplitStatus.PAID)`.
- `confirmDigitalPayment_publishesPaymentCompletedWhenAllSplitsPaid`'s `bobPaid`: change `.paid(true)` to `.status(BillSplitStatus.PAID)`.
- `registerPhysicalPayment_marksSplitAsPaid`: change `assertThat(captor.getValue().isPaid()).isTrue();` to `assertThat(captor.getValue().getStatus()).isEqualTo(BillSplitStatus.PAID);`.
- `confirmDigitalPayment_marksSplitAsPaid`: same change as above.

- [ ] **Step 11: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green, including the new `RefundRepositoryTenantIsolationTest` and every renamed test above.

- [ ] **Step 12: Report, update PROGRESS.md, and commit**

Write `reports/<NNN>-task-EMB-RV-01-refund-void-data-layer.md` per the CLAUDE.md report structure (Identification/Objective/Modified Files/What Changed/Why It Changed). Update `PROGRESS.md`'s three sections (mark EMB-RV-01 done, note the actual `bills` unique constraint name confirmed in Step 9, set Current Active Task to EMB-RV-02).

```bash
git add backend/src/main/java/com/vanter/ember/billing/model backend/src/main/java/com/vanter/ember/billing/repository backend/src/main/java/com/vanter/ember/billing/service/BillingService.java backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java backend/src/main/resources/db/migration/V8__refunds_and_voids.sql backend/src/test/java/com/vanter/ember/billing/repository backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java backend/src/test/java/com/vanter/ember/billing/listener/BillingEventListenerTest.java PROGRESS.md reports/<NNN>-task-EMB-RV-01-refund-void-data-layer.md
git commit -m "feat(backend): add refund/void data layer and bill split status rename"
```

---

### Task 2: EMB-RV-02 — `BillingService.voidBill` and `PaymentService.refundPayment`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/PaymentResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/RefundResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/BillVoidedMessage.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/SplitRefundedMessage.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`

**Interfaces:**
- Consumes: Task 1's `Refund`, `BillSplitStatus`, `RefundRepository`, `BillRepository.findBySessionIdAndStatusNot`/`findByIdForUpdate` (existing), `PaymentRepository.findByIdForUpdate`/`existsByBillIdAndStatus`/`findByCashShiftId`, `Bill.voidedBy`/`voidedAt`/`voidReason`; existing `com.vanter.ember.cashregister.model.CashShift`/`CashMovement`/`CashMovementType`, `com.vanter.ember.cashregister.repository.CashShiftRepository`/`CashMovementRepository`, `com.vanter.ember.cashregister.event.CashMovementRecorded`, `com.vanter.ember.identity.repository.UserRepository`, `com.vanter.ember.config.TenantContextHolder`, `com.vanter.ember.config.ResourceNotFoundException`.
- Produces: `PaymentResponse(Long id, Long billId, String participantName, BigDecimal amount, String method, String status, LocalDateTime createdAt, BigDecimal refundedAmount, BigDecimal remaining)`; `RefundResponse(Long id, BigDecimal amount, String reason, String refundedByName, LocalDateTime createdAt)`; `BillVoidedMessage(String type, Long billId, String reason)` with `of(billId, reason)`; `SplitRefundedMessage(String type, Long billId, String participantName, String status, BigDecimal amount)` with `of(billId, participantName, status, amount)`; `BillingService.voidBill(Long billId, String reason, String voidedByEmail):Bill`; `PaymentService.refundPayment(Long paymentId, BigDecimal amount, String reason, String refundedByEmail):Refund`, `listPayments(Long billId):List<PaymentResponse>`, `listRefunds(Long paymentId):List<RefundResponse>`, `toResponses(List<Payment>):List<PaymentResponse>`.

- [ ] **Step 1: Write the failing `BillingService` void tests**

Add to `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`. Add these imports:

```java
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.config.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.UUID;
```

Add mock fields and tenant binding (mirrors `PaymentServiceTest`'s existing `@BeforeEach`/`@AfterEach` pattern):

```java
    @Mock PaymentRepository paymentRepository;
    @Mock UserRepository userRepository;
    @Mock SimpMessagingTemplate messagingTemplate;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void bindTenant() {
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }
```

Add the test cases (append near the end of the class, before the closing brace):

```java

    // --- voidBill tests ---

    private Bill openBill() {
        return Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
    }

    @Test
    void voidBill_setsStatusVoidedAndStampsReason() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(false);
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Bill voided = billingService.voidBill(1L, "wrong split method", "waiter@ember.local");

        assertThat(voided.getStatus()).isEqualTo(BillStatus.VOIDED);
        assertThat(voided.getVoidReason()).isEqualTo("wrong split method");
        assertThat(voided.getVoidedBy()).isEqualTo("user-1");
        assertThat(voided.getVoidedAt()).isNotNull();
    }

    @Test
    void voidBill_broadcastsBillVoidedToSessionTopic() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(false);
        when(userRepository.findByEmail("waiter@ember.local"))
                .thenReturn(Optional.of(User.builder().id("user-1").role(Role.WAITER).build()));
        when(billRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        billingService.voidBill(1L, "wrong split method", "waiter@ember.local");

        ArgumentCaptor<com.vanter.ember.billing.dto.BillVoidedMessage> captor =
                ArgumentCaptor.forClass(com.vanter.ember.billing.dto.BillVoidedMessage.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/session/sess-1"), captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("BILL_VOIDED");
        assertThat(captor.getValue().billId()).isEqualTo(1L);
    }

    @Test
    void voidBill_throwsWhenBillIsNotOpen() {
        Bill paid = openBill();
        paid.setStatus(BillStatus.PAID);
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> billingService.voidBill(1L, "reason", "waiter@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidBill_throwsWhenAConfirmedPaymentAlreadyExists() {
        Bill bill = openBill();
        when(billRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(bill));
        when(paymentRepository.existsByBillIdAndStatus(1L, PaymentStatus.CONFIRMED)).thenReturn(true);

        assertThatThrownBy(() -> billingService.voidBill(1L, "reason", "waiter@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void voidBill_throwsWhenBillNotFound() {
        when(billRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.voidBill(99L, "reason", "waiter@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=BillingServiceTest`
Expected: FAIL to compile — `BillingService.voidBill` does not exist yet.

- [ ] **Step 3: Implement `BillingService.voidBill`**

In `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`, add imports:

```java
import com.vanter.ember.billing.dto.BillVoidedMessage;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.model.PaymentStatus;
import com.vanter.ember.billing.repository.PaymentRepository;
import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.identity.model.User;
import com.vanter.ember.identity.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
```

(`BillStatus` and `PaymentStatus` may already be imported for other reasons — check before duplicating; `ResourceNotFoundException` is not currently imported in this file even though the class is used implicitly nowhere yet, add it.)

Add three new constructor-injected fields (Lombok `@RequiredArgsConstructor` picks them up automatically):

```java
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
```

Add the method (place after `calculateBill`, before `splitByConsumption`):

```java

    @Transactional
    public Bill voidBill(Long billId, String reason, String voidedByEmail) {
        Bill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.OPEN) {
            throw new IllegalStateException("Bill is not open: " + billId);
        }
        if (paymentRepository.existsByBillIdAndStatus(billId, PaymentStatus.CONFIRMED)) {
            throw new IllegalStateException(
                    "Cannot void a bill with a confirmed payment; refund it instead: " + billId);
        }

        bill.setStatus(BillStatus.VOIDED);
        bill.setVoidedBy(resolveUserId(voidedByEmail));
        bill.setVoidedAt(LocalDateTime.now());
        bill.setVoidReason(reason);
        Bill saved = billRepository.save(bill);

        messagingTemplate.convertAndSend(
                "/topic/session/" + bill.getSessionId(), BillVoidedMessage.of(billId, reason));

        return saved;
    }

    private String resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
```

- [ ] **Step 4: Write `BillVoidedMessage`**

```java
package com.vanter.ember.billing.dto;

public record BillVoidedMessage(String type, Long billId, String reason) {

    public static BillVoidedMessage of(Long billId, String reason) {
        return new BillVoidedMessage("BILL_VOIDED", billId, reason);
    }
}
```

- [ ] **Step 5: Run `BillingServiceTest` to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=BillingServiceTest`
Expected: PASS — every test green, including the 5 new `voidBill_*` tests.

- [ ] **Step 6: Write the failing `PaymentService.refundPayment` tests**

Add to `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`. Add imports:

```java
import com.vanter.ember.billing.model.BillSplitStatus;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
```

Add mock fields:

```java
    @Mock RefundRepository refundRepository;
    @Mock CashMovementRepository cashMovementRepository;
```

Add a helper and the test cases (append near the end of the class, before the closing brace):

```java

    // --- refundPayment tests ---

    private Payment confirmedPhysicalPayment(Bill bill, String amount) {
        return Payment.builder()
                .id(30L).bill(bill).participantName("Alice")
                .amount(new BigDecimal(amount)).method(PaymentMethod.PHYSICAL)
                .status(PaymentStatus.CONFIRMED).cashShiftId(9L).processedBy("user-1")
                .createdAt(LocalDateTime.now()).build();
    }

    private BillSplit paidSplit(Bill bill, String participant, String amount) {
        return BillSplit.builder()
                .id(10L).bill(bill).participantName(participant)
                .amount(new BigDecimal(amount)).status(BillSplitStatus.PAID).build();
    }

    @Test
    void refundPayment_fullAmount_createsRefundAndMarksSplitUnpaid() {
        Bill bill = sampleBill();
        Payment payment = confirmedPhysicalPayment(bill, "12.50");
        BillSplit split = paidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        // Two calls happen: the pre-refund balance check (sees 0 refunded so far), then
        // updateSplitStatus's post-save recompute (sees the 12.50 just issued) — Mockito's
        // consecutive-return-values feature simulates the flush a real query would observe.
        when(refundRepository.sumByPaymentId(30L)).thenReturn(BigDecimal.ZERO, new BigDecimal("12.50"));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(paymentRepository.findByBillId(1L)).thenReturn(List.of(payment));
        when(billSplitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Refund refund = paymentService.refundPayment(30L, null, "customer dispute", "alice@ember.local");

        assertThat(refund.getAmount()).isEqualByComparingTo("12.50");
        assertThat(refund.getReason()).isEqualTo("customer dispute");
        assertThat(refund.getRefundedBy()).isEqualTo("user-1");

        ArgumentCaptor<BillSplit> splitCaptor = ArgumentCaptor.forClass(BillSplit.class);
        verify(billSplitRepository).save(splitCaptor.capture());
        assertThat(splitCaptor.getValue().getStatus()).isEqualTo(BillSplitStatus.UNPAID);
    }

    @Test
    void refundPayment_partialAmount_marksSplitPartiallyPaid() {
        Bill bill = sampleBill();
        Payment payment = confirmedPhysicalPayment(bill, "12.50");
        BillSplit split = paidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        // Same two-call shape as the full-refund test above: pre-refund balance check sees 0,
        // updateSplitStatus's post-save recompute sees the 5.00 just issued.
        when(refundRepository.sumByPaymentId(30L)).thenReturn(BigDecimal.ZERO, new BigDecimal("5.00"));
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(paymentRepository.findByBillId(1L)).thenReturn(List.of(payment));
        when(billSplitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Refund refund = paymentService.refundPayment(
                30L, new BigDecimal("5.00"), "comped an item", "alice@ember.local");

        assertThat(refund.getAmount()).isEqualByComparingTo("5.00");
        ArgumentCaptor<BillSplit> splitCaptor = ArgumentCaptor.forClass(BillSplit.class);
        verify(billSplitRepository).save(splitCaptor.capture());
        assertThat(splitCaptor.getValue().getStatus()).isEqualTo(BillSplitStatus.PARTIALLY_PAID);
    }

    @Test
    void refundPayment_physical_recordsCashOutMovementOnTheCurrentOpenShift() {
        Bill bill = sampleBill();
        Payment payment = confirmedPhysicalPayment(bill, "12.50");
        BillSplit split = paidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumByPaymentId(30L)).thenReturn(BigDecimal.ZERO);
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.of(openShift()));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(paymentRepository.findByBillId(1L)).thenReturn(List.of(payment));
        when(billSplitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.refundPayment(30L, null, "customer dispute", "alice@ember.local");

        ArgumentCaptor<CashMovement> movementCaptor = ArgumentCaptor.forClass(CashMovement.class);
        verify(cashMovementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getCashShiftId()).isEqualTo(9L);
        assertThat(movementCaptor.getValue().getType()).isEqualTo(CashMovementType.CASH_OUT);
        assertThat(movementCaptor.getValue().getAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void refundPayment_physical_throwsWhenNoOpenShift() {
        Bill bill = sampleBill();
        Payment payment = confirmedPhysicalPayment(bill, "12.50");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumByPaymentId(30L)).thenReturn(BigDecimal.ZERO);
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(cashShiftRepository.findOpenForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(30L, null, "reason", "alice@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundPayment_digital_doesNotTouchCashShift() {
        Bill bill = sampleBill();
        Payment payment = Payment.builder()
                .id(31L).bill(bill).participantName("Alice")
                .amount(new BigDecimal("12.50")).method(PaymentMethod.DIGITAL)
                .status(PaymentStatus.CONFIRMED).processedBy("user-1")
                .createdAt(LocalDateTime.now()).build();
        BillSplit split = paidSplit(bill, "Alice", "12.50");
        when(paymentRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumByPaymentId(31L)).thenReturn(BigDecimal.ZERO);
        when(userRepository.findByEmail("alice@ember.local")).thenReturn(Optional.of(waiterUser()));
        when(refundRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(billSplitRepository.findByBillIdAndParticipantName(1L, "Alice"))
                .thenReturn(Optional.of(split));
        when(paymentRepository.findByBillId(1L)).thenReturn(List.of(payment));
        when(billSplitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        paymentService.refundPayment(31L, null, "customer dispute", "alice@ember.local");

        verify(cashMovementRepository, never()).save(any());
        verify(cashShiftRepository, never()).findOpenForUpdate(any());
    }

    @Test
    void refundPayment_throwsWhenPaymentNotConfirmed() {
        Bill bill = sampleBill();
        Payment pending = pendingDigitalPayment(bill, "Alice");
        when(paymentRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> paymentService.refundPayment(20L, null, "reason", "alice@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refundPayment_throwsWhenAmountExceedsRemainingBalance() {
        Bill bill = sampleBill();
        Payment payment = confirmedPhysicalPayment(bill, "12.50");
        when(paymentRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
        when(refundRepository.sumByPaymentId(30L)).thenReturn(new BigDecimal("8.00"));

        assertThatThrownBy(() -> paymentService.refundPayment(
                30L, new BigDecimal("5.00"), "reason", "alice@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refundPayment_throwsWhenPaymentNotFound() {
        when(paymentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.refundPayment(99L, null, "reason", "alice@ember.local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

`registerPhysicalPayment_throwsWhenNoOpenShift`'s `openShift()` helper builds a shift with no `tenantId`/`id` other than `9L` — `openShift().getId()` already returns `9L` from the existing helper, matching `movementCaptor.getValue().getCashShiftId()` above; no change needed to that helper.

- [ ] **Step 7: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=PaymentServiceTest`
Expected: FAIL to compile — `PaymentService.refundPayment` does not exist yet.

- [ ] **Step 8: Implement `PaymentService.refundPayment` and its response DTOs**

In `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`, add imports:

```java
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.SplitRefundedMessage;
import com.vanter.ember.billing.model.Refund;
import com.vanter.ember.billing.repository.RefundRepository;
import com.vanter.ember.cashregister.model.CashMovement;
import com.vanter.ember.cashregister.model.CashMovementType;
import com.vanter.ember.cashregister.repository.CashMovementRepository;
import com.vanter.ember.cashregister.event.CashMovementRecorded;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
```

Add two new constructor-injected fields:

```java
    private final RefundRepository refundRepository;
    private final CashMovementRepository cashMovementRepository;
```

Add the method (place after `confirmDigitalPayment`, before `resolveUserId`):

```java

    @Transactional
    public Refund refundPayment(Long paymentId, BigDecimal amount, String reason, String refundedByEmail) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new IllegalStateException("Payment is not confirmed: " + paymentId);
        }

        BigDecimal remaining = payment.getAmount().subtract(refundRepository.sumByPaymentId(paymentId));
        BigDecimal refundAmount = amount != null ? amount : remaining;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive: " + refundAmount);
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + refundAmount + " exceeds remaining balance " + remaining);
        }

        String refundedBy = resolveUserId(refundedByEmail);

        if (payment.getMethod() == PaymentMethod.PHYSICAL) {
            CashShift openShift = cashShiftRepository.findOpenForUpdate(TenantContextHolder.requireTenantId())
                    .orElseThrow(() -> new IllegalStateException(
                            "No open cash shift; open one before refunding a physical payment"));
            // Built directly rather than via CashShiftService.recordMovement to avoid a circular
            // billing<->cashregister service dependency — CashShiftService.getDetail (Task 4)
            // depends on PaymentService to list a shift's payments, so this direction must not
            // depend back on CashShiftService.
            cashMovementRepository.save(CashMovement.builder()
                    .cashShiftId(openShift.getId())
                    .type(CashMovementType.CASH_OUT)
                    .amount(refundAmount)
                    .reason("Refund of payment #" + paymentId + ": " + reason)
                    .createdBy(refundedBy)
                    .createdAt(LocalDateTime.now())
                    .build());
            eventPublisher.publishEvent(new CashMovementRecorded(openShift.getTenantId(), openShift.getId()));
        }

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .amount(refundAmount)
                .reason(reason)
                .refundedBy(refundedBy)
                .createdAt(LocalDateTime.now())
                .build());

        Long billId = payment.getBill().getId();
        BillSplit split = updateSplitStatus(billId, payment.getParticipantName());

        messagingTemplate.convertAndSend(
                "/topic/session/" + payment.getBill().getSessionId(),
                SplitRefundedMessage.of(billId, payment.getParticipantName(), split.getStatus().name(), refundAmount));

        return refund;
    }

    private BillSplit updateSplitStatus(Long billId, String participantName) {
        BillSplit split = billSplitRepository.findByBillIdAndParticipantName(billId, participantName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Split not found for participant: " + participantName));

        List<Payment> confirmedPayments = paymentRepository.findByBillId(billId).stream()
                .filter(p -> p.getParticipantName().equals(participantName)
                        && p.getStatus() == PaymentStatus.CONFIRMED)
                .toList();
        BigDecimal netPaid = confirmedPayments.stream()
                .map(p -> p.getAmount().subtract(refundRepository.sumByPaymentId(p.getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BillSplitStatus status;
        if (netPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = BillSplitStatus.UNPAID;
        } else if (netPaid.compareTo(split.getAmount()) >= 0) {
            status = BillSplitStatus.PAID;
        } else {
            status = BillSplitStatus.PARTIALLY_PAID;
        }
        split.setStatus(status);
        return billSplitRepository.save(split);
    }

    public List<PaymentResponse> listPayments(Long billId) {
        return toResponses(paymentRepository.findByBillId(billId));
    }

    public List<PaymentResponse> toResponses(List<Payment> payments) {
        return payments.stream().map(p -> {
            BigDecimal refunded = refundRepository.sumByPaymentId(p.getId());
            return new PaymentResponse(
                    p.getId(), p.getBill().getId(), p.getParticipantName(), p.getAmount(),
                    p.getMethod().name(), p.getStatus().name(), p.getCreatedAt(),
                    refunded, p.getAmount().subtract(refunded));
        }).toList();
    }

    public List<RefundResponse> listRefunds(Long paymentId) {
        List<Refund> refunds = refundRepository.findByPaymentId(paymentId);
        Set<String> userIds = refunds.stream().map(Refund::getRefundedBy).collect(Collectors.toSet());
        Map<String, String> names = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getName));
        return refunds.stream()
                .map(r -> new RefundResponse(
                        r.getId(), r.getAmount(), r.getReason(),
                        names.getOrDefault(r.getRefundedBy(), r.getRefundedBy()), r.getCreatedAt()))
                .toList();
    }
```

Add `import java.util.HashSet;` only if actually used — it is not in the final version above (`Set.of`/`Collectors.toSet` suffice); remove it from the import list if added speculatively.

- [ ] **Step 9: Write `PaymentResponse` and `RefundResponse`**

```java
package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long billId,
        String participantName,
        BigDecimal amount,
        String method,
        String status,
        LocalDateTime createdAt,
        BigDecimal refundedAmount,
        BigDecimal remaining) {}
```

```java
package com.vanter.ember.billing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RefundResponse(
        Long id, BigDecimal amount, String reason, String refundedByName, LocalDateTime createdAt) {}
```

- [ ] **Step 10: Write `SplitRefundedMessage`**

```java
package com.vanter.ember.billing.dto;

import java.math.BigDecimal;

public record SplitRefundedMessage(String type, Long billId, String participantName, String status, BigDecimal amount) {

    public static SplitRefundedMessage of(Long billId, String participantName, String status, BigDecimal amount) {
        return new SplitRefundedMessage("SPLIT_REFUNDED", billId, participantName, status, amount);
    }
}
```

- [ ] **Step 11: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green.

- [ ] **Step 12: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/billing/dto backend/src/main/java/com/vanter/ember/billing/service backend/src/test/java/com/vanter/ember/billing/service PROGRESS.md reports/<NNN>-task-EMB-RV-02-void-refund-service-logic.md
git commit -m "feat(backend): add bill void and payment refund service logic"
```

---

### Task 3: EMB-RV-03 — `BillingController` endpoints and request DTOs

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/VoidBillRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/billing/dto/RefundPaymentRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- Modify: `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

**Interfaces:**
- Consumes: Task 2's `BillingService.voidBill`, `PaymentService.refundPayment`/`listPayments`/`listRefunds`, `PaymentResponse`, `RefundResponse`; Task 1's `Refund`.
- Produces: `VoidBillRequest(String reason)`, `RefundPaymentRequest(BigDecimal amount, String reason)`; `BillingController` gains `POST /bills/{id}/void`, `GET /bills/{id}/payments`, `POST /payments/{id}/refund`, `GET /payments/{id}/refunds`.

- [ ] **Step 1: Write the failing controller tests**

Add to `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`. Add imports:

```java
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.RefundPaymentRequest;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.VoidBillRequest;
import com.vanter.ember.billing.model.Refund;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

Add helpers and test cases (append near the end of the class, before the closing brace):

```java

    private Refund sampleRefund(Payment payment) {
        return Refund.builder()
                .id(40L).payment(payment).amount(new BigDecimal("10.00"))
                .reason("customer dispute").refundedBy("user-1")
                .createdAt(LocalDateTime.now()).build();
    }

    // --- POST /billing/bills/{id}/void ---

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void voidBill_returnsOkForWaiter() throws Exception {
        Bill voided = sampleBill();
        voided.setStatus(BillStatus.VOIDED);
        when(billingService.voidBill(eq(1L), anyString(), anyString())).thenReturn(voided);

        VoidBillRequest req = new VoidBillRequest("wrong split method");
        mockMvc.perform(post("/billing/bills/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VOIDED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void voidBill_forbiddenForAdmin() throws Exception {
        VoidBillRequest req = new VoidBillRequest("reason");
        mockMvc.perform(post("/billing/bills/1/void")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- GET /billing/bills/{id}/payments ---

    @Test
    @WithMockUser(roles = "WAITER")
    void listPayments_returnsOkForWaiter() throws Exception {
        Bill bill = sampleBill();
        PaymentResponse response = new PaymentResponse(
                20L, 1L, "Alice", new BigDecimal("25.00"), "PHYSICAL", "CONFIRMED",
                LocalDateTime.now(), BigDecimal.ZERO, new BigDecimal("25.00"));
        when(paymentService.listPayments(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/billing/bills/1/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantName").value("Alice"))
                .andExpect(jsonPath("$[0].remaining").value(25.00));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void listPayments_forbiddenForCustomer() throws Exception {
        mockMvc.perform(get("/billing/bills/1/payments"))
                .andExpect(status().isForbidden());
    }

    // --- POST /billing/payments/{id}/refund ---

    @Test
    @WithMockUser(username = "waiter@ember.local", roles = "WAITER")
    void refundPayment_returnsCreatedForWaiter() throws Exception {
        Bill bill = sampleBill();
        Payment payment = samplePayment(bill);
        when(paymentService.refundPayment(eq(20L), any(), anyString(), anyString()))
                .thenReturn(sampleRefund(payment));

        RefundPaymentRequest req = new RefundPaymentRequest(new BigDecimal("10.00"), "customer dispute");
        mockMvc.perform(post("/billing/payments/20/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(10.00))
                .andExpect(jsonPath("$.reason").value("customer dispute"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void refundPayment_forbiddenForCustomer() throws Exception {
        RefundPaymentRequest req = new RefundPaymentRequest(new BigDecimal("10.00"), "reason");
        mockMvc.perform(post("/billing/payments/20/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // --- GET /billing/payments/{id}/refunds ---

    @Test
    @WithMockUser(roles = "WAITER")
    void listRefunds_returnsOkForWaiter() throws Exception {
        RefundResponse response = new RefundResponse(
                40L, new BigDecimal("10.00"), "customer dispute", "Alice", LocalDateTime.now());
        when(paymentService.listRefunds(20L)).thenReturn(List.of(response));

        mockMvc.perform(get("/billing/payments/20/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("customer dispute"));
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void listRefunds_forbiddenForKitchen() throws Exception {
        mockMvc.perform(get("/billing/payments/20/refunds"))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=BillingControllerTest`
Expected: FAIL to compile — the new `BillingController` endpoints don't exist yet.

- [ ] **Step 3: Write the request DTOs**

```java
package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record VoidBillRequest(@NotBlank String reason) {}
```

```java
package com.vanter.ember.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record RefundPaymentRequest(@Positive BigDecimal amount, @NotBlank String reason) {}
```

- [ ] **Step 4: Add the four endpoints to `BillingController`**

In `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`, add imports:

```java
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.dto.RefundPaymentRequest;
import com.vanter.ember.billing.dto.RefundResponse;
import com.vanter.ember.billing.dto.VoidBillRequest;
import com.vanter.ember.billing.model.Refund;
import org.springframework.web.bind.annotation.GetMapping;
```

Add the four endpoints (place after `confirmDigitalPayment`, before the closing brace):

```java

    @Operation(summary = "Void a bill before any payment lands (WAITER)")
    @PostMapping("/bills/{id}/void")
    @PreAuthorize("hasRole('WAITER')")
    public Bill voidBill(
            @PathVariable Long id, @Valid @RequestBody VoidBillRequest request, Authentication authentication) {
        return billingService.voidBill(id, request.reason(), authentication.getName());
    }

    @Operation(summary = "List a bill's payments with refund status (WAITER/ADMIN)")
    @GetMapping("/bills/{id}/payments")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<PaymentResponse> listPayments(@PathVariable Long id) {
        return paymentService.listPayments(id);
    }

    @Operation(summary = "Refund a confirmed payment, full or partial (WAITER)")
    @PostMapping("/payments/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('WAITER')")
    public Refund refundPayment(
            @PathVariable Long id, @Valid @RequestBody RefundPaymentRequest request, Authentication authentication) {
        return paymentService.refundPayment(id, request.amount(), request.reason(), authentication.getName());
    }

    @Operation(summary = "List refunds issued against a payment (WAITER/ADMIN)")
    @GetMapping("/payments/{id}/refunds")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<RefundResponse> listRefunds(@PathVariable Long id) {
        return paymentService.listRefunds(id);
    }
```

- [ ] **Step 5: Add the new routes to `SecurityAuditTest`**

In `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`, add to the `@CsvSource` list, immediately after the existing `"POST, /api/billing/payments/1/confirm",` row (these four use the real prefix-less path, matching the `/cash-shifts/...` rows below them — not the stale `/api/billing/...` rows above):

```java
        "POST, /billing/bills/1/void",
        "GET,  /billing/bills/1/payments",
        "POST, /billing/payments/1/refund",
        "GET,  /billing/payments/1/refunds",
```

- [ ] **Step 6: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green.

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/billing/dto/VoidBillRequest.java backend/src/main/java/com/vanter/ember/billing/dto/RefundPaymentRequest.java backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java PROGRESS.md reports/<NNN>-task-EMB-RV-03-void-refund-endpoints.md
git commit -m "feat(backend): expose void and refund endpoints on BillingController"
```

---

### Task 4: EMB-RV-04 — Surface a shift's payments on `CashShiftDetailResponse`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftDetailResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`

**Interfaces:**
- Consumes: Task 1's `PaymentRepository.findByCashShiftId`; Task 2's `PaymentService.toResponses`, `PaymentResponse`.
- Produces: `CashShiftDetailResponse(CashShiftResponse shift, List<CashMovementResponse> movements, List<PaymentResponse> payments)` (field added — signature change).

- [ ] **Step 1: Write the failing test**

Add to `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`. Add imports:

```java
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.service.PaymentService;
```

Add a mock field:

```java
    @Mock PaymentService paymentService;
```

Add the test case (append near the end of the class, before the closing brace):

```java

    @Test
    void getDetail_includesPaymentsForTheShift() {
        CashShift shift = openShift();
        when(cashShiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(paymentRepository.findByCashShiftId(1L)).thenReturn(List.of(mock(Payment.class)));
        PaymentResponse response = new PaymentResponse(
                20L, 1L, "Alice", new BigDecimal("25.00"), "PHYSICAL", "CONFIRMED",
                LocalDateTime.now(), BigDecimal.ZERO, new BigDecimal("25.00"));
        when(paymentService.toResponses(anyList())).thenReturn(List.of(response));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        CashShiftDetailResponse detail = cashShiftService.getDetail(1L);

        assertThat(detail.payments()).hasSize(1);
        assertThat(detail.payments().get(0).participantName()).isEqualTo("Alice");
    }
```

Add `import static org.mockito.ArgumentMatchers.anyList;` and `import static org.mockito.Mockito.mock;` to the test file's static imports if not already present (`anyList` is not; `mock` is not — both need adding).

Note: `openShift()` in this test file builds a shift with `id(1L)`. `getDetail` calls `getById(1L)` internally, which calls `cashShiftRepository.findById(1L)` — that stub above is what backs it.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`
Expected: FAIL to compile — `CashShiftDetailResponse` has no `payments()` accessor yet, and `CashShiftService`'s constructor doesn't accept a `PaymentService` yet.

- [ ] **Step 3: Extend `CashShiftDetailResponse`**

```java
package com.vanter.ember.cashregister.dto;

import com.vanter.ember.billing.dto.PaymentResponse;
import java.util.List;

public record CashShiftDetailResponse(
        CashShiftResponse shift, List<CashMovementResponse> movements, List<PaymentResponse> payments) {}
```

- [ ] **Step 4: Wire `PaymentService` into `CashShiftService.getDetail`**

In `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`, add imports:

```java
import com.vanter.ember.billing.dto.PaymentResponse;
import com.vanter.ember.billing.model.Payment;
import com.vanter.ember.billing.service.PaymentService;
```

Add a new constructor-injected field:

```java
    private final PaymentService paymentService;
```

Replace the body of `getDetail`:

```java
    public CashShiftDetailResponse getDetail(Long id) {
        CashShift shift = getById(id);
        List<CashMovement> movements = cashMovementRepository.findByCashShiftIdOrderByCreatedAtAsc(id);
        List<Payment> payments = paymentRepository.findByCashShiftId(id);

        Set<String> userIds = new HashSet<>();
        userIds.add(shift.getOpenedBy());
        if (shift.getClosedBy() != null) userIds.add(shift.getClosedBy());
        movements.forEach(m -> userIds.add(m.getCreatedBy()));
        Map<String, String> names = resolveNames(userIds);

        List<CashMovementResponse> movementResponses =
                movements.stream().map(m -> toMovementResponse(m, names)).toList();
        List<PaymentResponse> paymentResponses = paymentService.toResponses(payments);

        return new CashShiftDetailResponse(toResponse(shift, names), movementResponses, paymentResponses);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`
Expected: PASS — every test green, including the new `getDetail_includesPaymentsForTheShift`.

- [ ] **Step 6: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green. This confirms no circular-bean-dependency startup failure (`CashShiftService` now depends on `PaymentService`, which does not depend back on `CashShiftService` — see Task 2 Step 8's comment).

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftDetailResponse.java backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java PROGRESS.md reports/<NNN>-task-EMB-RV-04-shift-detail-payments.md
git commit -m "feat(backend): surface a cash shift's payments on its detail response"
```

---

### Task 5: EMB-RV-05 — `AnalyticsService` nets out refunds

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- Modify: `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`

**Interfaces:**
- Consumes: Task 1's `RefundRepository.sumRefundsInWindow`, `findRefundsByDay`, `RefundDailyAmount`.
- Produces: `AnalyticsService.getSummary`/`getSales` — behavior change only, no signature change.

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`. Add imports:

```java
import com.vanter.ember.billing.repository.RefundDailyAmount;
import com.vanter.ember.billing.repository.RefundRepository;
```

Add a mock field:

```java
    @Mock RefundRepository refundRepository;
```

Add the test cases (place near the other `getSummary`/`getSales` tests):

```java

    @Test
    void getSummary_netsOutRefundsIssuedInTheWindow() {
        when(paymentRepository.sumConfirmedRevenue(eq(TENANT_ID), any(), any()))
                .thenReturn(new BigDecimal("100.00"));
        when(refundRepository.sumRefundsInWindow(eq(TENANT_ID), any(), any()))
                .thenReturn(new BigDecimal("15.00"));
        when(billRepository.findSalesTotals(eq(TENANT_ID), any(), any())).thenReturn(null);
        when(sessionRepository.countByTenantIdAndStatus(any(), any())).thenReturn(0L);

        AnalyticsSummaryResponse summary = analyticsService.getSummary(TENANT_ID, FROM, TO);

        assertThat(summary.revenue()).isEqualByComparingTo("85.00");
    }

    @Test
    void getSales_netsOutRefundsPerBucket() {
        when(paymentRepository.findConfirmedRevenueByDay(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(new PaymentDailyRevenue(2026, 8, 17, new BigDecimal("100.00"))));
        when(refundRepository.findRefundsByDay(eq(TENANT_ID), any(), any()))
                .thenReturn(List.of(new RefundDailyAmount(2026, 8, 17, new BigDecimal("15.00"))));
        when(billRepository.findPaidBillsByDay(eq(TENANT_ID), any(), any())).thenReturn(List.of());

        AnalyticsSalesResponse sales = analyticsService.getSales(
                TENANT_ID, "day", LocalDateTime.of(2026, 8, 17, 0, 0), LocalDateTime.of(2026, 8, 17, 23, 59));

        assertThat(sales.totalRevenue()).isEqualByComparingTo("85.00");
        assertThat(sales.buckets()).singleElement()
                .satisfies(bucket -> assertThat(bucket.revenue()).isEqualByComparingTo("85.00"));
    }
```

`PaymentDailyRevenue` is already imported in this test file (it backs the existing `getSales` tests) — confirm before adding a duplicate import.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=AnalyticsServiceTest`
Expected: FAIL — `getSummary`'s revenue is `100.00` (unadjusted) and `getSales`'s bucket revenue is `100.00`; the two new assertions fail (`refundRepository` isn't consulted yet). Every pre-existing test in this file still compiles and passes unchanged — an unstubbed `refundRepository` mock returns `null`/an empty list by Mockito's default, which the code below treats as zero, so no other test needs a new stub.

- [ ] **Step 3: Wire `RefundRepository` into `AnalyticsService`**

In `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`, add imports:

```java
import com.vanter.ember.billing.repository.RefundDailyAmount;
import com.vanter.ember.billing.repository.RefundRepository;
```

Add a new constructor-injected field (place with the other repository fields near the top of the class):

```java
    private final RefundRepository refundRepository;
```

In `getSummary`, replace:

```java
        BigDecimal revenue = paymentRepository.sumConfirmedRevenue(restaurantId, windowStart, windowEnd);
```

with:

```java
        BigDecimal revenue = paymentRepository.sumConfirmedRevenue(restaurantId, windowStart, windowEnd);
        BigDecimal refunds = refundRepository.sumRefundsInWindow(restaurantId, windowStart, windowEnd);
        BigDecimal netRevenue = (revenue == null ? BigDecimal.ZERO : revenue)
                .subtract(refunds == null ? BigDecimal.ZERO : refunds);
```

and replace the final `scaled(revenue == null ? BigDecimal.ZERO : revenue)` argument in the returned `AnalyticsSummaryResponse` with `scaled(netRevenue)`.

In `getSales`, immediately after the existing `for (PaymentDailyRevenue row : ...)` loop that populates `revenueByBucket`, add:

```java
        for (RefundDailyAmount row :
                refundRepository.findRefundsByDay(restaurantId, window.start(), window.end())) {
            revenueByBucket.merge(
                    granularity.bucketStart(row.date()),
                    (row.amount() == null ? BigDecimal.ZERO : row.amount()).negate(),
                    BigDecimal::add);
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=AnalyticsServiceTest`
Expected: PASS — every test green, including the 2 new refund-netting tests, with every pre-existing test unchanged and still passing.

- [ ] **Step 5: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS — every test green. This is the last backend task — the backend stream ends here.

- [ ] **Step 6: Report, update PROGRESS.md, and commit**

```bash
git add backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java PROGRESS.md reports/<NNN>-task-EMB-RV-05-analytics-refund-netting.md
git commit -m "fix(backend): net refunds out of analytics revenue figures"
```

---

### Task 6: EMB-RV-06 — Frontend shared prep (API client, stores, WebSocket handlers)

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/store/uiStore.ts`
- Modify: `frontend/src/store/sessionStore.tsx`
- Modify: `frontend/src/store/websocket.ts`

**Interfaces:**
- Consumes: Tasks 1–5's finished backend schema (regenerated into `backend-types.ts` in Step 1 below).
- Produces: `billingService.voidBill(billId, reason): Promise<Bill>`, `listPayments(billId): Promise<PaymentResponse[]>`, `refundPayment(paymentId, amount, reason): Promise<Refund>`, `listRefunds(paymentId): Promise<RefundResponse[]>`; types `PaymentResponse`, `RefundResponse`; `uiStore`'s `ModalType` gains `'VOID_BILL' | 'REFUND_PAYMENT'`; `sessionStore`'s `markSplitPaid(participantName)` becomes `markSplitStatus(participantName, status)`, plus a new `clearBill()`; `websocket.ts`'s `subscribeToSession`/`subscribeToWaiterSession` handle `BILL_VOIDED` and `SPLIT_REFUNDED`, and their existing `SPLIT_PAID` handling switches from a `paid` boolean to a `status` string.

- [ ] **Step 1: Regenerate `backend-types.ts` against the finished backend**

Run (in one terminal): `cd backend && ./mvnw spring-boot:run` — wait for it to finish booting (watch for `Started EmberApplication`).
Run (in a second terminal): `cd frontend && pnpm run openapi`
Expected: `frontend/src/lib/backend-types.ts` is rewritten and now contains `PaymentResponse`, `RefundResponse`, `VoidBillRequest`, `RefundPaymentRequest`, `Refund` schemas, and `CashShiftDetailResponse`'s schema now includes a `payments` field. Stop the backend (`Ctrl+C` in the first terminal) once the regen finishes.

- [ ] **Step 2: Add the new types and `billingService` methods to `api.ts`**

In `frontend/src/lib/api.ts`, add near the existing `export type Bill = ...` / `export type BillSplit = ...` / `export type Payment = ...` block:

```typescript
export type PaymentResponse = components['schemas']['PaymentResponse']
export type RefundResponse = components['schemas']['RefundResponse']
export type Refund = components['schemas']['Refund']
```

In the `billingService` object, add after the existing `confirmDigitalPayment` method:

```typescript
  voidBill: async (billId: number, reason: string): Promise<Bill> => {
    const { data } = await api.post<Bill>(`/billing/bills/${billId}/void`, { reason })
    return data
  },
  listPayments: async (billId: number): Promise<PaymentResponse[]> => {
    const { data } = await api.get<PaymentResponse[]>(`/billing/bills/${billId}/payments`)
    return data
  },
  refundPayment: async (
    paymentId: number,
    amount: number | undefined,
    reason: string
  ): Promise<Refund> => {
    const { data } = await api.post<Refund>(`/billing/payments/${paymentId}/refund`, { amount, reason })
    return data
  },
  listRefunds: async (paymentId: number): Promise<RefundResponse[]> => {
    const { data } = await api.get<RefundResponse[]>(`/billing/payments/${paymentId}/refunds`)
    return data
  },
```

- [ ] **Step 3: Extend `uiStore`'s `ModalType`**

In `frontend/src/store/uiStore.ts`, replace:

```typescript
export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |
                         'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT' |
                         'CHARGE_TABLE' | 'CREATE_STAFF' | 'EDIT_STAFF' | 'DELETE_STAFF' | null;
```

with:

```typescript
export type ModalType = 'CREATE_CATEGORY' | 'EDIT_CATEGORY' | 'DELETE_CATEGORY' |
                        'CREATE_ITEMS' | 'EDIT_ITEMS' | 'DELETE_ITEMS' |
                         'PARTICIPANTS_QR' | 'JOIN_TABLE' | 'TENANT_SUSPENDED' |
                         'OPEN_SHIFT' | 'CASH_MOVEMENT' | 'CLOSE_SHIFT' |
                         'CHARGE_TABLE' | 'CREATE_STAFF' | 'EDIT_STAFF' | 'DELETE_STAFF' |
                         'VOID_BILL' | 'REFUND_PAYMENT' | null;
```

- [ ] **Step 4: Rename `sessionStore`'s `markSplitPaid` to `markSplitStatus` and add `clearBill`**

In `frontend/src/store/sessionStore.tsx`, replace:

```typescript
  setBillReady: (bill: Bill, splits: BillSplit[]) => void
  markSplitPaid: (participantName: string) => void
```

with:

```typescript
  setBillReady: (bill: Bill, splits: BillSplit[]) => void
  markSplitStatus: (participantName: string, status: string) => void
  clearBill: () => void
```

and replace:

```typescript
      markSplitPaid: (participantName) => {
        set((state) => ({
          billSplits: (state.billSplits || []).map((split) =>
            split.participantName === participantName
              ? { ...split, paid: true }
              : split
          ),
        }))
      },
```

with:

```typescript
      markSplitStatus: (participantName, status) => {
        set((state) => ({
          billSplits: (state.billSplits || []).map((split) =>
            split.participantName === participantName
              ? { ...split, status }
              : split
          ),
        }))
      },
      clearBill: () => {
        set({ bill: undefined, billSplits: undefined })
      },
```

- [ ] **Step 5: Update `websocket.ts`'s two session handlers**

In `frontend/src/store/websocket.ts`, in `subscribeToSession` (the customer-facing handler), replace:

```typescript
            if(eventData.type === 'SPLIT_PAID'){
                useSessionStore.getState().markSplitPaid(eventData.participantName)
            }
```

with:

```typescript
            if(eventData.type === 'SPLIT_PAID'){
                useSessionStore.getState().markSplitStatus(eventData.participantName, eventData.status)
            }
            if(eventData.type === 'SPLIT_REFUNDED'){
                useSessionStore.getState().markSplitStatus(eventData.participantName, eventData.status)
            }
            if(eventData.type === 'BILL_VOIDED'){
                useSessionStore.getState().clearBill()
            }
```

In `subscribeToWaiterSession` (the waiter-facing handler), replace:

```typescript
            if(eventData.type === 'SPLIT_PAID'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            splits: old.splits.map((split) =>
                                split.participantName === eventData.participantName
                                    ? { ...split, paid: true }
                                    : split
                            ),
                            pendingDigitalPayments: (old.pendingDigitalPayments || []).filter(
                                (p) => p.participantName !== eventData.participantName
                            ),
                        }
                        : old
                )
            }
```

with:

```typescript
            if(eventData.type === 'SPLIT_PAID'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            splits: old.splits.map((split) =>
                                split.participantName === eventData.participantName
                                    ? { ...split, status: eventData.status }
                                    : split
                            ),
                            pendingDigitalPayments: (old.pendingDigitalPayments || []).filter(
                                (p) => p.participantName !== eventData.participantName
                            ),
                        }
                        : old
                )
            }
            if(eventData.type === 'SPLIT_REFUNDED'){
                queryClient.setQueryData<WaiterBillState | undefined>(['bill', sessionId], (old) =>
                    old
                        ? {
                            ...old,
                            splits: old.splits.map((split) =>
                                split.participantName === eventData.participantName
                                    ? { ...split, status: eventData.status }
                                    : split
                            ),
                        }
                        : old
                )
            }
            if(eventData.type === 'BILL_VOIDED'){
                queryClient.removeQueries({queryKey: ['bill', sessionId]})
            }
```

(`SESSION_CLOSED`'s existing `queryClient.removeQueries({queryKey: ['bill', sessionId]})` immediately below is untouched — `BILL_VOIDED` needs the identical call, just on a different trigger.)

- [ ] **Step 6: Verify the frontend build**

Run: `cd frontend && pnpm run build`
Expected: PASS. If `tsc -b` flags `BillSplit['status']`/`PendingDigitalPayment` shape mismatches, they mean `backend-types.ts` wasn't actually regenerated in Step 1 against the Task 1–5 backend — re-run Step 1 rather than hand-patching types.

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/lib/api.ts frontend/src/lib/backend-types.ts frontend/src/store/uiStore.ts frontend/src/store/sessionStore.tsx frontend/src/store/websocket.ts PROGRESS.md reports/<NNN>-task-EMB-RV-06-frontend-shared-prep.md
git commit -m "feat(frontend): add refund/void API client, store and websocket wiring"
```

---

### Task 7: EMB-RV-07 — Void/refund UI on the live waiter table view

**Files:**
- Create: `frontend/src/pages/waiter/components/VoidBillModal.tsx`
- Create: `frontend/src/pages/waiter/components/RefundPaymentModal.tsx`
- Modify: `frontend/src/pages/waiter/TableInformation.tsx`
- Modify: `frontend/src/pages/customer/Bill.tsx`

**Interfaces:**
- Consumes: Task 6's `billingService.voidBill`/`listPayments`/`refundPayment`, `uiStore`'s `VOID_BILL`/`REFUND_PAYMENT` modal types, `PaymentResponse`.
- Produces: two new modal components wired into `TableInformation.tsx`; no new exports consumed elsewhere in this task.

- [ ] **Step 1: Write `VoidBillModal`**

```tsx
import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'
import toast from 'react-hot-toast'

export const VoidBillModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'VOID_BILL'

  const mutation = useMutation({
    mutationFn: () => billingService.voidBill(modalPayload.billId, reason),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['bill', modalPayload.sessionId] })
      toast.success('Cuenta anulada.')
      handleClose()
    },
    onError: () => toast.error('No se pudo anular la cuenta.'),
  })

  const handleClose = () => {
    setReason('')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Anular Cuenta</DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            La cuenta calculada se anula y la mesa queda libre para recalcularla.
          </DialogDescription>
        </DialogHeader>
        <Textarea
          placeholder="Motivo de la anulación"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || reason.trim().length === 0}
          >
            Anular
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

If `frontend/src/components/ui/textarea.tsx` does not already exist, add it via the shadcn CLI before this step: `cd frontend && npx shadcn@latest add textarea` (check `ls frontend/src/components/ui/textarea.tsx` first — do not overwrite an existing file).

- [ ] **Step 2: Write `RefundPaymentModal`**

```tsx
import { useState, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { useUIStore } from '@/store/uiStore'
import { billingService } from '@/lib/api'
import toast from 'react-hot-toast'

export const RefundPaymentModal = () => {
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState('')
  const [reason, setReason] = useState('')

  const isOpen = activeModal === 'REFUND_PAYMENT'

  const { data: payments } = useQuery({
    queryKey: ['billPayments', modalPayload?.billId],
    queryFn: () => billingService.listPayments(modalPayload.billId),
    enabled: isOpen && !!modalPayload?.billId,
  })

  const payment = payments?.find((p) => p.participantName === modalPayload?.participantName)

  useEffect(() => {
    if (payment) {
      setAmount(String(payment.remaining ?? 0))
    }
  }, [payment])

  const mutation = useMutation({
    mutationFn: () =>
      billingService.refundPayment(payment!.id!, amount ? Number(amount) : undefined, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['billPayments', modalPayload.billId] })
      toast.success('Reembolso registrado.')
      handleClose()
    },
    onError: () => toast.error('No se pudo registrar el reembolso. ¿Hay una caja abierta?'),
  })

  const handleClose = () => {
    setAmount('')
    setReason('')
    closeModal()
  }

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">Reembolsar Pago</DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {modalPayload?.participantName} · saldo disponible: ${payment?.remaining?.toFixed(2) ?? '—'}
          </DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-3">
          <Input
            type="number"
            step="0.01"
            placeholder="Monto a reembolsar"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <Textarea
            placeholder="Motivo del reembolso"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
        </div>
        <DialogFooter className="mt-4">
          <Button variant="outline" onClick={handleClose}>
            Cancelar
          </Button>
          <Button
            variant="destructive"
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || !payment || reason.trim().length === 0}
          >
            Reembolsar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

If `frontend/src/components/ui/input.tsx` does not already exist, add it via the shadcn CLI: `cd frontend && npx shadcn@latest add input` (check first, do not overwrite).

- [ ] **Step 3: Wire both modals into `TableInformation.tsx`**

In `frontend/src/pages/waiter/TableInformation.tsx`, add imports:

```typescript
import { VoidBillModal } from './components/VoidBillModal'
import { RefundPaymentModal } from './components/RefundPaymentModal'
import { Ban, RotateCcw } from 'lucide-react'
```

(add `Ban, RotateCcw` to the existing `lucide-react` import block rather than a second import line).

Replace the `CardHeader` block:

```tsx
            <CardHeader className="p-7 border-b border">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                {billData ? 'Cuenta' : 'Resumen'}
              </CardTitle>
            </CardHeader>
```

with:

```tsx
            <CardHeader className="p-7 border-b border flex flex-row items-center justify-between">
              <CardTitle className="text-2xl text-gray-800 font-bold">
                {billData ? 'Cuenta' : 'Resumen'}
              </CardTitle>
              {billData && !billData.splits.some((s) => s.status !== 'UNPAID') && (
                <Button
                  variant="ghost"
                  className="text-sm text-destructive"
                  onClick={() => openModal('VOID_BILL', { billId: billData.id, sessionId: id })}
                >
                  <Ban className="w-4 h-4 mr-2" /> Anular Cuenta
                </Button>
              )}
            </CardHeader>
```

Replace the split-row rendering block (the `{split.paid ? (...` conditional):

```tsx
                        {split.paid ? (
                          <Badge className="flex items-center gap-1">
                            <CheckCircle2 className="w-4 h-4" /> Pagado
                          </Badge>
                        ) : pendingDigital ? (
```

with:

```tsx
                        {split.status === 'PAID' || split.status === 'PARTIALLY_PAID' ? (
                          <div className="flex items-center gap-2">
                            <Badge className="flex items-center gap-1">
                              <CheckCircle2 className="w-4 h-4" />
                              {split.status === 'PAID' ? 'Pagado' : 'Pago parcial'}
                            </Badge>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() =>
                                openModal('REFUND_PAYMENT', {
                                  billId: billData.id,
                                  sessionId: id,
                                  participantName: split.participantName,
                                })
                              }
                            >
                              <RotateCcw className="w-4 h-4" />
                            </Button>
                          </div>
                        ) : pendingDigital ? (
```

Add the two new modals at the bottom of the component's JSX, alongside the existing `<ChargeTableModal />`/`<GlobalDeleteModal />` renders (find that block and add both new modals next to them):

```tsx
      <VoidBillModal />
      <RefundPaymentModal />
```

- [ ] **Step 4: Update `Bill.tsx`'s status reads**

In `frontend/src/pages/customer/Bill.tsx`, replace:

```tsx
                    {split.paid ? (
```

with:

```tsx
                    {split.status === 'PAID' || split.status === 'PARTIALLY_PAID' ? (
```

and replace:

```tsx
            {mySplit && !mySplit.paid && (
```

with:

```tsx
            {mySplit && mySplit.status === 'UNPAID' && (
```

- [ ] **Step 5: Verify the frontend build**

Run: `cd frontend && pnpm run build`
Expected: PASS.

- [ ] **Step 6: Manual verification**

Run: `cd frontend && pnpm run dev` (and `cd backend && ./mvnw spring-boot:run` in another terminal if not already running). As WAITER, open a table's bill (calculate it via "Cobrar Mesa"), confirm "Anular Cuenta" appears while no split is paid and disappears once a payment is registered, void it and confirm the panel returns to "Resumen". Then calculate a fresh bill, register a physical payment for one split, confirm the refund icon appears next to "Pagado", open it, confirm the remaining balance and participant name are correct, submit a partial refund, and confirm the badge changes to "Pago parcial". Stop the dev server when done.

- [ ] **Step 7: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/pages/waiter/components/VoidBillModal.tsx frontend/src/pages/waiter/components/RefundPaymentModal.tsx frontend/src/pages/waiter/TableInformation.tsx frontend/src/pages/customer/Bill.tsx frontend/src/components/ui PROGRESS.md reports/<NNN>-task-EMB-RV-07-live-void-refund-ui.md
git commit -m "feat(frontend): add void and refund actions to the waiter table view"
```

---

### Task 8: EMB-RV-08 — Historical payments + refund on the admin cash-register view

**Files:**
- Modify: `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`

**Interfaces:**
- Consumes: Task 6's `cashShiftService.detail` (existing, previously unused by any UI), `billingService`; Task 7's `RefundPaymentModal` (reused, not duplicated).

- [ ] **Step 1: Add an expandable detail row**

In `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`, add imports:

```typescript
import { useQuery } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { RotateCcw } from 'lucide-react'
import { useUIStore } from '@/store/uiStore'
import { RefundPaymentModal } from '@/pages/waiter/components/RefundPaymentModal'
```

(`useQuery` and `cashShiftService` are already imported for the existing `history` query — do not duplicate; only add what's missing: `Button`, `RotateCcw`, `useUIStore`, `RefundPaymentModal`.)

Add state and the `openModal` hook near the top of the component body:

```typescript
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const { openModal } = useUIStore()

  const { data: detail } = useQuery({
    queryKey: ['cashShiftDetail', expandedId],
    queryFn: () => cashShiftService.detail(expandedId!),
    enabled: expandedId !== null,
  })
```

(`useState` is already imported for `page` — reuse it, don't add a second import.)

Replace the row-rendering block:

```tsx
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
```

with:

```tsx
                data.content.map((shift) => (
                  <>
                    <TableRow
                      key={shift.id}
                      className="cursor-pointer"
                      onClick={() => setExpandedId(expandedId === shift.id ? null : shift.id!)}
                    >
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
                    {expandedId === shift.id && (
                      <TableRow key={`${shift.id}-detail`}>
                        <TableCell colSpan={7} className="bg-muted/30">
                          {!detail ? (
                            <div className="py-3 text-sm text-muted-foreground">Cargando pagos...</div>
                          ) : detail.payments.length === 0 ? (
                            <div className="py-3 text-sm text-muted-foreground">Sin pagos en este turno.</div>
                          ) : (
                            <div className="flex flex-col gap-2 py-2">
                              {detail.payments.map((payment) => (
                                <div
                                  key={payment.id}
                                  className="flex items-center justify-between px-2 py-1"
                                >
                                  <span className="text-sm">
                                    {payment.participantName} — {formatCurrency(payment.amount ?? 0)}
                                    {payment.refundedAmount && payment.refundedAmount > 0
                                      ? ` (reembolsado ${formatCurrency(payment.refundedAmount)})`
                                      : ''}
                                  </span>
                                  <Button
                                    variant="ghost"
                                    size="sm"
                                    disabled={!payment.remaining || payment.remaining <= 0}
                                    onClick={(e) => {
                                      e.stopPropagation()
                                      openModal('REFUND_PAYMENT', {
                                        billId: payment.billId,
                                        participantName: payment.participantName,
                                      })
                                    }}
                                  >
                                    <RotateCcw className="w-4 h-4 mr-1" /> Reembolsar
                                  </Button>
                                </div>
                              ))}
                            </div>
                          )}
                        </TableCell>
                      </TableRow>
                    )}
                  </>
                ))
```

Add `<RefundPaymentModal />` once, right before the component's closing `</>`  (so refunding from this page reuses Task 7's modal rather than a duplicate).

- [ ] **Step 2: Verify the frontend build**

Run: `cd frontend && pnpm run build`
Expected: PASS.

- [ ] **Step 3: Manual verification**

Run: `cd frontend && pnpm run dev` (backend running too). As ADMIN, navigate to `/admin/cash-register`, click a shift row with at least one physical payment (open one as WAITER and register a payment first if none exist), confirm the expanded row lists that payment, click "Reembolsar", confirm the modal opens with the right participant and remaining balance, and complete a refund. Stop the dev server when done.

- [ ] **Step 4: Report, update PROGRESS.md, and commit**

```bash
git add frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx PROGRESS.md reports/<NNN>-task-EMB-RV-08-admin-historical-refund-ui.md
git commit -m "feat(frontend): surface historical shift payments with a refund action"
```
