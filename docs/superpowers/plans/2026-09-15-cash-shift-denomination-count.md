# Cash Shift Denomination Count Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single typed-in amount in the accountant's open/close cash-shift flow with a
real denomination count (bills and coins, quantity × value, auto-summed), matching how a proper
arqueo de caja is done, and persist the breakdown as an audit trail.

**Architecture:** New backend model types (`Denomination`, `DenominationKind`, `DenominationCount`,
`NicaraguaDenominations`) plus 3 new JSON/text columns on `CashShift`
(`opening_breakdown`/`closing_breakdown`/`close_notes`). `CashShiftService` re-validates any
submitted breakdown (legal denomination + sum matches the total) before persisting — never trusts
the client's math. Frontend gets one new shared `DenominationCounter` component (a 14-row
quantity grid, auto-summing, read-only total) reused by both `OpenShiftDialog` and
`CloseShiftDialog`, replacing their single number input; `CloseShiftDialog` also gains an optional
notes field. `ShiftHistoryTable`'s per-shift detail row renders both breakdowns + notes for admins.

**Tech Stack:** Spring Boot 3.5.14 (Java 17), Hibernate `@JdbcTypeCode(SqlTypes.JSON)` for the
breakdown columns, Flyway migration, React 19 + TypeScript + TanStack Query + Vitest/RTL on the
frontend.

**Spec:** `docs/superpowers/specs/2026-09-15-cash-shift-denomination-count-design.md`

## Global Constraints

- 14 denominations total (7 banknotes + 7 coins), sourced from the Banco Central de Nicaragua:
  bills 1000, 500, 200, 100, 50, 20, 10 córdobas; coins 10, 5, 1 córdoba and 0.50, 0.25, 0.10, 0.05
  córdoba (fractional centavos). **Finding made while writing this plan, not in the spec:** the
  C$10 denomination exists as *both* a banknote and a coin per the BCN — they are two separate rows
  (different `id`s, same `value`), not a duplicate to collapse.
- Both the opening float and the closing count get the denomination grid — not just the close.
- Storage is JSON columns on `CashShift`, matching the existing `@JdbcTypeCode(SqlTypes.JSON)`
  pattern already used by `PrintAgent.discoveredPrinters` — no new relational table.
- The close-shift notes field is always optional; never enforced server-side or client-side.
- The backend always re-validates that a submitted breakdown's sum matches the submitted total —
  never trusts the frontend's arithmetic.
- `breakdown` is optional on both open and close requests (nullable) — a request without it must
  keep working exactly as it does today, for backward compatibility.

---

## Task 1: Denomination model types

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/DenominationKind.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/Denomination.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/NicaraguaDenominations.java`
- Create: `backend/src/main/java/com/vanter/ember/cashregister/model/DenominationCount.java`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/model/NicaraguaDenominationsTest.java`

**Interfaces:**
- Produces: `NicaraguaDenominations.ALL` (`List<Denomination>`, 14 entries) and
  `NicaraguaDenominations.byId(String id)` (`Optional<Denomination>`) — Task 3's service validation
  consumes both. `DenominationCount(String denominationId, int quantity)` — the record persisted on
  `CashShift` (Task 2) and carried in the open/close requests and response (Task 3).

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.cashregister.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NicaraguaDenominationsTest {

    @Test
    void all_hasFourteenEntriesCoveringBillsAndCoins() {
        assertThat(NicaraguaDenominations.ALL).hasSize(14);
        assertThat(NicaraguaDenominations.ALL.stream().filter(d -> d.kind() == DenominationKind.BILL))
                .hasSize(7);
        assertThat(NicaraguaDenominations.ALL.stream().filter(d -> d.kind() == DenominationKind.COIN))
                .hasSize(7);
    }

    @Test
    void all_idsAreUnique() {
        long distinctIds = NicaraguaDenominations.ALL.stream().map(Denomination::id).distinct().count();
        assertThat(distinctIds).isEqualTo(14);
    }

    @Test
    void byId_findsTheTenCordobaBillAndTheTenCordobaCoinAsDistinctEntries() {
        assertThat(NicaraguaDenominations.byId("bill_10")).isPresent();
        assertThat(NicaraguaDenominations.byId("coin_10")).isPresent();
        assertThat(NicaraguaDenominations.byId("bill_10").get().value())
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(NicaraguaDenominations.byId("coin_10").get().value())
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(NicaraguaDenominations.byId("bill_10").get().kind()).isEqualTo(DenominationKind.BILL);
        assertThat(NicaraguaDenominations.byId("coin_10").get().kind()).isEqualTo(DenominationKind.COIN);
    }

    @Test
    void byId_returnsEmptyForAnUnknownId() {
        assertThat(NicaraguaDenominations.byId("bill_777")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=NicaraguaDenominationsTest test`
Expected: FAIL to compile — `NicaraguaDenominations`, `Denomination`, `DenominationKind` don't exist
yet.

- [ ] **Step 3: Write the model types**

```java
package com.vanter.ember.cashregister.model;

public enum DenominationKind {
    BILL, COIN
}
```

```java
package com.vanter.ember.cashregister.model;

import java.math.BigDecimal;

/** One legal córdoba denomination — a fixed catalog entry, never persisted on its own. */
public record Denomination(String id, BigDecimal value, DenominationKind kind) {}
```

```java
package com.vanter.ember.cashregister.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The 14 córdoba denominations currently in circulation, per the Banco Central de Nicaragua
 * (bcn.gob.ni/billetes-actuales, bcn.gob.ni/monedas-actuales). The C$10 denomination exists as
 * both a banknote and a coin at the same time — {@code bill_10} and {@code coin_10} are two
 * distinct rows with the same {@link Denomination#value()}, not a duplicate.
 */
public final class NicaraguaDenominations {

    public static final List<Denomination> ALL = List.of(
            new Denomination("bill_1000", new BigDecimal("1000.00"), DenominationKind.BILL),
            new Denomination("bill_500", new BigDecimal("500.00"), DenominationKind.BILL),
            new Denomination("bill_200", new BigDecimal("200.00"), DenominationKind.BILL),
            new Denomination("bill_100", new BigDecimal("100.00"), DenominationKind.BILL),
            new Denomination("bill_50", new BigDecimal("50.00"), DenominationKind.BILL),
            new Denomination("bill_20", new BigDecimal("20.00"), DenominationKind.BILL),
            new Denomination("bill_10", new BigDecimal("10.00"), DenominationKind.BILL),
            new Denomination("coin_10", new BigDecimal("10.00"), DenominationKind.COIN),
            new Denomination("coin_5", new BigDecimal("5.00"), DenominationKind.COIN),
            new Denomination("coin_1", new BigDecimal("1.00"), DenominationKind.COIN),
            new Denomination("coin_050", new BigDecimal("0.50"), DenominationKind.COIN),
            new Denomination("coin_025", new BigDecimal("0.25"), DenominationKind.COIN),
            new Denomination("coin_010", new BigDecimal("0.10"), DenominationKind.COIN),
            new Denomination("coin_005", new BigDecimal("0.05"), DenominationKind.COIN));

    private NicaraguaDenominations() {}

    public static Optional<Denomination> byId(String id) {
        return ALL.stream().filter(d -> d.id().equals(id)).findFirst();
    }
}
```

```java
package com.vanter.ember.cashregister.model;

/**
 * How many of one denomination were counted. Persisted as a JSON array on
 * {@link CashShift#getOpeningBreakdown()}/{@link CashShift#getClosingBreakdown()} — {@code
 * denominationId} refers to {@link NicaraguaDenominations#byId(String)}, the face value is never
 * stored here so it can't drift from the catalog.
 */
public record DenominationCount(String denominationId, int quantity) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -Dtest=NicaraguaDenominationsTest test`
Expected: PASS, 4/4.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/model/DenominationKind.java \
        backend/src/main/java/com/vanter/ember/cashregister/model/Denomination.java \
        backend/src/main/java/com/vanter/ember/cashregister/model/NicaraguaDenominations.java \
        backend/src/main/java/com/vanter/ember/cashregister/model/DenominationCount.java \
        backend/src/test/java/com/vanter/ember/cashregister/model/NicaraguaDenominationsTest.java
git commit -m "feat(cashregister): add the 14 Nicaragua denomination catalog"
```

---

## Task 2: `CashShift` breakdown columns + migration

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java`
- Create: `backend/src/main/resources/db/migration/V12__cash_shift_denomination_breakdown.sql`
- Test: `backend/src/test/java/com/vanter/ember/cashregister/repository/CashShiftRepositoryTest.java`
  (extend if it exists, else create)

**Interfaces:**
- Consumes: `DenominationCount` from Task 1.
- Produces: `CashShift.getOpeningBreakdown()/setOpeningBreakdown(List<DenominationCount>)`,
  `getClosingBreakdown()/setClosingBreakdown(...)`, `getCloseNotes()/setCloseNotes(String)` — Task 3
  reads/writes these.

- [ ] **Step 1: Check whether a repository round-trip test file already exists**

Run: `find backend/src/test/java/com/vanter/ember/cashregister/repository -iname "CashShiftRepositoryTest.java"`

If it exists, add the test in Step 2 as a new `@Test` method inside it (matching its existing
`@DataJpaTest` setup). If it does not exist, create it fresh with this shape (adjust the tenant
resolver import to match whatever other `@DataJpaTest` classes in this codebase already import —
grep any other `cashregister` or `kitchen` `@DataJpaTest` for the exact `@Import` needed).

- [ ] **Step 2: Write the failing test**

```java
    @Test
    void save_roundTripsTheDenominationBreakdownAndCloseNotesThroughJson() {
        CashShift shift = CashShift.builder()
                .shiftNumber(1).status(CashShiftStatus.OPEN)
                .openingFloat(new BigDecimal("100.00")).openedBy("user-1")
                .openedAt(LocalDateTime.now())
                .openingBreakdown(List.of(new DenominationCount("bill_100", 1)))
                .build();
        CashShift saved = cashShiftRepository.saveAndFlush(shift);
        entityManager.clear();

        CashShift reloaded = cashShiftRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getOpeningBreakdown()).containsExactly(new DenominationCount("bill_100", 1));
        assertThat(reloaded.getClosingBreakdown()).isNull();
        assertThat(reloaded.getCloseNotes()).isNull();

        reloaded.setStatus(CashShiftStatus.CLOSED);
        reloaded.setClosingBreakdown(List.of(new DenominationCount("bill_50", 2), new DenominationCount("coin_10", 1)));
        reloaded.setCloseNotes("Faltaron C$10, probablemente una propina no registrada.");
        cashShiftRepository.saveAndFlush(reloaded);
        entityManager.clear();

        CashShift closed = cashShiftRepository.findById(saved.getId()).orElseThrow();
        assertThat(closed.getClosingBreakdown()).containsExactly(
                new DenominationCount("bill_50", 2), new DenominationCount("coin_10", 1));
        assertThat(closed.getCloseNotes()).isEqualTo("Faltaron C$10, probablemente una propina no registrada.");
    }
```

Add the matching imports if creating the file fresh: `com.vanter.ember.cashregister.model.DenominationCount`
alongside the existing `CashShift`/`CashShiftStatus` imports.

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=CashShiftRepositoryTest test`
Expected: FAIL to compile — `CashShift` has no `openingBreakdown`/`closingBreakdown`/`closeNotes`
yet.

- [ ] **Step 4: Add the columns to `CashShift`**

Add these imports to `CashShift.java` (alongside the existing `java.math.BigDecimal` etc. imports):

```java
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
```

Add these fields after `private int prolongCount = 0;` and before the `effectiveDeadline()` method:

```java
    // SqlTypes.JSON resolves to the dialect's JSON type on both PostgreSQL and H2 — same pattern
    // as PrintAgent.discoveredPrinters / Session.participants. Null until a count with a
    // denomination breakdown has actually been submitted (older shifts, or a client that omits
    // it, simply have no breakdown — not an error).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_breakdown")
    private List<DenominationCount> openingBreakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "closing_breakdown")
    private List<DenominationCount> closingBreakdown;

    @Column(name = "close_notes")
    private String closeNotes;
```

- [ ] **Step 5: Write the migration**

```sql
-- Denomination-count audit trail for the arqueo de caja: how many of each córdoba bill/coin were
-- actually counted at open and close, instead of just a typed-in total. JSON, not a relational
-- child table — matches CashShift's own "no separate report table" design (see its class javadoc).
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS opening_breakdown jsonb;
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS closing_breakdown jsonb;
ALTER TABLE cash_shifts ADD COLUMN IF NOT EXISTS close_notes text;
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./mvnw -Dtest=CashShiftRepositoryTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java \
        backend/src/main/resources/db/migration/V12__cash_shift_denomination_breakdown.sql \
        backend/src/test/java/com/vanter/ember/cashregister/repository/CashShiftRepositoryTest.java
git commit -m "feat(cashregister): add denomination breakdown + notes columns to CashShift"
```

---

## Task 3: Service validation + DTOs

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/dto/OpenShiftRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/dto/CloseShiftRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java` (one `OpenShiftRequest` call
  site, line ~267)

**Interfaces:**
- Consumes: `Denomination`, `DenominationKind`, `NicaraguaDenominations`, `DenominationCount` from
  Task 1; `CashShift.getOpeningBreakdown()` etc. from Task 2.
- Produces: `CashShiftService.openShift(UUID, String, BigDecimal, List<DenominationCount>)` and
  `CashShiftService.closeShift(Long, String, BigDecimal, List<DenominationCount>, String)` — Task 4's
  controller calls these new signatures. `CashShiftResponse` gains `openingBreakdown`,
  `closingBreakdown`, `closeNotes` as its final 3 fields — Task 4's controller test and every
  frontend task after this one read them.

- [ ] **Step 1: Write the failing tests**

Add to `CashShiftServiceTest.java` (new imports needed:
`com.vanter.ember.cashregister.model.DenominationCount`, `java.util.List`):

```java
    @Test
    void openShift_acceptsAMatchingBreakdownAndPersistsIt() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(cashShiftRepository.findMaxShiftNumber(TENANT_ID)).thenReturn(0);
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithPayload());
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<DenominationCount> breakdown = List.of(new DenominationCount("bill_100", 1));
        CashShift shift = cashShiftService.openShift(
                TENANT_ID, "user-1", new BigDecimal("100.00"), breakdown);

        assertThat(shift.getOpeningBreakdown()).isEqualTo(breakdown);
    }

    @Test
    void openShift_rejectsABreakdownThatDoesNotSumToTheTotal() {
        List<DenominationCount> breakdown = List.of(new DenominationCount("bill_100", 1));

        assertThatThrownBy(() -> cashShiftService.openShift(
                        TENANT_ID, "user-1", new BigDecimal("50.00"), breakdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sums to");
    }

    @Test
    void openShift_rejectsAnUnknownDenominationId() {
        List<DenominationCount> breakdown = List.of(new DenominationCount("bill_777", 1));

        assertThatThrownBy(() -> cashShiftService.openShift(
                        TENANT_ID, "user-1", new BigDecimal("777.00"), breakdown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown denomination");
    }

    @Test
    void openShift_allowsNoBreakdownAtAllForBackwardCompatibility() {
        when(cashShiftRepository.findByTenantIdAndStatus(TENANT_ID, CashShiftStatus.OPEN))
                .thenReturn(Optional.empty());
        when(cashShiftRepository.findMaxShiftNumber(TENANT_ID)).thenReturn(0);
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWithPayload());
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashShift shift = cashShiftService.openShift(TENANT_ID, "user-1", new BigDecimal("100.00"), null);

        assertThat(shift.getOpeningBreakdown()).isNull();
    }
```

Also add these two tests near the existing `closeShift` tests (search the file for a `closeShift`
test to find where they belong — follow whatever mocking pattern the existing `closeShift` tests
there already use for `sessionRepository`/`cashMovementRepository`/`paymentRepository` stubs):

```java
    @Test
    void closeShift_acceptsAMatchingBreakdownAndNotes() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(sessionRepository.countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN)).thenReturn(0L);
        when(cashMovementRepository.sumCashIn(1L)).thenReturn(BigDecimal.ZERO);
        when(cashMovementRepository.sumCashOut(1L)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumConfirmedPhysicalForShift(any(), any())).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.sumConfirmedDigitalInWindow(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(cashShiftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<DenominationCount> breakdown = List.of(new DenominationCount("bill_100", 1));
        CashShift closed = cashShiftService.closeShift(
                1L, "user-1", new BigDecimal("100.00"), breakdown, "todo cuadró");

        assertThat(closed.getClosingBreakdown()).isEqualTo(breakdown);
        assertThat(closed.getCloseNotes()).isEqualTo("todo cuadró");
    }

    @Test
    void closeShift_rejectsABreakdownThatDoesNotSumToTheCountedCash() {
        CashShift shift = openShift();
        when(cashShiftRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(shift));
        when(sessionRepository.countByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN)).thenReturn(0L);

        List<DenominationCount> breakdown = List.of(new DenominationCount("bill_100", 1));
        assertThatThrownBy(() -> cashShiftService.closeShift(
                        1L, "user-1", new BigDecimal("50.00"), breakdown, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sums to");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -Dtest=CashShiftServiceTest test`
Expected: FAIL to compile — `openShift`/`closeShift` don't accept a `breakdown` param yet.

- [ ] **Step 3: Update the DTOs**

```java
package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.DenominationCount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record OpenShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal openingFloat, List<DenominationCount> breakdown) {}
```

```java
package com.vanter.ember.cashregister.dto;

import com.vanter.ember.cashregister.model.DenominationCount;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record CloseShiftRequest(
        @NotNull @DecimalMin("0.00") BigDecimal countedCash,
        List<DenominationCount> breakdown,
        String notes) {}
```

In `CashShiftResponse.java`, add the import `import com.vanter.ember.cashregister.model.DenominationCount;`
and `import java.util.List;`, then add 3 fields at the very end of the record component list (after
`int prolongCount`):

```java
        int prolongCount,
        List<DenominationCount> openingBreakdown,
        List<DenominationCount> closingBreakdown,
        String closeNotes) {}
```

- [ ] **Step 4: Update `CashShiftService`**

Change `openShift`'s signature and body:

```java
    @Transactional
    public CashShift openShift(
            UUID tenantId, String openedByUserId, BigDecimal openingFloat, List<DenominationCount> breakdown) {
        if (cashShiftRepository.findByTenantIdAndStatus(tenantId, CashShiftStatus.OPEN).isPresent()) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }
        validateBreakdown(breakdown, openingFloat, "Opening float");

        int nextShiftNumber = cashShiftRepository.findMaxShiftNumber(tenantId) + 1;

        var businessHours = settingService.getSettings(tenantId).getPayload().getBusinessHours();
        LocalDateTime openedAt = LocalDateTime.now();
        LocalDateTime expiresAt = deadlineService.computeExpiresAt(openedAt, businessHours);

        CashShift shift;
        try {
            shift = cashShiftRepository.save(CashShift.builder()
                    .shiftNumber(nextShiftNumber)
                    .status(CashShiftStatus.OPEN)
                    .openingFloat(openingFloat)
                    .openingBreakdown(breakdown)
                    .openedBy(openedByUserId)
                    .openedAt(openedAt)
                    .expiresAt(expiresAt)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException("A cash shift is already open for this tenant");
        }

        eventPublisher.publishEvent(new CashShiftOpened(tenantId, shift.getId()));
        return shift;
    }
```

Change `closeShift`'s signature and body:

```java
    @Transactional
    public CashShift closeShift(
            Long shiftId, String closedByUserId, BigDecimal countedCash,
            List<DenominationCount> breakdown, String notes) {
        CashShift shift = cashShiftRepository.findByIdForUpdate(shiftId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash shift not found: " + shiftId));
        if (shift.getStatus() != CashShiftStatus.OPEN) {
            throw new IllegalStateException("Cash shift is not open: " + shiftId);
        }

        long activeTables = sessionRepository.countByTenantIdAndStatus(shift.getTenantId(), SessionStatus.OPEN);
        if (activeTables > 0) {
            throw new IllegalStateException(
                    "Cannot close cash shift: " + activeTables + " table(s) still have an open session");
        }
        validateBreakdown(breakdown, countedCash, "Counted cash");

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
        shift.setClosingBreakdown(breakdown);
        shift.setCloseNotes(notes);
        shift.setVariance(variance);
        shift.setTotalCashSales(cashSales);
        shift.setTotalDigitalSales(digitalSales);
        shift.setTotalCashIn(cashIn);
        shift.setTotalCashOut(cashOut);

        CashShift saved = cashShiftRepository.save(shift);
        eventPublisher.publishEvent(new CashShiftClosed(shift.getTenantId(), shiftId));
        return saved;
    }
```

Add this private helper anywhere below `closeShift` (e.g. right after it):

```java
    /**
     * A breakdown is optional (older clients, or the API used directly, may omit it) — but when
     * present, every denomination must be real and the counted total must match exactly. Never
     * trusts the caller's arithmetic even though today's only caller (the frontend grid) always
     * derives the total from the same rows.
     */
    private void validateBreakdown(List<DenominationCount> breakdown, BigDecimal total, String label) {
        if (breakdown == null) {
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (DenominationCount count : breakdown) {
            if (count.quantity() < 0) {
                throw new IllegalArgumentException(label + " breakdown has a negative quantity");
            }
            Denomination denomination = NicaraguaDenominations.byId(count.denominationId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            label + " breakdown references an unknown denomination: " + count.denominationId()));
            sum = sum.add(denomination.value().multiply(BigDecimal.valueOf(count.quantity())));
        }
        if (sum.compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    label + " breakdown sums to " + sum + " but the submitted total is " + total);
        }
    }
```

Add the two new imports at the top of `CashShiftService.java`:

```java
import com.vanter.ember.cashregister.model.Denomination;
import com.vanter.ember.cashregister.model.DenominationCount;
import com.vanter.ember.cashregister.model.NicaraguaDenominations;
```

Update `toResponse`'s final `new CashShiftResponse(...)` call to pass the 3 new fields:

```java
    private CashShiftResponse toResponse(CashShift shift, Map<String, String> names) {
        return new CashShiftResponse(
                shift.getId(), shift.getShiftNumber(), shift.getStatus().name(), shift.getOpeningFloat(),
                names.getOrDefault(shift.getOpenedBy(), shift.getOpenedBy()), shift.getOpenedAt(),
                shift.getClosedBy() == null ? null : names.getOrDefault(shift.getClosedBy(), shift.getClosedBy()),
                shift.getClosedAt(), shift.getExpectedCash(), shift.getCountedCash(), shift.getVariance(),
                shift.getTotalCashSales(), shift.getTotalDigitalSales(), shift.getTotalCashIn(),
                shift.getTotalCashOut(),
                shift.getExpiresAt(), shift.effectiveDeadline(),
                deadlineService.isOverdue(shift, LocalDateTime.now()),
                shift.businessDay(), shift.getProlongCount(),
                shift.getOpeningBreakdown(), shift.getClosingBreakdown(), shift.getCloseNotes());
    }
```

- [ ] **Step 5: Fix the other positional `CashShiftResponse`/`OpenShiftRequest` call sites**

In `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java` around line 267, change
`new OpenShiftRequest(new BigDecimal("100.00"))` to
`new OpenShiftRequest(new BigDecimal("100.00"), null)`.

(The `CashShiftControllerTest`/`CashShiftControllerProlongTest` positional-constructor call sites
are fixed in Task 4, which also owns those test files.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw -Dtest=CashShiftServiceTest test`
Expected: PASS, all tests including the 6 new ones.

Run: `cd backend && ./mvnw -Dtest=E2EOrderFlowTest test`
Expected: PASS (this only confirms the one call-site fix compiles and the existing E2E flow still
works — it is not expected to exercise the new breakdown logic itself).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/dto/OpenShiftRequest.java \
        backend/src/main/java/com/vanter/ember/cashregister/dto/CloseShiftRequest.java \
        backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java \
        backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java \
        backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java \
        backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java
git commit -m "feat(cashregister): validate and persist the denomination breakdown on open/close"
```

---

## Task 4: Controller wiring

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java`

**Interfaces:**
- Consumes: `OpenShiftRequest`, `CloseShiftRequest`, `CashShiftResponse`, `CashShiftService.openShift`/
  `closeShift` from Task 3.

- [ ] **Step 1: Write the failing test**

Add to `CashShiftControllerTest.java` (new import: `com.vanter.ember.cashregister.model.DenominationCount`,
`java.util.List`):

```java
    @Test
    @WithMockUser(username = "accountant@ember.local", roles = "ACCOUNTANT")
    void open_passesTheBreakdownThrough() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("accountant@ember.local"))
                .thenReturn(Optional.of(sampleUser("accountant@ember.local")));
        when(cashShiftService.openShift(any(), eq("user-1"), any(BigDecimal.class), anyList()))
                .thenReturn(sampleShift());
        when(cashShiftService.toResponse(any())).thenReturn(new CashShiftResponse(
                1L, 1, "OPEN", new BigDecimal("100.00"), "Alice", LocalDateTime.now(),
                null, null, null, null, null, null, null, null, null,
                null, null, false, null, 0, null, null, null));

        String body = """
                {"openingFloat": 100.00, "breakdown": [{"denominationId": "bill_100", "quantity": 1}]}
                """;
        mockMvc.perform(post("/cash-shifts/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        org.mockito.Mockito.verify(cashShiftService).openShift(
                any(), eq("user-1"), any(BigDecimal.class),
                eq(List.of(new DenominationCount("bill_100", 1))));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -Dtest=CashShiftControllerTest test`
Expected: FAIL to compile — `CashShiftResponse` needs 3 more constructor args everywhere it's built
in this file, and `cashShiftService.openShift`/`closeShift` mocks don't match the new signature.

- [ ] **Step 3: Update the controller**

```java
    @Operation(summary = "Open a new cash shift — Apertura de Caja (ACCOUNTANT)")
    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public CashShiftResponse open(@Valid @RequestBody OpenShiftRequest request, Authentication authentication) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "cashclose");
        CashShift shift = cashShiftService.openShift(
                tenantId, resolveUserId(authentication), request.openingFloat(), request.breakdown());
        return cashShiftService.toResponse(shift);
    }
```

```java
    @Operation(summary = "Close a shift with a blind cash count — Arqueo de Turno (ACCOUNTANT)")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public CashShiftResponse close(
            @PathVariable Long id,
            @Valid @RequestBody CloseShiftRequest request,
            Authentication authentication) {
        CashShift shift = cashShiftService.closeShift(
                id, resolveUserId(authentication), request.countedCash(), request.breakdown(), request.notes());
        return cashShiftService.toResponse(shift);
    }
```

- [ ] **Step 4: Fix every remaining positional `CashShiftResponse`/`OpenShiftRequest`/`CloseShiftRequest`
  call site in this file and in `CashShiftControllerProlongTest.java`**

In `CashShiftControllerTest.java`:
- Line ~87, ~105, ~116, ~169: `new OpenShiftRequest(new BigDecimal("100.00"))` →
  `new OpenShiftRequest(new BigDecimal("100.00"), null)`.
- Line ~232, ~244: `new CloseShiftRequest(new BigDecimal("260.00"))` →
  `new CloseShiftRequest(new BigDecimal("260.00"), null, null)`.
- Line ~82 (`open_returnsCreatedForAccountant`) and ~139
  (`current_returnsTheOpenShiftWhenOneExists`): append `, null, null, null` right before the final
  `)` of each `new CashShiftResponse(...)` call (they currently end `..., false, null, 0));` — leave
  everything through `0` unchanged and change the tail to `..., false, null, 0, null, null, null));`.
- Line ~225 (`close_returnsOkAndRevealsVarianceForAccountant`): same tail change — it currently ends
  `null, null, false, null, 0));`, change to `null, null, false, null, 0, null, null, null));`.
- Also update `when(cashShiftService.openShift(...))` mocks used across the file's existing tests
  (e.g. `open_returnsCreatedForAccountant`) from
  `cashShiftService.openShift(any(), eq("user-1"), any(BigDecimal.class))` to
  `cashShiftService.openShift(any(), eq("user-1"), any(BigDecimal.class), any())` — add the
  `anyList()`/`any()` matcher for the new 4th parameter everywhere `openShift`/`closeShift` is
  stubbed with `when(...)` in this file, matching the new 4-arg/5-arg signatures from Task 3.

In `CashShiftControllerProlongTest.java` around line 69: the `new CashShiftResponse(...)` call
currently ends `false, LocalDate.now(), 1));` — change to `false, LocalDate.now(), 1, null, null,
null));`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -Dtest=CashShiftControllerTest,CashShiftControllerProlongTest test`
Expected: PASS, all tests.

Run full suite to confirm nothing else broke: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS`, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java \
        backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java \
        backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java
git commit -m "feat(cashregister): wire the denomination breakdown through the controller"
```

---

## Task 5: Frontend denomination constants + type patch

**Files:**
- Create: `frontend/src/lib/denominations.ts`
- Test: `frontend/src/lib/denominations.test.ts`
- Modify: `frontend/src/lib/backend-types.ts`

**Interfaces:**
- Produces: `NICARAGUA_DENOMINATIONS` (`Denomination[]`), `sumBreakdown(breakdown: DenominationCount[]): number`,
  and the `Denomination`/`DenominationCount`/`DenominationKind` TS types — Task 6's `DenominationCounter`
  and Tasks 7-8's dialogs all import from this file.

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, test, expect } from 'vitest'
import { NICARAGUA_DENOMINATIONS, sumBreakdown } from './denominations'

describe('denominations', () => {
  test('has 14 entries, 7 bills and 7 coins', () => {
    expect(NICARAGUA_DENOMINATIONS).toHaveLength(14)
    expect(NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'BILL')).toHaveLength(7)
    expect(NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'COIN')).toHaveLength(7)
  })

  test('the C$10 bill and C$10 coin are distinct entries with the same value', () => {
    const bill = NICARAGUA_DENOMINATIONS.find((d) => d.id === 'bill_10')
    const coin = NICARAGUA_DENOMINATIONS.find((d) => d.id === 'coin_10')
    expect(bill?.value).toBe(10)
    expect(coin?.value).toBe(10)
    expect(bill?.kind).toBe('BILL')
    expect(coin?.kind).toBe('COIN')
  })

  test('sumBreakdown multiplies value by quantity across rows', () => {
    const total = sumBreakdown([
      { denominationId: 'bill_100', quantity: 2 },
      { denominationId: 'coin_5', quantity: 3 },
    ])
    expect(total).toBe(215)
  })

  test('sumBreakdown ignores an unknown denominationId rather than throwing', () => {
    const total = sumBreakdown([{ denominationId: 'nope', quantity: 5 }])
    expect(total).toBe(0)
  })

  test('sumBreakdown of an empty array is 0', () => {
    expect(sumBreakdown([])).toBe(0)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/lib/denominations.test.ts`
Expected: FAIL — `./denominations` module doesn't exist.

- [ ] **Step 3: Write the implementation**

```typescript
export type DenominationKind = 'BILL' | 'COIN'

export interface Denomination {
  id: string
  value: number
  kind: DenominationKind
}

export interface DenominationCount {
  denominationId: string
  quantity: number
}

/**
 * The 14 córdoba denominations in circulation per the Banco Central de Nicaragua
 * (bcn.gob.ni/billetes-actuales, bcn.gob.ni/monedas-actuales). Mirrors the backend's
 * NicaraguaDenominations.ALL exactly — kept in sync by hand, same as every other shared
 * enum/constant in this codebase (no shared package between frontend and backend).
 * bill_10 and coin_10 are deliberately both C$10 — the BCN has both a banknote and a coin at
 * that face value in circulation at once.
 */
export const NICARAGUA_DENOMINATIONS: Denomination[] = [
  { id: 'bill_1000', value: 1000, kind: 'BILL' },
  { id: 'bill_500', value: 500, kind: 'BILL' },
  { id: 'bill_200', value: 200, kind: 'BILL' },
  { id: 'bill_100', value: 100, kind: 'BILL' },
  { id: 'bill_50', value: 50, kind: 'BILL' },
  { id: 'bill_20', value: 20, kind: 'BILL' },
  { id: 'bill_10', value: 10, kind: 'BILL' },
  { id: 'coin_10', value: 10, kind: 'COIN' },
  { id: 'coin_5', value: 5, kind: 'COIN' },
  { id: 'coin_1', value: 1, kind: 'COIN' },
  { id: 'coin_050', value: 0.5, kind: 'COIN' },
  { id: 'coin_025', value: 0.25, kind: 'COIN' },
  { id: 'coin_010', value: 0.1, kind: 'COIN' },
  { id: 'coin_005', value: 0.05, kind: 'COIN' },
]

export const sumBreakdown = (breakdown: DenominationCount[]): number =>
  breakdown.reduce((total, entry) => {
    const denomination = NICARAGUA_DENOMINATIONS.find((d) => d.id === entry.denominationId)
    return denomination ? total + denomination.value * entry.quantity : total
  }, 0)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/lib/denominations.test.ts`
Expected: PASS, 5/5.

- [ ] **Step 5: Patch `backend-types.ts`**

Attempt a real regen first: `cd frontend && pnpm run openapi` (needs a locally running backend on
:8080 with this branch's code — start one with `cd backend && ./mvnw spring-boot:run` in another
terminal first). Diff the result: `git diff frontend/src/lib/backend-types.ts`.

- If the diff is limited to `CashShiftResponse`/`OpenShiftRequest`/`CloseShiftRequest`/a new
  `DenominationCount` schema (i.e., actually reflects this branch's changes), keep the regen as-is.
- If the diff has large unrelated drift (this codebase's established pattern per reports 421/483 —
  hundreds of lines touching unrelated endpoints because of a stale local backend), `git checkout
  frontend/src/lib/backend-types.ts` to discard it and hand-patch instead, exactly as follows.

Hand patch (apply regardless of which path above was taken, to guarantee the exact 3 shapes below):

In the `components["schemas"]` block, add a new entry (alphabetical position, near `CloseShiftRequest`):

```typescript
        DenominationCount: {
            denominationId?: string;
            /** Format: int32 */
            quantity?: number;
        };
```

Change `CloseShiftRequest`:

```typescript
        CloseShiftRequest: {
            countedCash: number;
            breakdown?: components["schemas"]["DenominationCount"][];
            notes?: string;
        };
```

Change `OpenShiftRequest`:

```typescript
        OpenShiftRequest: {
            openingFloat: number;
            breakdown?: components["schemas"]["DenominationCount"][];
        };
```

Change `CashShiftResponse` — add 3 fields right before its closing `};`:

```typescript
            /** Format: int32 */
            prolongCount?: number;
            openingBreakdown?: components["schemas"]["DenominationCount"][];
            closingBreakdown?: components["schemas"]["DenominationCount"][];
            closeNotes?: string;
        };
```

- [ ] **Step 6: Verify the frontend still typechecks**

Run: `cd frontend && pnpm run build`
Expected: clean build (this is the fastest way to catch a typo in the hand-patched types before any
other task depends on them).

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/denominations.ts frontend/src/lib/denominations.test.ts \
        frontend/src/lib/backend-types.ts
git commit -m "feat(cashregister): add the frontend denomination catalog and API types"
```

---

## Task 6: `DenominationCounter` component

**Files:**
- Create: `frontend/src/pages/accountant/cashRegister/components/DenominationCounter.tsx`
- Test: `frontend/src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx`

**Interfaces:**
- Consumes: `NICARAGUA_DENOMINATIONS`, `sumBreakdown`, `Denomination`, `DenominationCount` from
  Task 5; `formatCurrency` from `@/lib/format`.
- Produces: `<DenominationCounter onChange={(breakdown: DenominationCount[], total: number) => void} />`
  — Tasks 7 and 8 both render this instead of their old single amount input.

- [ ] **Step 1: Write the failing test**

```typescript
import { describe, test, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { DenominationCounter } from './DenominationCounter'

describe('DenominationCounter', () => {
  test('renders all 14 denomination rows', () => {
    render(<DenominationCounter onChange={vi.fn()} />)
    // 14 quantity inputs, one per denomination row.
    expect(screen.getAllByRole('spinbutton')).toHaveLength(14)
  })

  test('starts at a total of C$0.00', () => {
    render(<DenominationCounter onChange={vi.fn()} />)
    expect(screen.getByText('C$0.00')).toBeVisible()
  })

  test('entering a quantity updates the total and calls onChange with the breakdown', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('C$100.00'), { target: { value: '2' } })

    expect(screen.getByText('C$200.00')).toBeVisible()
    expect(onChange).toHaveBeenLastCalledWith([{ denominationId: 'bill_100', quantity: 2 }], 200)
  })

  test('a zero quantity is not included in the emitted breakdown', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('C$100.00'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('C$100.00'), { target: { value: '0' } })

    expect(onChange).toHaveBeenLastCalledWith([], 0)
  })

  test('the C$10 bill and C$10 coin are counted independently', () => {
    const onChange = vi.fn()
    render(<DenominationCounter onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('Billete C$10.00'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('Moneda C$10.00'), { target: { value: '1' } })

    expect(onChange).toHaveBeenLastCalledWith(
      [
        { denominationId: 'bill_10', quantity: 1 },
        { denominationId: 'coin_10', quantity: 1 },
      ],
      20,
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx`
Expected: FAIL — the component doesn't exist yet.

- [ ] **Step 3: Write the implementation**

```typescript
import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { formatCurrency } from '@/lib/format'
import { NICARAGUA_DENOMINATIONS, sumBreakdown, type Denomination, type DenominationCount } from '@/lib/denominations'
import { useTranslation } from '@/lib/i18n'

interface DenominationCounterProps {
  onChange: (breakdown: DenominationCount[], total: number) => void
}

const toBreakdown = (quantities: Record<string, number>): DenominationCount[] =>
  Object.entries(quantities)
    .filter(([, quantity]) => quantity > 0)
    .map(([denominationId, quantity]) => ({ denominationId, quantity }))

export const DenominationCounter = ({ onChange }: DenominationCounterProps) => {
  const { t } = useTranslation('waiter')
  const [quantities, setQuantities] = useState<Record<string, number>>({})

  const handleQuantityChange = (denominationId: string, rawValue: string) => {
    const quantity = Math.max(0, Math.floor(Number(rawValue) || 0))
    const next = { ...quantities, [denominationId]: quantity }
    setQuantities(next)
    const breakdown = toBreakdown(next)
    onChange(breakdown, sumBreakdown(breakdown))
  }

  const rowLabel = (denomination: Denomination) => {
    if (denomination.kind === 'BILL') {
      return t('billDenominationLabel', { amount: formatCurrency(denomination.value) })
    }
    return t('coinDenominationLabel', { amount: formatCurrency(denomination.value) })
  }

  const renderRow = (denomination: Denomination) => (
    <div key={denomination.id} className="flex items-center justify-between gap-3">
      <label htmlFor={`denom-${denomination.id}`} className="text-sm font-medium">
        {formatCurrency(denomination.value)}
      </label>
      <Input
        id={`denom-${denomination.id}`}
        aria-label={rowLabel(denomination)}
        type="number"
        min="0"
        step="1"
        className="w-20 rounded-xl"
        value={quantities[denomination.id] ?? ''}
        onChange={(e) => handleQuantityChange(denomination.id, e.target.value)}
      />
    </div>
  )

  const bills = NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'BILL')
  const coins = NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'COIN')
  const total = sumBreakdown(toBreakdown(quantities))

  return (
    <div className="flex flex-col gap-4">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t('billsLabel')}
        </p>
        <div className="grid grid-cols-2 gap-2">{bills.map(renderRow)}</div>
      </div>
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {t('coinsLabel')}
        </p>
        <div className="grid grid-cols-2 gap-2">{coins.map(renderRow)}</div>
      </div>
      <div className="flex items-center justify-between border-t pt-3">
        <span className="text-sm font-semibold">{t('totalCountedLabel')}</span>
        <span className="text-lg font-bold text-primary">{formatCurrency(total)}</span>
      </div>
    </div>
  )
}
```

**Note:** the test's `screen.getByLabelText('C$100.00')` (plain, no "Billete"/"Moneda" prefix) works
for every denomination except the two C$10 rows, because `rowLabel` only adds a
"Billete"/"Moneda" prefix — but every row's *visible* `<label>` text is always the plain
`formatCurrency(denomination.value)` (e.g. `"C$100.00"`), while `aria-label` carries the
disambiguated bill/coin text. RTL's `getByLabelText` matches against the *visible* `<label>` first;
for the two ambiguous C$10 rows the test instead queries by the `aria-label` text
(`'Billete C$10.00'`/`'Moneda C$10.00'`), which is what `getByLabelText` also matches on. Run the
test as written in Step 1 — if `getByLabelText('C$100.00')` fails because it now matches more than
one element (it shouldn't, since only the two C$10 rows are ambiguous and C$100 is not one of
them), that is a real bug to fix, not a test to loosen.

Add the i18n keys this component needs (`billsLabel`, `coinsLabel`, `totalCountedLabel`,
`billDenominationLabel`, `coinDenominationLabel`) in Task 10 — until then, `pnpm vitest` will render
the raw key names as text, which does not affect the test assertions above (they assert on
formatted currency values and DOM roles, not on the label copy).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx`
Expected: PASS, 5/5.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/accountant/cashRegister/components/DenominationCounter.tsx \
        frontend/src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx
git commit -m "feat(cashregister): add the DenominationCounter grid component"
```

---

## Task 7: `OpenShiftDialog` uses the counter

**Files:**
- Modify: `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx`
- Modify: `frontend/src/lib/api.ts` (`cashShiftService.open`)
- Create: `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx`

**Interfaces:**
- Consumes: `DenominationCounter` from Task 6; `DenominationCount` from Task 5.
- Produces: `cashShiftService.open(openingFloat: number, breakdown?: DenominationCount[])` — this
  signature is what Task 7's own dialog calls; nothing later depends on it.

- [ ] **Step 1: Write the failing test**

```typescript
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { OpenShiftDialog } from './OpenShiftDialog'
import { useUIStore } from '@/store/uiStore'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: { ...actual.cashShiftService, open: vi.fn().mockResolvedValue({ id: 1 }) },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

describe('OpenShiftDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'OPEN_SHIFT', modalPayload: undefined })
  })

  test('submits the counted breakdown and total, not a typed amount', async () => {
    wrap(<OpenShiftDialog />)

    fireEvent.change(screen.getByLabelText('C$100.00'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: 'Abrir caja' }))

    await waitFor(() =>
      expect(cashShiftService.open).toHaveBeenCalledWith(
        200,
        [{ denominationId: 'bill_100', quantity: 2 }],
      ),
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx`
Expected: FAIL — `OpenShiftDialog` still renders the old single input, no `C$100.00` label exists.

- [ ] **Step 3: Update `cashShiftService.open`**

In `frontend/src/lib/api.ts`, replace:

```typescript
  open: async (openingFloat: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>('/cash-shifts/open', { openingFloat })
    return data
  },
```

with:

```typescript
  open: async (
    openingFloat: number,
    breakdown?: DenominationCount[]
  ): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>('/cash-shifts/open', { openingFloat, breakdown })
    return data
  },
```

Add the import near this file's other `@/lib/*` imports: `import type { DenominationCount } from '@/lib/denominations'`.

- [ ] **Step 4: Rewrite `OpenShiftDialog.tsx`**

```typescript
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import toast from 'react-hot-toast'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService } from '@/lib/api'
import { useTranslation } from '@/lib/i18n'
import { extractPlanGateError } from '@/lib/planGate'
import { DenominationCounter } from './DenominationCounter'
import type { DenominationCount } from '@/lib/denominations'

export const OpenShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { t: tCommon } = useTranslation('common')
  const { activeModal, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const [breakdown, setBreakdown] = useState<DenominationCount[]>([])
  const [total, setTotal] = useState(0)

  const handleCounterChange = (nextBreakdown: DenominationCount[], nextTotal: number) => {
    setBreakdown(nextBreakdown)
    setTotal(nextTotal)
  }

  const mutation = useMutation({
    mutationFn: () => cashShiftService.open(total, breakdown),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      toast.success(t('shiftOpenedToast'))
      setBreakdown([])
      setTotal(0)
      closeModal()
    },
    onError: (error) => {
      const gate = extractPlanGateError(error)
      toast.error(
        gate
          ? tCommon('planGateUpgradeToast', { plan: gate.requiredPlan ?? '' })
          : t('shiftOpenErrorToast'),
      )
    },
  })

  return (
    <Dialog open={activeModal === 'OPEN_SHIFT'} onOpenChange={(isOpen) => !isOpen && closeModal()}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('openShiftTitle')}</DialogTitle>
        </DialogHeader>

        <DenominationCounter onChange={handleCounterChange} />

        <DialogFooter className="mt-5">
          <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
            {t('cancelButton')}
          </Button>
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            {mutation.isPending ? t('openingLabel') : t('openCajaButton')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx \
        frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx \
        frontend/src/lib/api.ts
git commit -m "feat(cashregister): open-shift dialog uses the denomination counter"
```

---

## Task 8: `CloseShiftDialog` uses the counter + notes

**Files:**
- Modify: `frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.tsx`
- Modify: `frontend/src/lib/api.ts` (`cashShiftService.close`)
- Create: `frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx`

**Interfaces:**
- Consumes: `DenominationCounter` from Task 6; `DenominationCount` from Task 5.
- Produces: `cashShiftService.close(id, countedCash, breakdown?, notes?)` — only this task's own
  dialog calls it.

- [ ] **Step 1: Write the failing test**

```typescript
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CloseShiftDialog } from './CloseShiftDialog'
import { useUIStore } from '@/store/uiStore'
import { cashShiftService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    cashShiftService: {
      ...actual.cashShiftService,
      close: vi.fn().mockResolvedValue({ expectedCash: 200, countedCash: 200, variance: 0 }),
    },
  }
})

const wrap = (ui: ReactNode, qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })) =>
  render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)

describe('CloseShiftDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'CLOSE_SHIFT', modalPayload: { shiftId: 7 } })
  })

  test('submits the counted breakdown, total, and notes', async () => {
    wrap(<CloseShiftDialog />)

    fireEvent.change(screen.getByLabelText('C$100.00'), { target: { value: '2' } })
    fireEvent.change(screen.getByPlaceholderText('Explica la diferencia, si la hay'), {
      target: { value: 'todo cuadró' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar conteo' }))

    await waitFor(() =>
      expect(cashShiftService.close).toHaveBeenCalledWith(
        7,
        200,
        [{ denominationId: 'bill_100', quantity: 2 }],
        'todo cuadró',
      ),
    )
  })

  test('notes are optional — an empty notes field submits undefined, not an empty string', async () => {
    wrap(<CloseShiftDialog />)

    fireEvent.change(screen.getByLabelText('C$50.00'), { target: { value: '1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar conteo' }))

    await waitFor(() =>
      expect(cashShiftService.close).toHaveBeenCalledWith(7, 50, [{ denominationId: 'bill_50', quantity: 1 }], undefined),
    )
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx`
Expected: FAIL — the dialog still renders the old single amount input and has no notes field.

- [ ] **Step 3: Update `cashShiftService.close`**

In `frontend/src/lib/api.ts`, replace:

```typescript
  close: async (id: number, countedCash: number): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>(`/cash-shifts/${id}/close`, { countedCash })
    return data
  },
```

with:

```typescript
  close: async (
    id: number,
    countedCash: number,
    breakdown?: DenominationCount[],
    notes?: string
  ): Promise<CashShiftResponse> => {
    const { data } = await api.post<CashShiftResponse>(`/cash-shifts/${id}/close`, {
      countedCash,
      breakdown,
      notes,
    })
    return data
  },
```

- [ ] **Step 4: Rewrite `CloseShiftDialog.tsx`**

```typescript
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import toast from 'react-hot-toast'
import axios from 'axios'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { useUIStore } from '@/store/uiStore'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { cashShiftService, type CashShiftResponse } from '@/lib/api'
import { formatCurrency } from '@/lib/format'
import { useTranslation } from '@/lib/i18n'
import { DenominationCounter } from './DenominationCounter'
import type { DenominationCount } from '@/lib/denominations'

// Matches CashShiftService.closeShift's "Cannot close cash shift: N table(s) still have an
// open session" detail (backend) so the toast can surface the open-table count without
// showing the raw English backend string to Spanish users.
const extractOpenTablesCount = (detail: unknown): number | null => {
  if (typeof detail !== 'string') return null
  const match = detail.match(/^Cannot close cash shift: (\d+) table/)
  return match ? Number(match[1]) : null
}

export const CloseShiftDialog = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const queryClient = useQueryClient()
  const shiftId = modalPayload?.shiftId as number | undefined
  const [breakdown, setBreakdown] = useState<DenominationCount[]>([])
  const [total, setTotal] = useState(0)
  const [notes, setNotes] = useState('')
  const [result, setResult] = useState<CashShiftResponse | null>(null)

  const handleCounterChange = (nextBreakdown: DenominationCount[], nextTotal: number) => {
    setBreakdown(nextBreakdown)
    setTotal(nextTotal)
  }

  const handleOpenChange = (isOpen: boolean) => {
    if (!isOpen) {
      setBreakdown([])
      setTotal(0)
      setNotes('')
      setResult(null)
      closeModal()
    }
  }

  const mutation = useMutation({
    mutationFn: () => cashShiftService.close(shiftId!, total, breakdown, notes.trim() || undefined),
    onSuccess: (closed) => {
      queryClient.invalidateQueries({ queryKey: ['cashShiftCurrent'] })
      setResult(closed)
    },
    onError: (error) => {
      const count = axios.isAxiosError(error)
        ? extractOpenTablesCount(error.response?.data?.detail)
        : null
      if (count !== null) {
        toast.error(t('shiftCloseTablesOpenToast', { count }))
        handleOpenChange(false)
      } else {
        toast.error(t('shiftCloseErrorToast'))
      }
    },
  })

  return (
    <Dialog open={activeModal === 'CLOSE_SHIFT'} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-lg rounded-3xl p-6">
        <DialogHeader className="mb-4">
          <DialogTitle className="text-2xl font-bold text-zinc-800">{t('closeShiftTitle')}</DialogTitle>
        </DialogHeader>

        {!result ? (
          <div className="flex flex-col gap-5">
            <DialogDescription>{t('closeShiftDescription')}</DialogDescription>
            <DenominationCounter onChange={handleCounterChange} />
            <div className="flex flex-col gap-2">
              <label htmlFor="close-shift-notes" className="text-sm font-medium">
                {t('closeNotesLabel')}
              </label>
              <Textarea
                id="close-shift-notes"
                className="rounded-xl"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
                placeholder={t('closeNotesPlaceholder')}
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={mutation.isPending}>
                {t('cancelButton')}
              </Button>
              <Button onClick={() => mutation.mutate()} disabled={mutation.isPending || !shiftId}>
                {mutation.isPending ? t('closingShiftLabel') : t('confirmCountButton')}
              </Button>
            </DialogFooter>
          </div>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <p className="text-xs text-muted-foreground">{t('expectedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.expectedCash ?? 0)}</p>
              </div>
              <div>
                <p className="text-xs text-muted-foreground">{t('countedLabel')}</p>
                <p className="text-lg font-bold">{formatCurrency(result.countedCash ?? 0)}</p>
              </div>
              <div className="col-span-2">
                <p className="text-xs text-muted-foreground">{t('differenceLabel')}</p>
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
              <Button onClick={() => handleOpenChange(false)}>{t('closeButton')}</Button>
            </DialogFooter>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx`
Expected: PASS, 2/2.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.tsx \
        frontend/src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx \
        frontend/src/lib/api.ts
git commit -m "feat(cashregister): close-shift dialog uses the denomination counter and notes"
```

---

## Task 9: Admin Corte Z shows the breakdown

**Files:**
- Modify: `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- Modify: `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx` (extend if it
  exists — check with `find frontend/src/pages/admin/cashRegister/components -iname
  "ShiftHistoryTable.test.tsx"` first; create fresh with the same `wrap`/`vi.mock` shape as Task 7/8
  if it doesn't)

**Interfaces:**
- Consumes: `CashShiftResponse.openingBreakdown`/`closingBreakdown`/`closeNotes` from Task 3/5;
  `NICARAGUA_DENOMINATIONS` from Task 5 (to look up a denomination's label from its id for display).

- [ ] **Step 1: Write the failing test**

```typescript
    test('expanding a closed shift shows its denomination breakdown and notes', async () => {
      vi.mocked(cashShiftService.history).mockResolvedValue({
        content: [
          {
            id: 3, shiftNumber: 3, status: 'CLOSED', openedByName: 'Ana', closedByName: 'Ana',
            expectedCash: 200, countedCash: 200, variance: 0,
          },
        ],
        totalPages: 1,
      } as never)
      vi.mocked(cashShiftService.detail).mockResolvedValue({
        shift: {
          id: 3, shiftNumber: 3, status: 'CLOSED',
          openingBreakdown: [{ denominationId: 'bill_100', quantity: 1 }],
          closingBreakdown: [{ denominationId: 'bill_100', quantity: 2 }],
          closeNotes: 'Todo cuadró',
        },
        movements: [], payments: [],
      } as never)

      wrap(<ShiftHistoryTable />)
      fireEvent.click(await screen.findByText('#3'))

      expect(await screen.findByText('Todo cuadró')).toBeVisible()
      expect(screen.getByText('C$100.00 × 1')).toBeVisible()
      expect(screen.getByText('C$100.00 × 2')).toBeVisible()
    })
```

(Follow whatever `wrap`/`vi.mock('@/lib/api', ...)` setup the rest of that test file already uses —
if the file doesn't exist yet, base it on Task 7's `OpenShiftDialog.test.tsx` `wrap` helper, mocking
`cashShiftService.history` and `cashShiftService.detail` instead of `open`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && pnpm vitest run src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx`
Expected: FAIL — no breakdown or notes rendered today.

- [ ] **Step 3: Add the breakdown/notes rendering**

Add this import to `ShiftHistoryTable.tsx`: `import { NICARAGUA_DENOMINATIONS, type DenominationCount } from '@/lib/denominations'`.

Add this small helper above the `ShiftHistoryTable` component:

```typescript
const denominationLabel = (count: DenominationCount): string => {
  const denomination = NICARAGUA_DENOMINATIONS.find((d) => d.id === count.denominationId)
  const value = denomination ? formatCurrency(denomination.value) : count.denominationId
  return `${value} × ${count.quantity}`
}
```

Inside the expanded `<TableCell colSpan={7} className="bg-muted/30">` block, right after the
`{!detail ? ... : ...}` payments block that's already there, add:

```typescript
                          {detail && ((detail.shift.openingBreakdown?.length ?? 0) > 0 ||
                            (detail.shift.closingBreakdown?.length ?? 0) > 0 ||
                            detail.shift.closeNotes) && (
                            <div className="mt-3 flex flex-col gap-2 border-t border-border/40 pt-3 text-sm">
                              {(detail.shift.openingBreakdown?.length ?? 0) > 0 && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('openingBreakdownLabel')}
                                  </p>
                                  <p>{detail.shift.openingBreakdown!.map(denominationLabel).join(', ')}</p>
                                </div>
                              )}
                              {(detail.shift.closingBreakdown?.length ?? 0) > 0 && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('closingBreakdownLabel')}
                                  </p>
                                  <p>{detail.shift.closingBreakdown!.map(denominationLabel).join(', ')}</p>
                                </div>
                              )}
                              {detail.shift.closeNotes && (
                                <div>
                                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                    {t('closeNotesLabel')}
                                  </p>
                                  <p>{detail.shift.closeNotes}</p>
                                </div>
                              )}
                            </div>
                          )}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && pnpm vitest run src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx \
        frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx
git commit -m "feat(cashregister): show the denomination breakdown and notes in Corte Z"
```

---

## Task 10: i18n keys (ES/EN)

**Files:**
- Modify: `frontend/src/locales/es/waiter.ts`
- Modify: `frontend/src/locales/en/waiter.ts`
- Modify: `frontend/src/locales/es/admin.ts`
- Modify: `frontend/src/locales/en/admin.ts`

**Interfaces:**
- Consumes: nothing new — this task only supplies the string keys Tasks 6, 8, and 9 already
  reference via `t(...)`.

- [ ] **Step 1: Add the `waiter` namespace keys**

In `frontend/src/locales/es/waiter.ts`, add near the existing `closeShiftDescription`/`countedCashLabel`
keys (replace `countedCashLabel` usage nowhere — it can stay, just unused now — do not delete it,
some other view might still reference it; check with `grep -rn countedCashLabel frontend/src` first
and only remove it if genuinely unused):

```typescript
  billsLabel: 'Billetes',
  coinsLabel: 'Monedas',
  totalCountedLabel: 'Total contado',
  billDenominationLabel: 'Billete {{amount}}',
  coinDenominationLabel: 'Moneda {{amount}}',
  closeNotesLabel: 'Observaciones',
  closeNotesPlaceholder: 'Explica la diferencia, si la hay',
```

In `frontend/src/locales/en/waiter.ts`, add the matching English keys in the same position:

```typescript
  billsLabel: 'Bills',
  coinsLabel: 'Coins',
  totalCountedLabel: 'Total counted',
  billDenominationLabel: 'Bill {{amount}}',
  coinDenominationLabel: 'Coin {{amount}}',
  closeNotesLabel: 'Notes',
  closeNotesPlaceholder: 'Explain the difference, if any',
```

- [ ] **Step 2: Add the `admin` namespace keys**

In `frontend/src/locales/es/admin.ts`, add near the other Corte Z / cash-register keys:

```typescript
  openingBreakdownLabel: 'Conteo de apertura',
  closingBreakdownLabel: 'Conteo de cierre',
  closeNotesLabel: 'Observaciones',
```

In `frontend/src/locales/en/admin.ts`:

```typescript
  openingBreakdownLabel: 'Opening count',
  closingBreakdownLabel: 'Closing count',
  closeNotesLabel: 'Notes',
```

- [ ] **Step 2: Check for the existing ES/EN parity check**

Run: `grep -rn "satisfies" frontend/src/locales/en/waiter.ts frontend/src/locales/en/admin.ts`

This codebase enforces ES/EN key parity via a TypeScript `satisfies` clause against the ES file's
shape — if the grep shows one, `pnpm run build`'s typecheck step (Step 4 below) will fail loudly on
any missing key, which is the real verification for this task.

- [ ] **Step 3: Run every test file this plan touched, plus a full build**

Run: `cd frontend && pnpm vitest run src/lib/denominations.test.ts src/pages/accountant/cashRegister/components/DenominationCounter.test.tsx src/pages/accountant/cashRegister/components/OpenShiftDialog.test.tsx src/pages/accountant/cashRegister/components/CloseShiftDialog.test.tsx src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx`
Expected: PASS, every test from Tasks 5-9.

Run: `cd frontend && pnpm run build`
Expected: clean build — this is also what proves ES/EN parity held.

Run: `cd frontend && pnpm run lint`
Expected: 0 new errors (pre-existing warning count, if any, is unrelated to this plan).

- [ ] **Step 4: Full backend suite one more time, end to end**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS`, 0 failures — final confirmation nothing across the whole plan regressed.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts \
        frontend/src/locales/es/admin.ts frontend/src/locales/en/admin.ts
git commit -m "feat(cashregister): add i18n copy for the denomination count feature"
```

---

## Self-Review

**Spec coverage:** §3.1-3.3 (model types, columns, DTO/service validation) → Tasks 1-3. §3.4
(controller wiring, folded into Task 3's service since the controller itself needed no new routes,
just updated calls) → Task 4. §4.1 (`DenominationCounter`) → Task 6. §4.2 (`OpenShiftDialog`) → Task
7. §4.3 (`CloseShiftDialog` + notes) → Task 8. §4.4 (admin Corte Z detail) → Task 9, scoped to
`ShiftHistoryTable.tsx` specifically (resolving the spec's "and/or `DailyZReportPanel.tsx`"
ambiguity — that component is the daily rollup across shifts, not per-shift detail, so it's out of
scope). §6 testing → one test task per implementation task throughout. §7.2's resolution (notes
always optional, no patch endpoint) → reflected in Task 8, no separate endpoint task exists.

**Placeholder scan:** no TBD/TODO; every step has real code or (Task 2 Step 1, Task 9 Step 1) an
explicit fallback instruction with a concrete `find`/`grep` command to resolve the one genuine
unknown (whether a test file already exists) rather than a vague "handle it."

**Type consistency:** `DenominationCount(String denominationId, int quantity)` (Task 1) is the same
shape used in every later Java task's mocks/assertions (Tasks 2-4) and mirrors
`{ denominationId: string; quantity: number }` on the frontend (Task 5) used identically through
Tasks 6-9. `CashShiftService.openShift`'s 4th parameter and `closeShift`'s 4th/5th parameters
(Task 3) match exactly what Task 4's controller passes. `cashShiftService.open`/`close`'s new
frontend signatures (Tasks 7-8) match what each dialog actually calls.
