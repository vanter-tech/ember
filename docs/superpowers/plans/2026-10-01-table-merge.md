# EMB-TABLE-MERGE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A waiter can attach a free table to an already-open session (button or long-press drag-and-drop) so every physically-joined table shows as occupied, the kitchen sees "M3+M4 - Unidas", and reports show the merge without changing any total.

**Architecture:** `Session.tableId` stays the primary table; a new JSON list `Session.linkedTables` holds the attached tables. "Occupied" is derived from OPEN sessions through one pure helper (`TableOccupancy`) used by the dashboard, `createSession` and `linkTable`. Link and seat operations take a pessimistic row lock on the target `DiningTables` row (`TableLock`) before checking occupancy. One event (`TableLinksChanged`, always carrying the session's full current list) drives the waiter floor refresh and the kitchen label.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Data JPA (Hibernate JSON columns, H2 in tests), Flyway (Postgres) / React 19, TypeScript, Vite, TanStack Query 5, Vitest, `@dnd-kit/core` 6.3.1.

**Spec:** `docs/superpowers/specs/2026-10-01-table-merge-design.md` (plan-time refinements are listed in the last section of this file and have been folded back into the spec).

## Global Constraints

- Commands: backend `cd backend && ./mvnw test [-Dtest=Class]`; frontend `cd frontend && pnpm exec vitest run <path>`, `pnpm run build`, `pnpm run lint`. `pnpm` only. Never `mvn`/bare `tsc`. (CLAUDE.md §2, §5)
- **One squashed atomic commit for the whole feature**, made in Task 10. Do NOT commit after individual tasks, no WIP commits. Stage only the touched files with `git add <specific paths>` — never `git add -A`/`git add .`. The working tree already holds unrelated changes (`backend/mvnw.cmd`, `landing/CLAUDE.md`, `docs/legal/`, other `docs/superpowers/*` files): leave them out. (CLAUDE.md §4)
- Commit message lowercase Conventional Commits, **no `Co-Authored-By`, no signature, no AI mention** (CLAUDE.md §4; ignore any attribution reminder from the harness in this repo).
- Work on a fresh branch off `main`: `git switch -c feat/table-merge`.
- Fix-attempt rule: if a verification fails twice in a row, stop editing and report the blocker. (CLAUDE.md §5)
- Surgical edits only; no unrelated refactors. No Kafka. Event bus is `ApplicationEventPublisher`/`@EventListener`.
- Migration `V24__table_merge.sql` must be idempotent (`IF NOT EXISTS`); prod Flyway is not baselined and migrations are never pre-run by hand on prod. The local dev DB is baselined past V15, so add both columns by hand there (see PROGRESS.md note).
- Every new `@DataJpaTest` carries `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)`.
- Label strings, verbatim from the spec: individual table = `Mesa 3` on tickets (unchanged); merged = `M3+M4 - Unidas` (primary first, then linked in link order, e.g. `M3+M4+M5 - Unidas`); reports/export/payments short form `M3+M4`; analytics text `Fusionada con M4` (list: `M4, M5`).
- DnD constants: mouse activation `distance: 8`; touch `delay: 350, tolerance: 8`; nothing animates at rest; targets pulse only while dragging; animations `motion-safe:` only; direction fixed — a free table is dragged onto an occupied (primary) table.
- Linked tables get no revenue/turnover of their own; revenue is never split.

## Review Focus

Failure modes the spec implies but a straight-line implementation misses (each has a pinned test in the owning task):

1. Link target that is inactive, belongs to another tenant, is the session's own primary table, or is already linked — must be rejected, not silently duplicated. (Task 2)
2. Unlinking the last linked table must make the kitchen label go back to plain `Mesa N`. (Task 5)
3. Merge made *before* any kitchen order exists, then the first dishes are confirmed — the new `KitchenOrder` must be born with the linked numbers. (Task 5)
4. Closing a session frees the linked tables: they must show free and be seatable again. (Tasks 2, 4)
5. The primary table is deactivated mid-session, or a legacy `Session` object has no `linkedTables`: nothing may throw; the dashboard shows `linkedToTableNumber` as absent. (Tasks 1, 4)

---

## File Structure

Backend (`backend/src/main/java/com/vanter/ember/…`):

| File | Responsibility |
|---|---|
| `session/model/LinkedTable.java` (new) | `{tableId, tableNumber, linkedAt}` JSON element |
| `session/model/TableLabels.java` (new) | Pure label formatting (`joined`, `ticketLine`) |
| `session/service/TableOccupancy.java` (new) | Pure `byTable(openSessions)` → table id → owning session |
| `session/service/TableLock.java` (new) | Runs an action in a transaction holding the target table's row lock |
| `session/event/TableLinksChanged.java` (new) | One event for link + unlink, full list inside |
| `session/dto/LinkTableRequest.java`, `session/dto/LinkedTableSummary.java` (new) | Request body; dashboard row element |
| `session/model/Session.java`, `SessionActivity.java` | `linkedTables` field; two activity types |
| `session/repository/SessionRepository.java`, `settings/repository/DiningTableRepository.java` | `findByTenantIdAndStatus`; `findByIdForUpdate` |
| `session/service/SessionService.java` | `createSession` via lock; `linkTable`; `unlinkTable`; pass linked numbers to kitchen event |
| `session/controller/SessionController.java` | Two endpoints |
| `session/service/DashboardService.java`, `session/dto/TableStatusResponse.java` | Occupancy incl. linked tables |
| `session/listener/WaiterWebSocketListener.java` | Broadcast `TableLinksChanged` |
| `session/event/KitchenItemsConfirmed.java`, `kitchen/model/KitchenOrder.java`, `kitchen/service/KitchenService.java`, `kitchen/listener/KitchenWebSocketListener.java` | Kitchen label data |
| `printing/service/KitchenTicketPrintService.java`, `printing/listener/PrintingEventListener.java`, `printing/service/ReceiptLayout.java`, `printing/service/ReceiptRenderer.java` | Ticket/receipt label |
| `export/service/ExportService.java`, `billing/dto/PaymentResponse.java`, `billing/service/PaymentService.java`, `analytics/dto/TablePerformance.java`, `analytics/service/AnalyticsService.java` | Reports |
| `resources/db/migration/V24__table_merge.sql` (new) | Both new columns |

Frontend (`frontend/src/…`): `lib/backend-types.ts`, `lib/api.ts`, `store/uiStore.ts`, `locales/{es,en}/{waiter,kitchen,admin}.ts`, `pages/kitchen/lib/mergedTableLabel.ts` (new), `pages/kitchen/components/{QueueCard,FocusedCard}.tsx`, `pages/admin/analytics/components/TableAnalytics.tsx`, `pages/admin/cashRegister/components/ShiftHistoryTable.tsx`, `pages/waiter/Tables.tsx`, `pages/waiter/components/{TableCard,LinkTableModal}.tsx` (new), `pages/waiter/lib/linkDrop.ts` (new).

---

### Task 1: Domain model, pure helpers, repositories, migration

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/model/LinkedTable.java`
- Create: `backend/src/main/java/com/vanter/ember/session/model/TableLabels.java`
- Create: `backend/src/main/java/com/vanter/ember/session/service/TableOccupancy.java`
- Create: `backend/src/main/resources/db/migration/V24__table_merge.sql`
- Modify: `backend/src/main/java/com/vanter/ember/session/model/Session.java` (after the `activityLog` field, ~line 84)
- Modify: `backend/src/main/java/com/vanter/ember/session/model/SessionActivity.java:15-22`
- Modify: `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/settings/repository/DiningTableRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java` (after `items`, ~line 56)
- Test: `backend/src/test/java/com/vanter/ember/session/model/TableLabelsTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/session/service/TableOccupancyTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java` (append a test)

**Interfaces:**
- Produces: `LinkedTable` (Lombok `@Data @Builder`, fields `UUID tableId`, `int tableNumber`, `LocalDateTime linkedAt`); `Session.getLinkedTables(): List<LinkedTable>` (never null in practice), `Session.linkedTableNumbers(): List<Integer>` (null-safe, link order); `TableLabels.joined(int, List<Integer>): String`, `TableLabels.ticketLine(int, List<Integer>): String`; `TableOccupancy.byTable(Collection<Session>): Map<UUID, Session>`; `SessionRepository.findByTenantIdAndStatus(UUID, SessionStatus): List<Session>`; `DiningTableRepository.findByIdForUpdate(UUID): Optional<DiningTables>`; `KitchenOrder.getLinkedTableNumbers()/setLinkedTableNumbers(List<Integer>)`; `SessionActivity.Type.TABLE_LINKED/TABLE_UNLINKED`.

- [ ] **Step 1: Write the failing tests**

`TableLabelsTest.java`:

```java
package com.vanter.ember.session.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TableLabelsTest {

    @Test
    void joined_aloneIsJustThePrimary() {
        assertThat(TableLabels.joined(3, List.of())).isEqualTo("M3");
    }

    @Test
    void joined_listsPrimaryThenLinkedInOrder() {
        assertThat(TableLabels.joined(3, List.of(4, 5))).isEqualTo("M3+M4+M5");
    }

    @Test
    void joined_toleratesNull() {
        assertThat(TableLabels.joined(3, null)).isEqualTo("M3");
    }

    @Test
    void ticketLine_individualTableKeepsTheLegacyWording() {
        assertThat(TableLabels.ticketLine(5, List.of())).isEqualTo("Mesa 5");
        assertThat(TableLabels.ticketLine(5, null)).isEqualTo("Mesa 5");
    }

    @Test
    void ticketLine_mergedTablesReadMxPlusMyUnidas() {
        assertThat(TableLabels.ticketLine(3, List.of(4))).isEqualTo("M3+M4 - Unidas");
        assertThat(TableLabels.ticketLine(3, List.of(4, 5))).isEqualTo("M3+M4+M5 - Unidas");
    }
}
```

`TableOccupancyTest.java`:

```java
package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TableOccupancyTest {

    private static final UUID M3 = UUID.randomUUID();
    private static final UUID M4 = UUID.randomUUID();
    private static final UUID M7 = UUID.randomUUID();

    private static Session session(String id, UUID primary, UUID... linked) {
        List<LinkedTable> links = new ArrayList<>();
        int n = 10;
        for (UUID l : linked) {
            links.add(LinkedTable.builder().tableId(l).tableNumber(n++).linkedAt(LocalDateTime.now()).build());
        }
        return Session.builder().id(id).tableId(primary).status(SessionStatus.OPEN).linkedTables(links).build();
    }

    @Test
    void byTable_mapsPrimaryAndLinkedTablesToTheirSession() {
        Session family = session("s-1", M3, M4);
        Session other = session("s-2", M7);

        Map<UUID, Session> map = TableOccupancy.byTable(List.of(family, other));

        assertThat(map.get(M3)).isSameAs(family);
        assertThat(map.get(M4)).isSameAs(family);
        assertThat(map.get(M7)).isSameAs(other);
        assertThat(map).hasSize(3);
    }

    @Test
    void byTable_primaryWinsIfLegacyDataClaimsTheSameTableTwice() {
        Session a = session("s-1", M3);
        Session b = session("s-2", M7, M3);

        assertThat(TableOccupancy.byTable(List.of(b, a)).get(M3)).isSameAs(a);
    }

    @Test
    void byTable_toleratesASessionWhoseLinkedTablesIsNull() {
        Session legacy = Session.builder().id("s-1").tableId(M3).status(SessionStatus.OPEN).build();
        legacy.setLinkedTables(null);

        assertThat(TableOccupancy.byTable(List.of(legacy))).containsOnlyKeys(M3);
        assertThat(legacy.linkedTableNumbers()).isEmpty();
    }

    @Test
    void byTable_emptyInputIsEmpty() {
        assertThat(TableOccupancy.byTable(List.of())).isEmpty();
    }
}
```

Append to `SessionRepositoryTest.java` (before the last closing brace; add `import com.vanter.ember.session.model.LinkedTable;`):

```java
    @Test
    void save_persistsLinkedTablesAsJsonAndFindsOpenSessionsByTenantAndStatus() {
        LinkedTable linked = LinkedTable.builder()
                .tableId(TABLE_2_ID).tableNumber(4).linkedAt(LocalDateTime.of(2026, 10, 1, 20, 0)).build();
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("w@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4).createdAt(LocalDateTime.now())
                .linkedTables(new java.util.ArrayList<>(List.of(linked))).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(UUID.randomUUID()).waiterId("w@test.com")
                .status(SessionStatus.CLOSED).maxParticipants(4).createdAt(LocalDateTime.now()).build());

        List<Session> open = sessionRepository.findByTenantIdAndStatus(TENANT_ID, SessionStatus.OPEN);

        assertThat(open).hasSize(1);
        assertThat(open.get(0).getLinkedTables()).singleElement().satisfies(l -> {
            assertThat(l.getTableId()).isEqualTo(TABLE_2_ID);
            assertThat(l.getTableNumber()).isEqualTo(4);
            assertThat(l.getLinkedAt()).isEqualTo(LocalDateTime.of(2026, 10, 1, 20, 0));
        });
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=TableLabelsTest,TableOccupancyTest,SessionRepositoryTest`
Expected: compilation FAIL (`TableLabels`, `TableOccupancy`, `LinkedTable`, `findByTenantIdAndStatus` do not exist).

- [ ] **Step 3: Implement**

`LinkedTable.java`:

```java
package com.vanter.ember.session.model;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A table physically joined to a session's primary table. {@code tableNumber} is denormalised so
 * tickets and reports never need a lookup and survive a table being renumbered or deactivated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedTable {
    private UUID tableId;
    private int tableNumber;
    private LocalDateTime linkedAt;
}
```

`TableLabels.java`:

```java
package com.vanter.ember.session.model;

import java.util.List;

/** Display strings for a (possibly merged) table. Pure; shared by kitchen, receipt and reports. */
public final class TableLabels {

    private TableLabels() {
    }

    /** {@code M3} alone, {@code M3+M4+M5} when merged. */
    public static String joined(int primary, List<Integer> linked) {
        StringBuilder sb = new StringBuilder("M").append(primary);
        if (linked != null) {
            for (Integer number : linked) {
                sb.append("+M").append(number);
            }
        }
        return sb.toString();
    }

    /** Ticket/receipt line: {@code Mesa 3} for an individual table, {@code M3+M4 - Unidas} when merged. */
    public static String ticketLine(int primary, List<Integer> linked) {
        if (linked == null || linked.isEmpty()) {
            return "Mesa " + primary;
        }
        return joined(primary, linked) + " - Unidas";
    }
}
```

`TableOccupancy.java`:

```java
package com.vanter.ember.session.service;

import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Session;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single definition of "occupied": a table is occupied by an OPEN session when it is that
 * session's primary table or one of its linked tables. Callers pass OPEN sessions only.
 */
public final class TableOccupancy {

    private TableOccupancy() {
    }

    public static Map<UUID, Session> byTable(Collection<Session> openSessions) {
        Map<UUID, Session> byTable = new HashMap<>();
        for (Session session : openSessions) {
            byTable.put(session.getTableId(), session);
        }
        for (Session session : openSessions) {
            List<LinkedTable> linked = session.getLinkedTables();
            if (linked == null) {
                continue;
            }
            for (LinkedTable table : linked) {
                byTable.putIfAbsent(table.getTableId(), session);
            }
        }
        return byTable;
    }
}
```

`Session.java` — add imports are already present for `List`/`ArrayList`; add after the `activityLog` field:

```java
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_tables", nullable = false)
    @Builder.Default
    private List<LinkedTable> linkedTables = new ArrayList<>();
```

and, after `ensureId()`:

```java
    /** Table numbers of the linked tables in link order; empty (never null) for an unmerged session. */
    public List<Integer> linkedTableNumbers() {
        if (linkedTables == null) {
            return List.of();
        }
        return linkedTables.stream().map(LinkedTable::getTableNumber).toList();
    }
```

`SessionActivity.Type`: add after `CLOSED_BY_ADMIN`:

```java
        CLOSED_BY_ADMIN,
        /** A free table was attached to the session; {@code note} = the table label, e.g. {@code M4}. */
        TABLE_LINKED,
        /** A linked table was detached; {@code note} = the table label. */
        TABLE_UNLINKED
```
(add the comma after `CLOSED_BY_ADMIN` and keep its Javadoc.)

`SessionRepository` — add:

```java
    List<Session> findByTenantIdAndStatus(UUID tenantId, SessionStatus status);
```

`DiningTableRepository` — add imports `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`, `java.util.Optional`, and:

```java
    /** Row lock used to serialise "who gets this table" decisions (seating vs linking). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DiningTables t where t.id = :id")
    Optional<DiningTables> findByIdForUpdate(@Param("id") UUID id);
```

`KitchenOrder.java` — after the `items` field add:

```java
    /** Numbers of the tables merged into this order's primary table; empty for an individual table. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "linked_table_numbers", nullable = false)
    @Builder.Default
    private List<Integer> linkedTableNumbers = new ArrayList<>();
```

`V24__table_merge.sql`:

```sql
-- Merge physical tables into one open session. A session keeps its primary table_id; the tables
-- attached later live in a JSON list, and the kitchen order mirrors their numbers for its label.
-- Idempotent (prod Flyway is not baselined, see V9/V11). Prod runs ddl-auto=validate.
ALTER TABLE sessions ADD COLUMN IF NOT EXISTS linked_tables jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE kitchen_orders ADD COLUMN IF NOT EXISTS linked_table_numbers jsonb NOT NULL DEFAULT '[]'::jsonb;
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=TableLabelsTest,TableOccupancyTest,SessionRepositoryTest,SessionEntityTest,SessionRepositoryTenantIsolationTest`
Expected: PASS.

- [ ] **Step 5: Local dev DB (manual, not committed)** — per the PROGRESS.md "Prod Flyway is NOT baselined" note, add the two columns to the local dev Postgres by hand with the same two `ALTER TABLE` statements via `docker exec … psql`. Skip if you only run H2 tests.

---

### Task 2: `TableLock`, `createSession`, `linkTable`, `unlinkTable`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/service/TableLock.java`
- Create: `backend/src/main/java/com/vanter/ember/session/event/TableLinksChanged.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` (fields ~line 63-71; `createSession` lines 136-167; new methods after `transferTable`, ~line 617)
- Test: `backend/src/test/java/com/vanter/ember/session/service/TableLockTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java` (migrate createSession tests; add link/unlink tests)
- Test: `backend/src/test/java/com/vanter/ember/session/service/TableLinkConcurrencyIntegrationTest.java` (new)

**Interfaces:**
- Consumes (Task 1): `LinkedTable`, `TableOccupancy.byTable`, `Session.linkedTableNumbers()`, `SessionRepository.findByTenantIdAndStatus`, `DiningTableRepository.findByIdForUpdate`, `SessionActivity.Type.TABLE_LINKED/TABLE_UNLINKED`.
- Produces: `TableLock.withTableLock(UUID tableId, Function<DiningTables, T> action): T` (runs `action` inside a transaction holding `FOR UPDATE` on that table row; `ResourceNotFoundException` if the table is not visible to the tenant; any exception from `action` rolls back and propagates); `TableLinksChanged` record `(String type, UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers)` with factories `linked(...)` (`type = "TABLES_LINKED"`) and `unlinked(...)` (`type = "TABLE_UNLINKED"`); `SessionService.linkTable(String sessionId, String callerEmail, UUID targetTableId): Session`; `SessionService.unlinkTable(String sessionId, String callerEmail, UUID tableId): Session`.

- [ ] **Step 1: Write the failing tests**

`TableLockTest.java`:

```java
package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class TableLockTest {

    @Mock DiningTableRepository diningTableRepository;
    @Mock PlatformTransactionManager transactionManager;
    TableLock tableLock;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        tableLock = new TableLock(diningTableRepository, transactionManager);
    }

    @Test
    void withTableLock_passesTheLockedRowToTheActionInsideATransactionAndCommits() {
        UUID id = UUID.randomUUID();
        DiningTables table = DiningTables.builder().id(id).tableNumber(4).build();
        when(diningTableRepository.findByIdForUpdate(id)).thenReturn(Optional.of(table));

        Integer number = tableLock.withTableLock(id, DiningTables::getTableNumber);

        assertThat(number).isEqualTo(4);
        verify(transactionManager).commit(any());
    }

    @Test
    void withTableLock_unknownTableIsNotFound() {
        UUID id = UUID.randomUUID();
        when(diningTableRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tableLock.withTableLock(id, t -> "x"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transactionManager).rollback(any());
    }

    @Test
    void withTableLock_aFailingActionRollsBackAndPropagates() {
        UUID id = UUID.randomUUID();
        when(diningTableRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(DiningTables.builder().id(id).tableNumber(4).build()));

        assertThatThrownBy(() -> tableLock.withTableLock(id, t -> {
            throw new IllegalStateException("occupied");
        })).isInstanceOf(IllegalStateException.class).hasMessage("occupied");
        verify(transactionManager).rollback(any());
    }
}
```

`SessionServiceTest.java` changes.

(a) Add fields/imports: `@Mock TableLock tableLock;` next to the other mocks; imports `com.vanter.ember.session.model.LinkedTable`, `com.vanter.ember.session.event.TableLinksChanged`, `java.util.function.Function`, `static org.mockito.ArgumentMatchers.anyString`? (not needed), `static org.mockito.Mockito.never` (already present).

(b) Add this helper below `diningTable()`:

```java
    /** Makes the mocked {@link TableLock} run its action synchronously with the given table. */
    @SuppressWarnings("unchecked")
    private void lockReturns(DiningTables table) {
        when(tableLock.withTableLock(eq(table.getId()), any())).thenAnswer(
                inv -> ((Function<DiningTables, Object>) inv.getArgument(1)).apply(table));
    }

    private void noOpenSessions() {
        when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN)).thenReturn(List.of());
    }
```

(c) Migrate the existing `createSession_*` tests (lines ~95-96, 114-116, 135-136, 146-147, 165-169, 180-182, 192-194, 206-208, 216-218). In each, replace the two stubs

```java
when(diningTableRepository.findById(TABLE_ID)).thenReturn(Optional.of(diningTable()));
when(sessionRepository.findByTenantIdAndTableIdAndStatus(RESTAURANT_ID, TABLE_ID, SessionStatus.OPEN)).thenReturn(List.of());
```

with

```java
lockReturns(diningTable());
noOpenSessions();
```

and in `createSession_throwsWhenTableOccupied` replace the stubs with

```java
lockReturns(diningTable());
Session existingOpenSession = Session.builder()
        .id("sess-0").tableId(TABLE_ID).status(SessionStatus.OPEN).build();
when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN))
        .thenReturn(List.of(existingOpenSession));
```

(d) Add the new tests (a `private DiningTables otherTable(UUID id, int n)` helper = `DiningTables.builder().id(id).tableNumber(n).isActive(true).build()`; `openSession()` = `Session.builder().id("sess-1").tenantId(RESTAURANT_ID).tableId(TABLE_ID).waiterId("waiter@test.com").status(SessionStatus.OPEN).build()`; use the class's existing tenant-binding setup for `RESTAURANT_ID`):

```java
    @Test
    void createSession_rejectsATableThatIsLinkedToAnotherOpenSession() {
        UUID m4 = UUID.randomUUID();
        lockReturns(otherTable(m4, 4));
        Session family = openSession();
        family.getLinkedTables().add(LinkedTable.builder().tableId(m4).tableNumber(4)
                .linkedAt(LocalDateTime.now()).build());
        when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN)).thenReturn(List.of(family));

        assertThatThrownBy(() -> sessionService.createSession(m4, "other@test.com", 2, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("occupied");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void linkTable_attachesAFreeTable_logsIt_andPublishesTheFullList() {
        UUID m4 = UUID.randomUUID();
        lockReturns(otherTable(m4, 4));
        Session session = openSession();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN)).thenReturn(List.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session saved = sessionService.linkTable("sess-1", "waiter@test.com", m4);

        assertThat(saved.getLinkedTables()).singleElement().satisfies(l -> {
            assertThat(l.getTableId()).isEqualTo(m4);
            assertThat(l.getTableNumber()).isEqualTo(4);
            assertThat(l.getLinkedAt()).isNotNull();
        });
        assertThat(saved.getActivityLog()).extracting(SessionActivity::getType)
                .containsExactly(SessionActivity.Type.TABLE_LINKED);
        ArgumentCaptor<TableLinksChanged> captor = ArgumentCaptor.forClass(TableLinksChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("TABLES_LINKED");
        assertThat(captor.getValue().linkedTableNumbers()).containsExactly(4);
        assertThat(captor.getValue().tableId()).isEqualTo(TABLE_ID);
    }

    @Test
    void linkTable_rejectsATableOccupiedByAnotherSession() {
        UUID m4 = UUID.randomUUID();
        lockReturns(otherTable(m4, 4));
        Session session = openSession();
        Session other = Session.builder().id("sess-9").tableId(m4).status(SessionStatus.OPEN).build();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN))
                .thenReturn(List.of(session, other));

        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "waiter@test.com", m4))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("occupied");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void linkTable_rejectsTheSessionsOwnPrimaryTableAndAnAlreadyLinkedOne() {
        UUID m4 = UUID.randomUUID();
        Session session = openSession();
        session.getLinkedTables().add(LinkedTable.builder().tableId(m4).tableNumber(4).linkedAt(LocalDateTime.now()).build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.findByTenantIdAndStatus(RESTAURANT_ID, SessionStatus.OPEN)).thenReturn(List.of(session));

        lockReturns(diningTable());
        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "waiter@test.com", TABLE_ID))
                .isInstanceOf(IllegalStateException.class);
        lockReturns(otherTable(m4, 4));
        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "waiter@test.com", m4))
                .isInstanceOf(IllegalStateException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void linkTable_rejectsAnInactiveTable() {
        UUID m4 = UUID.randomUUID();
        lockReturns(DiningTables.builder().id(m4).tableNumber(4).isActive(false).build());
        Session session = openSession();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "waiter@test.com", m4))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not active");
    }

    @Test
    void linkTable_onlyTheAssignedWaiterOnAnOpenSession() {
        UUID m4 = UUID.randomUUID();
        lockReturns(otherTable(m4, 4));
        Session session = openSession();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "intruder@test.com", m4))
                .isInstanceOf(AccessDeniedException.class);

        session.setStatus(SessionStatus.CLOSED);
        assertThatThrownBy(() -> sessionService.linkTable("sess-1", "waiter@test.com", m4))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unlinkTable_detachesIt_logsIt_andPublishesTheRemainingList() {
        UUID m4 = UUID.randomUUID();
        UUID m5 = UUID.randomUUID();
        Session session = openSession();
        session.getLinkedTables().add(LinkedTable.builder().tableId(m4).tableNumber(4).linkedAt(LocalDateTime.now()).build());
        session.getLinkedTables().add(LinkedTable.builder().tableId(m5).tableNumber(5).linkedAt(LocalDateTime.now()).build());
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session saved = sessionService.unlinkTable("sess-1", "waiter@test.com", m4);

        assertThat(saved.getLinkedTables()).extracting(LinkedTable::getTableId).containsExactly(m5);
        assertThat(saved.getActivityLog()).extracting(SessionActivity::getType)
                .containsExactly(SessionActivity.Type.TABLE_UNLINKED);
        ArgumentCaptor<TableLinksChanged> captor = ArgumentCaptor.forClass(TableLinksChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("TABLE_UNLINKED");
        assertThat(captor.getValue().linkedTableNumbers()).containsExactly(5);
    }

    @Test
    void unlinkTable_aTableThatIsNotLinkedIsNotFound() {
        Session session = openSession();
        when(sessionRepository.findByIdAndTenantId("sess-1", RESTAURANT_ID)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.unlinkTable("sess-1", "waiter@test.com", UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

`TableLinkConcurrencyIntegrationTest.java` (modelled on `GuestJoinFlowIntegrationTest`; real Spring context + H2, real threads):

```java
package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "ember.ratelimit.enabled=false")
class TableLinkConcurrencyIntegrationTest {

    @Autowired SessionService sessionService;
    @Autowired SessionRepository sessionRepository;
    @Autowired DiningTableRepository diningTableRepository;
    @Autowired RestaurantRepository restaurantRepository;

    private UUID tenantId;
    private UUID m1;
    private UUID m2;
    private UUID m3;
    private String s1;
    private String s2;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        diningTableRepository.deleteAll();
        restaurantRepository.deleteAll();
        Restaurant restaurant = restaurantRepository.save(Restaurant.builder()
                .name("Merge Test").slug("merge-test-" + UUID.randomUUID()).build());
        tenantId = restaurant.getId();
        TenantContextHolder.setTenantId(tenantId);
        m1 = table(1);
        m2 = table(2);
        m3 = table(3);
        s1 = sessionService.createSession(m1, "w1@test.com", 4, null).getId();
        s2 = sessionService.createSession(m2, "w2@test.com", 4, null).getId();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private UUID table(int number) {
        return diningTableRepository.save(DiningTables.builder()
                .restaurantId(tenantId).tableNumber(number).isActive(true).build()).getId();
    }

    private boolean succeeded(Callable<Object> action, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(tenantId);
        try {
            start.await();
            action.call();
            return true;
        } catch (IllegalStateException occupied) {
            return false;
        } finally {
            TenantContextHolder.clear();
        }
    }

    private long runConcurrently(Callable<Object> a, Callable<Object> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Boolean> fa = pool.submit(() -> succeeded(a, start));
        Future<Boolean> fb = pool.submit(() -> succeeded(b, start));
        start.countDown();
        long wins = List.of(fa.get(), fb.get()).stream().filter(w -> w).count();
        pool.shutdown();
        return wins;
    }

    @Test
    void twoWaitersLinkingTheSameFreeTableToDifferentSessions_exactlyOneWins() throws Exception {
        long wins = runConcurrently(
                () -> sessionService.linkTable(s1, "w1@test.com", m3),
                () -> sessionService.linkTable(s2, "w2@test.com", m3));

        assertThat(wins).isEqualTo(1);
        long sessionsHoldingM3 = sessionRepository.findByTenantIdAndStatus(tenantId, SessionStatus.OPEN).stream()
                .filter(s -> s.getLinkedTables().stream().anyMatch(l -> l.getTableId().equals(m3))).count();
        assertThat(sessionsHoldingM3).isEqualTo(1);
    }

    @Test
    void linkingATableWhileAnotherWaiterSeatsAPartyThere_exactlyOneWins() throws Exception {
        long wins = runConcurrently(
                () -> sessionService.linkTable(s1, "w1@test.com", m3),
                () -> sessionService.createSession(m3, "w2@test.com", 2, null));

        assertThat(wins).isEqualTo(1);
    }

    @Test
    void afterTheSessionCloses_theLinkedTableIsFreeAgain() {
        sessionService.linkTable(s1, "w1@test.com", m3);
        Session family = sessionRepository.findById(s1).orElseThrow();
        family.setStatus(SessionStatus.CLOSED);
        sessionRepository.save(family);

        Session reseated = sessionService.createSession(m3, "w2@test.com", 2, null);

        assertThat(reseated.getTableId()).isEqualTo(m3);
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=TableLockTest,SessionServiceTest,TableLinkConcurrencyIntegrationTest`
Expected: compilation FAIL (`TableLock`, `TableLinksChanged`, `linkTable`, `unlinkTable` missing).

- [ ] **Step 3: Implement**

`TableLock.java`:

```java
package com.vanter.ember.session.service;

import com.vanter.ember.config.ResourceNotFoundException;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Serialises "who gets this table" decisions. The row lock on the target table is taken at the start
 * of a short programmatic transaction, before the occupancy check, so two waiters racing for the
 * same free table (a seat vs a link, or two links onto different sessions) cannot both pass the
 * check. Programmatic rather than {@code @Transactional} so the caller publishes its events after
 * the commit, never inside the lock.
 */
@Component
public class TableLock {

    private final DiningTableRepository diningTableRepository;
    private final TransactionTemplate transactionTemplate;

    public TableLock(DiningTableRepository diningTableRepository, PlatformTransactionManager transactionManager) {
        this.diningTableRepository = diningTableRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public <T> T withTableLock(UUID tableId, Function<DiningTables, T> action) {
        return transactionTemplate.execute(status -> {
            DiningTables table = diningTableRepository.findByIdForUpdate(tableId)
                    .orElseThrow(() -> new ResourceNotFoundException("Table not found: " + tableId));
            return action.apply(table);
        });
    }
}
```

`TableLinksChanged.java`:

```java
package com.vanter.ember.session.event;

import java.util.List;
import java.util.UUID;

/**
 * A table was attached to or detached from a session. Always carries the session's FULL current
 * list of linked table numbers, so listeners overwrite their copy instead of applying a delta.
 */
public record TableLinksChanged(
        String type, UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {

    public static TableLinksChanged linked(
            UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {
        return new TableLinksChanged("TABLES_LINKED", tenantId, sessionId, tableId, List.copyOf(linkedTableNumbers));
    }

    public static TableLinksChanged unlinked(
            UUID tenantId, String sessionId, UUID tableId, List<Integer> linkedTableNumbers) {
        return new TableLinksChanged("TABLE_UNLINKED", tenantId, sessionId, tableId, List.copyOf(linkedTableNumbers));
    }
}
```

`SessionService.java`:
- imports: `com.vanter.ember.session.model.LinkedTable` (the `session.event.*` wildcard already covers `TableLinksChanged`).
- add field `private final TableLock tableLock;` next to `diningTableRepository` (Lombok `@RequiredArgsConstructor` injects it).
- replace `createSession` (lines 136-167) with:

```java
    public Session createSession(UUID tableId, String waiterId, int maxParticipants, List<String> seatNames) {
        UUID tenantId = TenantContextHolder.requireTenantId();

        record Opened(Session session, DiningTables table) {}
        Opened opened = tableLock.withTableLock(tableId, table -> {
            requireTableFree(tenantId, table);
            List<Participant> seats = buildSeats(maxParticipants, seatNames);
            Session saved = sessionRepository.save(Session.builder()
                    .tenantId(tenantId)
                    .tableId(tableId)
                    .waiterId(waiterId)
                    .status(SessionStatus.OPEN)
                    .maxParticipants(maxParticipants)
                    .participants(new ArrayList<>(seats))
                    .createdAt(LocalDateTime.now())
                    .joinCode(generateJoinCode())
                    .build());
            return new Opened(saved, table);
        });

        eventPublisher.publishEvent(new SessionOpened(
                tenantId, opened.session().getId(), tableId, opened.table().getTableNumber()));
        return opened.session();
    }

    /** Must run while holding the table's row lock; "occupied" includes tables linked to an open session. */
    private void requireTableFree(UUID tenantId, DiningTables table) {
        Session occupant = TableOccupancy
                .byTable(sessionRepository.findByTenantIdAndStatus(tenantId, SessionStatus.OPEN))
                .get(table.getId());
        if (occupant != null) {
            throw new IllegalStateException("Table " + table.getTableNumber() + " is already occupied");
        }
    }
```

- add after `transferTable` (before `resolveSelectedModifiers`):

```java
    /**
     * Attaches a FREE table to an OPEN session (the session's table stays primary). Locks the target
     * table row first so a concurrent seat/link of the same table cannot also pass the free check.
     */
    public Session linkTable(String sessionId, String callerEmail, UUID targetTableId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Session saved = tableLock.withTableLock(targetTableId, target -> {
            Session session = findById(sessionId);
            requireAssignedWaiter(session, callerEmail);
            requireOpen(session);
            if (!Boolean.TRUE.equals(target.getIsActive())) {
                throw new IllegalStateException("Table " + target.getTableNumber() + " is not active");
            }
            requireTableFree(tenantId, target);

            LocalDateTime now = LocalDateTime.now();
            session.getLinkedTables().add(LinkedTable.builder()
                    .tableId(target.getId())
                    .tableNumber(target.getTableNumber())
                    .linkedAt(now)
                    .build());
            session.getActivityLog().add(SessionActivity.builder()
                    .type(SessionActivity.Type.TABLE_LINKED)
                    .note("M" + target.getTableNumber())
                    .timestamp(now)
                    .build());
            return sessionRepository.save(session);
        });
        eventPublisher.publishEvent(TableLinksChanged.linked(
                saved.getTenantId(), saved.getId(), saved.getTableId(), saved.linkedTableNumbers()));
        return saved;
    }

    /** Detaches a linked table; it becomes free immediately. No row lock needed: freeing cannot double-book. */
    public Session unlinkTable(String sessionId, String callerEmail, UUID tableId) {
        Session session = findById(sessionId);
        requireAssignedWaiter(session, callerEmail);
        requireOpen(session);

        LinkedTable removed = session.getLinkedTables().stream()
                .filter(l -> l.getTableId().equals(tableId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Table is not linked to this session: " + tableId));
        session.getLinkedTables().remove(removed);
        session.getActivityLog().add(SessionActivity.builder()
                .type(SessionActivity.Type.TABLE_UNLINKED)
                .note("M" + removed.getTableNumber())
                .timestamp(LocalDateTime.now())
                .build());

        Session saved = sessionRepository.save(session);
        eventPublisher.publishEvent(TableLinksChanged.unlinked(
                saved.getTenantId(), saved.getId(), saved.getTableId(), saved.linkedTableNumbers()));
        return saved;
    }
```

- [ ] **Step 4: Run to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=TableLockTest,SessionServiceTest,TableLinkConcurrencyIntegrationTest`
Expected: PASS. If `TableLinkConcurrencyIntegrationTest` fails because the context cannot start for a reason unrelated to this change, compare with `GuestJoinFlowIntegrationTest` (same annotations) before changing anything; if H2 reports a lock timeout, the two-thread tests are flaky only if the action is not short — keep the action as written (no events inside the lock).

---

### Task 3: REST endpoints

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/LinkTableRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java` (after `transfer`, ~line 234)
- Test: `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java` (append)

**Interfaces:**
- Consumes (Task 2): `SessionService.linkTable(String, String, UUID)`, `unlinkTable(String, String, UUID)`.
- Produces: `POST /sessions/{id}/linked-tables` body `{"tableId":"<uuid>"}` and `DELETE /sessions/{id}/linked-tables/{tableId}`, both `hasRole('WAITER')`, both return the session detail (`SessionDetailResponseDto`) like `transfer`.

- [ ] **Step 1: Write the failing tests** — append to `SessionControllerTest` (imports already cover `post`, `delete`, `status`, `verify`, `MediaType`, `WithMockUser`, `UUID`):

```java
    // --- POST/DELETE /sessions/{id}/linked-tables ---

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void linkTable_callsTheServiceWithTheCallerAndTarget() throws Exception {
        UUID target = UUID.randomUUID();

        mockMvc.perform(post("/sessions/sess-1/linked-tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + target + "\"}"))
                .andExpect(status().isOk());

        verify(sessionService).linkTable("sess-1", "waiter@test.com", target);
    }

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void linkTable_badRequestWhenTableIdMissing() throws Exception {
        mockMvc.perform(post("/sessions/sess-1/linked-tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "cust@test.com", roles = "CUSTOMER")
    void linkTable_forbiddenForCustomer() throws Exception {
        mockMvc.perform(post("/sessions/sess-1/linked-tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "waiter@test.com", roles = "WAITER")
    void unlinkTable_callsTheService() throws Exception {
        UUID target = UUID.randomUUID();

        mockMvc.perform(delete("/sessions/sess-1/linked-tables/" + target))
                .andExpect(status().isOk());

        verify(sessionService).unlinkTable("sess-1", "waiter@test.com", target);
    }

    @Test
    @WithMockUser(username = "cust@test.com", roles = "CUSTOMER")
    void unlinkTable_forbiddenForCustomer() throws Exception {
        mockMvc.perform(delete("/sessions/sess-1/linked-tables/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 2: Run** — `cd backend && ./mvnw test -Dtest=SessionControllerTest` → FAIL (404 / compile).

- [ ] **Step 3: Implement**

`LinkTableRequest.java`:

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkTableRequest(@NotNull UUID tableId) {}
```

`SessionController.java` (import `java.util.UUID` and `LinkTableRequest` if missing):

```java
    @Operation(summary = "Attach a free table to this open session (WAITER)")
    @PostMapping("/{id}/linked-tables")
    @PreAuthorize("hasRole('WAITER')")
    public SessionDetailResponseDto linkTable(@PathVariable String id,
                                              @Valid @RequestBody LinkTableRequest request,
                                              Authentication authentication) {
        sessionService.linkTable(id, authentication.getName(), request.tableId());
        return sessionService.getSessionDetails(id);
    }

    @Operation(summary = "Detach a linked table from this session (WAITER)")
    @DeleteMapping("/{id}/linked-tables/{tableId}")
    @PreAuthorize("hasRole('WAITER')")
    public SessionDetailResponseDto unlinkTable(@PathVariable String id,
                                                @PathVariable UUID tableId,
                                                Authentication authentication) {
        sessionService.unlinkTable(id, authentication.getName(), tableId);
        return sessionService.getSessionDetails(id);
    }
```

- [ ] **Step 4: Run** — `cd backend && ./mvnw test -Dtest=SessionControllerTest` → PASS.

---

### Task 4: Dashboard occupancy and waiter broadcast

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/LinkedTableSummary.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/dto/TableStatusResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/DashboardService.java:25-64`
- Modify: `backend/src/main/java/com/vanter/ember/session/listener/WaiterWebSocketListener.java`
- Test: `backend/src/test/java/com/vanter/ember/session/service/DashboardServiceTest.java` (new)
- Test: `backend/src/test/java/com/vanter/ember/session/listener/WaiterWebSocketListenerTest.java` (append)

**Interfaces:**
- Consumes (Tasks 1-2): `TableOccupancy.byTable`, `SessionRepository.findByTenantIdAndStatus`, `TableLinksChanged`.
- Produces: `LinkedTableSummary(UUID tableId, int tableNumber)`; `TableStatusResponse` gains `List<LinkedTableSummary> linkedTables` (on the primary, empty list when none), `UUID linkedToTableId` and `Integer linkedToTableNumber` (on a table attached to someone else's session; both absent otherwise). A linked table is returned `isOccupied = true` with the OWNING session in `currentSession`.

- [ ] **Step 1: Write the failing tests**

`DashboardServiceTest.java`:

```java
package com.vanter.ember.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vanter.ember.session.dto.TableStatusResponse;
import com.vanter.ember.session.model.LinkedTable;
import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import com.vanter.ember.session.repository.SessionRepository;
import com.vanter.ember.settings.model.DiningTables;
import com.vanter.ember.settings.repository.DiningTableRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID M3 = UUID.randomUUID();
    private static final UUID M4 = UUID.randomUUID();
    private static final UUID M5 = UUID.randomUUID();

    @Mock DiningTableRepository diningTableRepository;
    @Mock SessionRepository sessionRepository;
    @InjectMocks DashboardService dashboardService;

    private static DiningTables table(UUID id, int number) {
        return DiningTables.builder().id(id).restaurantId(TENANT).tableNumber(number).isActive(true).build();
    }

    private static Session family(UUID primary, UUID... linked) {
        List<LinkedTable> links = new ArrayList<>();
        int n = 4;
        for (UUID l : linked) {
            links.add(LinkedTable.builder().tableId(l).tableNumber(n++).linkedAt(LocalDateTime.now()).build());
        }
        return Session.builder().id("s-1").tenantId(TENANT).tableId(primary).waiterId("w@test.com")
                .status(SessionStatus.OPEN).createdAt(LocalDateTime.now())
                .participants(new ArrayList<>(List.of(Participant.builder().name("Ana").build())))
                .linkedTables(links).build();
    }

    private TableStatusResponse row(List<TableStatusResponse> rows, UUID id) {
        return rows.stream().filter(r -> r.tableId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void getLiveStatus_aLinkedTableIsOccupied_pointsAtThePrimary_andSharesTheSession() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3), table(M4, 4), table(M5, 5)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3, M4)));

        List<TableStatusResponse> rows = dashboardService.getLiveStatus(TENANT);

        TableStatusResponse primary = row(rows, M3);
        assertThat(primary.isOccupied()).isTrue();
        assertThat(primary.linkedToTableId()).isNull();
        assertThat(primary.linkedTables()).singleElement().satisfies(l -> {
            assertThat(l.tableId()).isEqualTo(M4);
            assertThat(l.tableNumber()).isEqualTo(4);
        });

        TableStatusResponse linked = row(rows, M4);
        assertThat(linked.isOccupied()).isTrue();
        assertThat(linked.linkedToTableId()).isEqualTo(M3);
        assertThat(linked.linkedToTableNumber()).isEqualTo(3);
        assertThat(linked.currentSession().sessionId()).isEqualTo("s-1");

        TableStatusResponse free = row(rows, M5);
        assertThat(free.isOccupied()).isFalse();
        assertThat(free.currentSession()).isNull();
        assertThat(free.linkedTables()).isEmpty();
    }

    @Test
    void getLiveStatus_anUnmergedSessionHasNoLinkedTables() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3)));

        TableStatusResponse primary = dashboardService.getLiveStatus(TENANT).get(0);

        assertThat(primary.isOccupied()).isTrue();
        assertThat(primary.linkedTables()).isEmpty();
    }

    @Test
    void getLiveStatus_whenThePrimaryWasDeactivatedMidSession_theLinkedTableStillShowsOccupiedWithoutANumber() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M4, 4)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN))
                .thenReturn(List.of(family(M3, M4)));

        TableStatusResponse linked = dashboardService.getLiveStatus(TENANT).get(0);

        assertThat(linked.isOccupied()).isTrue();
        assertThat(linked.linkedToTableId()).isEqualTo(M3);
        assertThat(linked.linkedToTableNumber()).isNull();
    }

    @Test
    void getLiveStatus_aClosedSessionNoLongerOccupiesItsLinkedTables() {
        when(diningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(TENANT))
                .thenReturn(List.of(table(M3, 3), table(M4, 4)));
        when(sessionRepository.findByTenantIdAndStatus(TENANT, SessionStatus.OPEN)).thenReturn(List.of());

        assertThat(dashboardService.getLiveStatus(TENANT)).allSatisfy(r -> assertThat(r.isOccupied()).isFalse());
    }
}
```

`WaiterWebSocketListenerTest` — append (mirror an existing test there for the mock names `messagingTemplate`, `listener`):

```java
    @Test
    void onTableLinksChanged_broadcastsToTheWaiterTopic() {
        TableLinksChanged event = TableLinksChanged.linked(TENANT_ID, "sess-1", UUID.randomUUID(), List.of(4));

        listener.onTableLinksChanged(event);

        verify(messagingTemplate).convertAndSend("/topic/waiter/" + TENANT_ID, event);
    }
```
(add imports `com.vanter.ember.session.event.TableLinksChanged`, `java.util.List`, `java.util.UUID` if missing; if the constant is not named `TENANT_ID` in that file, use the file's own tenant constant.)

- [ ] **Step 2: Run** — `cd backend && ./mvnw test -Dtest=DashboardServiceTest,WaiterWebSocketListenerTest` → FAIL (compile).

- [ ] **Step 3: Implement**

`LinkedTableSummary.java`:

```java
package com.vanter.ember.session.dto;

import java.util.UUID;

public record LinkedTableSummary(UUID tableId, int tableNumber) {}
```

`TableStatusResponse.java` (keep the file's existing imports; add `java.util.List`):

```java
@Builder
public record TableStatusResponse(
        UUID tableId,
        int tableNumber,
        boolean isOccupied,
        ActiveSessionSummary currentSession,
        /** Tables attached to this table's session; empty when it is not a merged primary. */
        List<LinkedTableSummary> linkedTables,
        /** Set only on a table attached to ANOTHER table's session: that primary table. */
        UUID linkedToTableId,
        Integer linkedToTableNumber
) {
    public TableStatusResponse {
        if (linkedTables == null) {
            linkedTables = List.of();
        }
    }
}
```

`DashboardService.getLiveStatus` — replace the body from `var tablesId = …` to the end with:

```java
        Map<UUID, DiningTables> activeById = tables.stream()
                .collect(Collectors.toMap(DiningTables::getId, t -> t));
        Map<UUID, Session> occupancy = TableOccupancy.byTable(
                sessionRepository.findByTenantIdAndStatus(restaurantId, SessionStatus.OPEN));

        return tables.stream().map(table -> {
            Session session = occupancy.get(table.getId());
            if (session == null) {
                return TableStatusResponse.builder()
                        .tableId(table.getId())
                        .tableNumber(table.getTableNumber())
                        .isOccupied(false)
                        .build();
            }
            TableStatusResponse.TableStatusResponseBuilder row = TableStatusResponse.builder()
                    .tableId(table.getId())
                    .tableNumber(table.getTableNumber())
                    .isOccupied(true)
                    .currentSession(new ActiveSessionSummary(
                            session.getId(),
                            session.getWaiterId(),
                            session.getParticipants().size(),
                            session.getCreatedAt()));
            if (session.getTableId().equals(table.getId())) {
                row.linkedTables(session.getLinkedTables() == null ? List.of() : session.getLinkedTables().stream()
                        .map(l -> new LinkedTableSummary(l.getTableId(), l.getTableNumber()))
                        .toList());
            } else {
                DiningTables primary = activeById.get(session.getTableId());
                row.linkedToTableId(session.getTableId())
                        .linkedToTableNumber(primary == null ? null : primary.getTableNumber());
            }
            return row.build();
        }).toList();
```
and fix imports (`java.util.Map`, `com.vanter.ember.session.dto.LinkedTableSummary`, `com.vanter.ember.session.model.Session` already imported). `var tables = …` line stays.

`WaiterWebSocketListener.java` — add `import com.vanter.ember.session.event.TableLinksChanged;` and:

```java
    @EventListener
    public void onTableLinksChanged(TableLinksChanged event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }
```

- [ ] **Step 4: Run** — `cd backend && ./mvnw test -Dtest=DashboardServiceTest,WaiterWebSocketListenerTest,SessionControllerTest` → PASS. (Any other test that builds `TableStatusResponse` through its builder keeps working; the compact constructor defaults `linkedTables`.)

---

### Task 5: Kitchen label (KDS + tickets)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/event/KitchenItemsConfirmed.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` (the two `new KitchenItemsConfirmed(` sites, ~lines 580 and 799)
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java` (`handleOrderItemAdded` + new listener)
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/listener/KitchenWebSocketListener.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/service/KitchenTicketPrintService.java:59`
- Modify: `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java:93`
- Test: `backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java`, `kitchen/listener/KitchenWebSocketListenerTest.java`, `printing/service/KitchenTicketPrintServiceTest.java`, `printing/listener/PrintingEventListenerTest.java` (append)

**Interfaces:**
- Consumes: `KitchenOrder.linkedTableNumbers`, `TableLinksChanged`, `TableLabels.ticketLine`, `Session.linkedTableNumbers()`.
- Produces: `KitchenItemsConfirmed(UUID tenantId, String sessionId, int tableNumber, List<OrderItem> confirmedItems, List<Integer> linkedTableNumbers)` plus the existing 4-arg constructor (defaults to `List.of()`); `KitchenService.handleTableLinksChanged(TableLinksChanged)`; `KitchenWebSocketListener.onTableLinksChanged(TableLinksChanged)`. `KitchenDisplayEntry` is unchanged: its `orders` already carry `linkedTableNumbers`.

- [ ] **Step 1: Write the failing tests**

`KitchenServiceTest` (add imports `com.vanter.ember.session.event.TableLinksChanged`, `static org.mockito.Mockito.never`, `static org.assertj…` already present; the class already has `TENANT_ID`, `confirmedItem`, `kitchenOrderRepository`, `kitchenService`):

```java
    @Test
    void handleOrderItemAdded_newOrderIsBornWithTheMergedTablesOfTheSession() {
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.empty());

        kitchenService.handleOrderItemAdded(new KitchenItemsConfirmed(
                TENANT_ID, "sess-1", 5, List.of(confirmedItem("i1", "Tacos", "Alice")), List.of(6, 7)));

        ArgumentCaptor<KitchenOrder> saved = ArgumentCaptor.forClass(KitchenOrder.class);
        verify(kitchenOrderRepository).save(saved.capture());
        assertThat(saved.getValue().getLinkedTableNumbers()).containsExactly(6, 7);
    }

    @Test
    void handleOrderItemAdded_anExistingOrderTakesTheCurrentLinkedListFromTheEvent() {
        KitchenOrder existing = KitchenOrder.builder().id("ko-1").tenantId(TENANT_ID).sessionId("sess-1")
                .tableNumber(5).items(new ArrayList<>()).build();
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.of(existing));

        kitchenService.handleOrderItemAdded(new KitchenItemsConfirmed(
                TENANT_ID, "sess-1", 5, List.of(confirmedItem("i1", "Tacos", "Alice")), List.of(6)));

        assertThat(existing.getLinkedTableNumbers()).containsExactly(6);
    }

    @Test
    void handleTableLinksChanged_overwritesTheLinkedNumbersOfTheSessionsOrder() {
        KitchenOrder order = KitchenOrder.builder().id("ko-1").tenantId(TENANT_ID).sessionId("sess-1")
                .tableNumber(5).build();
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.of(order));

        kitchenService.handleTableLinksChanged(
                TableLinksChanged.linked(TENANT_ID, "sess-1", UUID.randomUUID(), List.of(6)));

        verify(kitchenOrderRepository).save(order);
        assertThat(order.getLinkedTableNumbers()).containsExactly(6);
    }

    @Test
    void handleTableLinksChanged_unlinkingTheLastTableBringsTheOrderBackToAnIndividualTable() {
        KitchenOrder order = KitchenOrder.builder().id("ko-1").tenantId(TENANT_ID).sessionId("sess-1")
                .tableNumber(5).linkedTableNumbers(new ArrayList<>(List.of(6))).build();
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.of(order));

        kitchenService.handleTableLinksChanged(
                TableLinksChanged.unlinked(TENANT_ID, "sess-1", UUID.randomUUID(), List.of()));

        assertThat(order.getLinkedTableNumbers()).isEmpty();
    }

    @Test
    void handleTableLinksChanged_aSessionWithoutAKitchenOrderYetIsANoOp() {
        when(kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1")).thenReturn(Optional.empty());

        kitchenService.handleTableLinksChanged(
                TableLinksChanged.linked(TENANT_ID, "sess-1", UUID.randomUUID(), List.of(6)));

        verify(kitchenOrderRepository, never()).save(any());
    }
```

`KitchenWebSocketListenerTest` (add imports `TableLinksChanged`):

```java
    @Test
    void onTableLinksChanged_sendsToTenantKitchenTopic() {
        TableLinksChanged event = TableLinksChanged.linked(TENANT_ID, "sess-1", UUID.randomUUID(), List.of(6));

        listener.onTableLinksChanged(event);

        verify(messagingTemplate).convertAndSend("/topic/kitchen/" + TENANT_ID, event);
    }
```

`KitchenTicketPrintServiceTest` (after `enqueue_buildsPendingKitchenTicketJob`):

```java
    @Test
    void enqueue_mergedTablesPrintTheJoinedLabelInsteadOfMesaN() {
        TenantContextHolder.setTenantId(TENANT_ID);
        KitchenOrder order = sampleOrder();
        order.setLinkedTableNumbers(new java.util.ArrayList<>(List.of(6)));
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(order));
        when(printJobRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        PrintJob job = service.enqueue("ko-1");

        assertThat(job.getPayload()).contains("M5+M6 - Unidas", "Tacos").doesNotContain("Mesa 5");
    }
```

`PrintingEventListenerTest` (after `…createsAndDispatchesKitchenJob`):

```java
    @Test
    void onKitchenItemsConfirmed_mergedTables_printTheJoinedLabel() {
        when(settingService.getSettings(TENANT_ID)).thenReturn(settingsWith(true));
        when(printJobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        OrderItem item = OrderItem.builder().id("i1").itemId(1L).name("Hamburguesa").build();

        printingEventListener.onKitchenItemsConfirmed(
                new KitchenItemsConfirmed(TENANT_ID, "session-1", 5, List.of(item), List.of(6)));

        ArgumentCaptor<PrintJob> jobCaptor = ArgumentCaptor.forClass(PrintJob.class);
        verify(printJobRepository).saveAndFlush(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getPayload()).contains("M5+M6 - Unidas").doesNotContain("Mesa 5");
    }
```

- [ ] **Step 2: Run** — `cd backend && ./mvnw test -Dtest=KitchenServiceTest,KitchenWebSocketListenerTest,KitchenTicketPrintServiceTest,PrintingEventListenerTest` → FAIL (compile).

- [ ] **Step 3: Implement**

`KitchenItemsConfirmed.java`:

```java
public record KitchenItemsConfirmed(
        UUID tenantId,
        String sessionId,
        int tableNumber,
        List<OrderItem> confirmedItems,
        List<Integer> linkedTableNumbers
) {
    /** Individual table: no linked tables. */
    public KitchenItemsConfirmed(UUID tenantId, String sessionId, int tableNumber, List<OrderItem> confirmedItems) {
        this(tenantId, sessionId, tableNumber, confirmedItems, List.of());
    }
}
```

`SessionService` — at both `new KitchenItemsConfirmed(` sites append the session's linked numbers: first site (`addItemAsWaiter`): `…, table.getTableNumber(), List.of(newItem), saved.linkedTableNumbers()));`; second site (`confirmDraftsForUser`): `…, table.getTableNumber(), drafts, savedSession.linkedTableNumbers()));`.

`KitchenService.handleOrderItemAdded` — in the builder add `.linkedTableNumbers(new ArrayList<>(event.linkedTableNumbers()))` after `.tableNumber(event.tableNumber())`, and after `order.setActive(true);` add `order.setLinkedTableNumbers(new ArrayList<>(event.linkedTableNumbers()));`. Add the listener (import `com.vanter.ember.session.event.TableLinksChanged`):

```java
    @EventListener
    public void handleTableLinksChanged(TableLinksChanged event) {
        kitchenOrderRepository.findByTenantIdAndSessionId(event.tenantId(), event.sessionId()).ifPresent(order -> {
            order.setLinkedTableNumbers(new ArrayList<>(event.linkedTableNumbers()));
            kitchenOrderRepository.save(order);
        });
    }
```

`KitchenWebSocketListener` (import `TableLinksChanged`):

```java
    @EventListener
    public void onTableLinksChanged(TableLinksChanged event) {
        messagingTemplate.convertAndSend("/topic/kitchen/" + event.tenantId(), event);
    }
```

`KitchenTicketPrintService.renderTicketPayload` line 59 becomes:

```java
        sb.append(TableLabels.ticketLine(order.getTableNumber(), order.getLinkedTableNumbers())).append('\n');
```
(import `com.vanter.ember.session.model.TableLabels`).

`PrintingEventListener.renderKitchenPayload` line 93 becomes:

```java
        sb.append(TableLabels.ticketLine(event.tableNumber(), event.linkedTableNumbers())).append('\n');
```
(same import).

- [ ] **Step 4: Run** — `cd backend && ./mvnw test -Dtest=KitchenServiceTest,KitchenWebSocketListenerTest,KitchenTicketPrintServiceTest,PrintingEventListenerTest,SessionServiceTest,KitchenControllerTest` → PASS.

---

### Task 6: Reports — receipt, Excel export, payments, analytics

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/printing/service/ReceiptLayout.java` (`Data` record, `Builder`, `render` line 91)
- Modify: `backend/src/main/java/com/vanter/ember/printing/service/ReceiptRenderer.java:71-76`
- Modify: `backend/src/main/java/com/vanter/ember/export/service/ExportService.java:142-168`
- Modify: `backend/src/main/java/com/vanter/ember/billing/dto/PaymentResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java:451-484`
- Modify: `backend/src/main/java/com/vanter/ember/analytics/dto/TablePerformance.java`
- Modify: `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java` (`getTables` ~lines 324-370; `TableTally` ~line 513)
- Test updates: `printing/service/ReceiptRendererTest.java`, `export/service/ExportServiceTest.java`, `billing/service/PaymentServiceTest.java`, `analytics/service/AnalyticsServiceTest.java`; constructor fixes in `cashregister/service/CashShiftServiceTest.java:273`, `billing/controller/BillingControllerTest.java:474`, `analytics/controller/AnalyticsControllerTest.java:438`

**Interfaces:**
- Consumes: `Session.linkedTableNumbers()`, `TableLabels.joined/ticketLine`.
- Produces: `PaymentResponse` gains last component `String tableLabel` (`"M3+M4"` when merged, else `null`); `TablePerformance` gains last component `List<Integer> mergedWithTableNumbers` (distinct, ascending; empty when never merged); `ReceiptLayout.Data` gains a last component `String tableLine` and `Builder.tableLine(String)`.

- [ ] **Step 1: Write the failing tests**

`ReceiptRendererTest` (add import `com.vanter.ember.session.model.LinkedTable`):

```java
    @Test
    void render_mergedTables_printTheJoinedLabelInsteadOfMesaN() {
        Bill bill = Bill.builder().id(12L).tenantId(TENANT).sessionId(SESSION_ID)
                .total(new BigDecimal("10.00")).createdAt(CREATED).build();
        when(bills.findById(12L)).thenReturn(Optional.of(bill));
        Session session = Session.builder().id(SESSION_ID).tableId(TABLE)
                .linkedTables(List.of(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(6)
                        .linkedAt(CREATED).build()))
                .items(List.of(item("Sopa", "10.00", OrderItemStatus.DELIVERED))).build();
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(tables.findById(TABLE)).thenReturn(Optional.of(DiningTables.builder().tableNumber(5).build()));

        String out = renderer.render(12L, settings);

        assertThat(out).contains("M5+M6 - Unidas").doesNotContain("Mesa 5");
    }
```

`ExportServiceTest` (new test modelled on `…oneRowPerBillWithTableAndDistinctConfirmedPaymentMethods`; add import `LinkedTable`):

```java
    @Test
    void buildTenantExportWorkbook_ventasSheet_mergedTablesShowTheJoinedLabelAsText() throws IOException {
        Bill bill = Bill.builder()
                .id(1L).sessionId("sess-1").total(new BigDecimal("50.00"))
                .splitMethod(SplitMethod.EQUAL_PARTS).status(BillStatus.PAID)
                .createdAt(LocalDateTime.of(2026, 8, 5, 20, 0)).build();
        when(billRepository.findByTenantIdAndCreatedAtBetweenAndStatusIn(eq(TENANT_ID), eq(FROM), eq(TO), any()))
                .thenReturn(List.of(bill));
        when(paymentRepository.findByBillIdIn(List.of(1L))).thenReturn(List.of());

        UUID tableId = UUID.randomUUID();
        Session session = Session.builder()
                .id("sess-1").tenantId(TENANT_ID).tableId(tableId).status(SessionStatus.CLOSED)
                .linkedTables(List.of(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(8)
                        .linkedAt(LocalDateTime.of(2026, 8, 5, 19, 40)).build()))
                .maxParticipants(4).createdAt(LocalDateTime.of(2026, 8, 5, 19, 30)).build();
        when(sessionRepository.findByTenantIdAndIdIn(TENANT_ID, List.of("sess-1"))).thenReturn(List.of(session));
        when(diningTableRepository.findByRestaurantIdAndIdIn(TENANT_ID, List.of(tableId)))
                .thenReturn(List.of(DiningTables.builder().id(tableId).restaurantId(TENANT_ID).tableNumber(7).build()));
        when(analyticsService.getProducts(TENANT_ID, FROM, TO, null)).thenReturn(emptyProducts());
        when(settingService.getSettings(TENANT_ID)).thenReturn(sampleSettings());

        Workbook workbook = readWorkbook(exportService.buildTenantExportWorkbook(TENANT_ID, FROM, TO));

        assertThat(text(workbook.getSheet("Ventas").getRow(9), 1)).isEqualTo("M7+M8");
    }
```

`PaymentServiceTest` (after `toResponses_resolvesTheTableNumberFromEachPaymentsSession`; add import `LinkedTable`):

```java
    @Test
    void toResponses_mergedSessionCarriesTheJoinedTableLabel_andUnmergedKeepsItNull() {
        Bill bill = Bill.builder()
                .id(1L).tenantId(TENANT_ID).sessionId("sess-1").total(new BigDecimal("22.50"))
                .splitMethod(SplitMethod.BY_CONSUMPTION).status(BillStatus.OPEN)
                .createdAt(LocalDateTime.now()).build();
        Payment payment = Payment.builder()
                .id(20L).bill(bill).participantName("Alice").amount(new BigDecimal("22.50"))
                .method(PaymentMethod.PHYSICAL).status(PaymentStatus.CONFIRMED)
                .createdAt(LocalDateTime.now()).build();
        Session merged = sampleSession();
        merged.getLinkedTables().add(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(6)
                .linkedAt(LocalDateTime.now()).build());
        when(sessionService.findById("sess-1")).thenReturn(merged);
        when(diningTableRepository.findByRestaurantIdAndIdIn(TENANT_ID, Set.of(TABLE_ID)))
                .thenReturn(List.of(DiningTables.builder().id(TABLE_ID).tableNumber(5).build()));
        when(refundRepository.sumByPaymentId(20L)).thenReturn(BigDecimal.ZERO);

        PaymentResponse mergedResponse = paymentService.toResponses(List.of(payment)).get(0);

        assertThat(mergedResponse.tableNumber()).isEqualTo(5);
        assertThat(mergedResponse.tableLabel()).isEqualTo("M5+M6");
    }
```
and in the existing `toResponses_resolvesTheTableNumberFromEachPaymentsSession` add `assertThat(responses.get(0).tableLabel()).isNull();`.

`AnalyticsServiceTest` (after `getTables_attributesRevenueAndTurnoverPerTableOrderedByRevenue`; add import `LinkedTable`):

```java
    @Test
    void getTables_listsTheDistinctTablesEachPrimaryWasMergedWith_withoutChangingTotals() {
        UUID tableA = UUID.randomUUID();
        when(billRepository.findPaidBillActivity(eq(TENANT_ID), any(), any())).thenReturn(List.of(
                paidBill("s-1", "100.00", LocalDateTime.of(2026, 8, 2, 20, 30)),
                paidBill("s-2", "50.00", LocalDateTime.of(2026, 8, 3, 21, 0))));
        Session first = sessionAt("s-1", tableA, LocalDateTime.of(2026, 8, 2, 20, 0));
        first.getLinkedTables().add(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(4)
                .linkedAt(LocalDateTime.of(2026, 8, 2, 20, 5)).build());
        Session second = sessionAt("s-2", tableA, LocalDateTime.of(2026, 8, 3, 20, 0));
        second.getLinkedTables().add(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(4)
                .linkedAt(LocalDateTime.of(2026, 8, 3, 20, 5)).build());
        second.getLinkedTables().add(LinkedTable.builder().tableId(UUID.randomUUID()).tableNumber(5)
                .linkedAt(LocalDateTime.of(2026, 8, 3, 20, 6)).build());
        when(sessionRepository.findByTenantIdAndIdIn(eq(TENANT_ID), any())).thenReturn(List.of(first, second));
        when(diningTableRepository.findByRestaurantIdAndIdIn(eq(TENANT_ID), any()))
                .thenReturn(List.of(DiningTables.builder().id(tableA).tableNumber(3).build()));
        when(diningTableRepository.countByRestaurantIdAndIsActiveTrue(TENANT_ID)).thenReturn(5L);

        AnalyticsTablesResponse response = analyticsService.getTables(TENANT_ID, FROM, TO);

        assertThat(response.totalRevenue()).isEqualByComparingTo("150.00");
        assertThat(response.totalTurnovers()).isEqualTo(2L);
        assertThat(response.tables()).singleElement().satisfies(table -> {
            assertThat(table.tableNumber()).isEqualTo(3);
            assertThat(table.mergedWithTableNumbers()).containsExactly(4, 5);
        });
    }
```
In the existing `getTables_attributesRevenue…` test add `assertThat(best.mergedWithTableNumbers()).isEmpty();`.

Constructor fixes (mechanical): `CashShiftServiceTest:273` and `BillingControllerTest:474` — append `, null` as the new last `PaymentResponse` argument; `AnalyticsControllerTest:438` — append `, List.of()` as the new last `TablePerformance` argument.

- [ ] **Step 2: Run** — `cd backend && ./mvnw test -Dtest=ReceiptRendererTest,ExportServiceTest,PaymentServiceTest,AnalyticsServiceTest,CashShiftServiceTest,BillingControllerTest,AnalyticsControllerTest` → FAIL (compile).

- [ ] **Step 3: Implement**

`ReceiptLayout.java`: add `String tableLine` as the LAST component of `Data` (after `boolean tipLine`); in `Builder` add `private String tableLine;`, `Builder tableLine(String v) { this.tableLine = v; return this; }` and pass it as the last argument in `build()`. In `render`, replace lines 91-93 with:

```java
        if (d.tableLine() != null) {
            out.append(d.tableLine()).append('\n');
        } else if (d.tableNumber() != null) {
            out.append("Mesa ").append(d.tableNumber()).append('\n');
        }
```
If the compiler reports another `new ReceiptLayout.Data(` call site, add `, null` there.

`ReceiptRenderer.addSession` (lines 72-76) becomes (import `TableLabels`):

```java
        if (session.getTableId() != null) {
            diningTableRepository.findById(session.getTableId())
                    .map(DiningTables::getTableNumber)
                    .ifPresent(number -> {
                        data.tableNumber(number);
                        List<Integer> linked = session.linkedTableNumbers();
                        if (!linked.isEmpty()) {
                            data.tableLine(TableLabels.ticketLine(number, linked));
                        }
                    });
        }
```

`ExportService` — inside the loop replace the `tableNumber` write (lines 145-146 and 166-168):

```java
            UUID tableId = session == null ? null : session.getTableId();
            Integer tableNumber = tableId == null ? null : tableNumbersById.get(tableId);
            List<Integer> linkedNumbers = session == null ? List.of() : session.linkedTableNumbers();
```
```java
            if (tableNumber != null && !linkedNumbers.isEmpty()) {
                textCell(row, 1, TableLabels.joined(tableNumber, linkedNumbers), styles.text());
            } else if (tableNumber != null) {
                numericCell(row, 1, tableNumber, styles.count());
            }
```
(import `com.vanter.ember.session.model.TableLabels`).

`PaymentResponse.java` — add last component `String tableLabel`.

`PaymentService` — replace `resolveTableNumbers` and its use:

```java
    public List<PaymentResponse> toResponses(List<Payment> payments) {
        Map<String, TableRef> tableBySessionId = resolveTables(payments);
        return payments.stream().map(p -> {
            BigDecimal refunded = refundRepository.sumByPaymentId(p.getId());
            TableRef table = tableBySessionId.get(p.getBill().getSessionId());
            return new PaymentResponse(
                    p.getId(), p.getBill().getId(), p.getParticipantName(), p.getAmount(),
                    p.getMethod().name(), p.getStatus().name(), p.getCreatedAt(),
                    refunded, p.getAmount().subtract(refunded),
                    table == null ? null : table.number(),
                    table == null ? null : table.label());
        }).toList();
    }

    private record TableRef(Integer number, String label) {}

    /**
     * A shift's payment list spans many tables — the admin Corte Z view wants to show which table
     * each payment was for, not just who paid it. Resolved via each bill's session (for its table
     * id and linked tables) then a single batch lookup of table numbers, rather than a query per
     * payment. {@code label} is the merged form ({@code M3+M4}) and is null for an individual table.
     */
    private Map<String, TableRef> resolveTables(List<Payment> payments) {
        if (payments.isEmpty()) {
            return Map.of();
        }
        UUID tenantId = payments.get(0).getBill().getTenantId();
        Map<String, Session> sessionById = payments.stream()
                .map(p -> p.getBill().getSessionId())
                .distinct()
                .collect(Collectors.toMap(sessionId -> sessionId, sessionService::findById));
        Set<UUID> tableIds = sessionById.values().stream().map(Session::getTableId).collect(Collectors.toSet());
        Map<UUID, Integer> tableNumberByTableId = diningTableRepository
                .findByRestaurantIdAndIdIn(tenantId, tableIds).stream()
                .collect(Collectors.toMap(DiningTables::getId, DiningTables::getTableNumber));
        return sessionById.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> {
            Session session = e.getValue();
            Integer number = tableNumberByTableId.get(session.getTableId());
            List<Integer> linked = session.linkedTableNumbers();
            String label = number != null && !linked.isEmpty() ? TableLabels.joined(number, linked) : null;
            return new TableRef(number, label);
        }));
    }
```
(imports `com.vanter.ember.session.model.Session`, `com.vanter.ember.session.model.TableLabels`; remove the now-unused old method.)

`TablePerformance.java` — add last component `List<Integer> mergedWithTableNumbers` (import `java.util.List`).

`AnalyticsService` — in `TableTally` add `private final java.util.SortedSet<Integer> mergedWith = new java.util.TreeSet<>();`; in the tally loop (line ~330) change to:

```java
            TableTally tally = tallies.computeIfAbsent(session.getTableId(), id -> new TableTally());
            tally.add(bill.total(), session.getCreatedAt(), bill.createdAt());
            tally.mergedWith.addAll(session.linkedTableNumbers());
```
and pass `List.copyOf(tally.mergedWith)` as the new last argument of `new TablePerformance(...)` (line ~363-369). Update the Javadoc sentence of `getTables` with: "Linked tables of a merged session get no row of their own; they are listed on the primary's `mergedWithTableNumbers`."

- [ ] **Step 4: Run** — `cd backend && ./mvnw test -Dtest=ReceiptRendererTest,ReceiptLayoutTest,ExportServiceTest,PaymentServiceTest,AnalyticsServiceTest,CashShiftServiceTest,BillingControllerTest,AnalyticsControllerTest` → PASS.

- [ ] **Step 5: Full backend suite** — `cd backend && ./mvnw test`. Expected: green except the two MinIO tests that PROGRESS.md documents as passing alone but flaky in the full run; re-run those two alone to confirm.

---

### Task 7: Frontend types, API client, kitchen label, analytics + payment text

**Files:**
- Modify: `frontend/src/lib/backend-types.ts` (hand-edit; it is normally generated by `pnpm run openapi` against a running backend — hand-editing is the offline equivalent and a later regeneration yields the same shapes)
- Modify: `frontend/src/lib/api.ts` (inside `SessionTableService`, next to `transferTable`, ~line 526)
- Create: `frontend/src/pages/kitchen/lib/mergedTableLabel.ts` and `mergedTableLabel.test.ts`
- Modify: `frontend/src/pages/kitchen/components/QueueCard.tsx:40-42`, `FocusedCard.tsx:77-79`
- Modify: `frontend/src/locales/{es,en}/kitchen.ts`, `frontend/src/locales/{es,en}/admin.ts`
- Modify: `frontend/src/pages/admin/analytics/components/TableAnalytics.tsx:80-83`
- Modify: `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx:116`
- Test: `frontend/src/pages/kitchen/components/FocusedCard.test.tsx` (append), new `frontend/src/pages/admin/analytics/components/TableAnalytics.test.tsx`

**Interfaces:**
- Produces: `SessionTableService.linkTable(sessionId: string, tableId: string): Promise<void>`, `SessionTableService.unlinkTable(sessionId: string, tableId: string): Promise<void>`; `mergedTableLabel(tableNumber: number | undefined, linked: number[] | undefined, word: string): string | null` (null when not merged, else `"M3+M4 - <word>"`); types: `DashboardResponse` gains `linkedTables?: { tableId?: string; tableNumber?: number }[]`, `linkedToTableId?: string`, `linkedToTableNumber?: number`; `kitchenOrders` gains `linkedTableNumbers?: number[]`; `TablePerformance` gains `mergedWithTableNumbers?: number[]`; the payment type gains `tableLabel?: string`.

- [ ] **Step 1: Write the failing tests**

`mergedTableLabel.test.ts`:

```ts
import { describe, expect, test } from 'vitest'
import { mergedTableLabel } from './mergedTableLabel'

describe('mergedTableLabel', () => {
  test('is null for an individual table', () => {
    expect(mergedTableLabel(3, [], 'Unidas')).toBeNull()
    expect(mergedTableLabel(3, undefined, 'Unidas')).toBeNull()
  })

  test('joins the primary and every linked table in order, then the word', () => {
    expect(mergedTableLabel(3, [4], 'Unidas')).toBe('M3+M4 - Unidas')
    expect(mergedTableLabel(3, [4, 5], 'Unidas')).toBe('M3+M4+M5 - Unidas')
  })

  test('is null when the primary number is unknown', () => {
    expect(mergedTableLabel(undefined, [4], 'Unidas')).toBeNull()
  })
})
```

Append to `FocusedCard.test.tsx`:

```tsx
describe('FocusedCard table label', () => {
  test('an individual table keeps the plain heading', () => {
    wrap(sampleOrder)

    expect(screen.getByRole('heading', { name: 'Detalles de Orden - M5' })).toBeVisible()
  })

  test('merged tables show M3+M4 - Unidas', () => {
    wrap({ ...sampleOrder, tableNumber: 3, linkedTableNumbers: [4] } as kitchenOrders)

    expect(screen.getByRole('heading', { name: 'Detalles de Orden - M3+M4 - Unidas' })).toBeVisible()
  })
})
```

`TableAnalytics.test.tsx` (new):

```tsx
import { describe, expect, test, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TableAnalytics } from './TableAnalytics'
import { analyticsService } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return { ...actual, analyticsService: { ...actual.analyticsService, getTables: vi.fn() } }
})

const wrap = () =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TableAnalytics />
    </QueryClientProvider>,
  )

describe('TableAnalytics merged tables', () => {
  beforeEach(() => vi.clearAllMocks())

  test('a table that was merged shows "Fusionada con M4, M5"; an unmerged one shows nothing extra', async () => {
    vi.mocked(analyticsService.getTables).mockResolvedValue({
      activeTableCount: 5,
      totalTurnovers: 2,
      totalRevenue: 150,
      averageTurnoverRate: 0.4,
      averageSessionDurationMinutes: 30,
      tables: [
        { tableId: 'a', tableNumber: 3, turnoverCount: 1, revenue: 100, revenueShare: 66, averageSessionDurationMinutes: 30, mergedWithTableNumbers: [4, 5] },
        { tableId: 'b', tableNumber: 7, turnoverCount: 1, revenue: 50, revenueShare: 33, averageSessionDurationMinutes: 20, mergedWithTableNumbers: [] },
      ],
    } as never)

    wrap()

    expect(await screen.findByText('Fusionada con M4, M5')).toBeVisible()
    expect(screen.getAllByText(/Fusionada con/)).toHaveLength(1)
  })
})
```

- [ ] **Step 2: Run** — `cd frontend && pnpm exec vitest run src/pages/kitchen src/pages/admin/analytics` → FAIL (module/label missing).

- [ ] **Step 3: Implement**

`backend-types.ts` — edit by hand:
- `TableStatusResponse`: add `linkedTables?: components["schemas"]["LinkedTableSummary"][]; /** Format: uuid */ linkedToTableId?: string; /** Format: int32 */ linkedToTableNumber?: number;`
- add schema `LinkedTableSummary: { /** Format: uuid */ tableId?: string; /** Format: int32 */ tableNumber?: number; };` next to `ActiveSessionSummary`
- `KitchenOrder`: add `linkedTableNumbers?: number[];`
- `TablePerformance`: add `mergedWithTableNumbers?: number[];`
- `PaymentResponse` schema: add `tableLabel?: string;`
- both `type?: "ITEM_SENT" | … | "PARTICIPANT_LEFT"` unions (lines ~2082, 2153): add `| "TABLE_LINKED" | "TABLE_UNLINKED"` (and `"CLOSED_BY_ADMIN"` is already absent there — leave it).

`api.ts` — in `SessionTableService` after `transferTable`:

```ts
  // Attach a free table to this open session (it shows occupied until the session closes).
  linkTable: async (sessionId: string, tableId: string): Promise<void> => {
    await api.post<void>(`/sessions/${sessionId}/linked-tables`, { tableId })
  },

  // Detach a linked table; it is free immediately.
  unlinkTable: async (sessionId: string, tableId: string): Promise<void> => {
    await api.delete<void>(`/sessions/${sessionId}/linked-tables/${tableId}`)
  },
```

`mergedTableLabel.ts`:

```ts
// Kitchen label for a merged table group: "M3+M4 - Unidas". Null when the table is not merged,
// so callers fall back to their existing individual-table rendering.
export const mergedTableLabel = (
  tableNumber: number | undefined,
  linked: number[] | undefined,
  word: string,
): string | null => {
  if (tableNumber == null || !linked || linked.length === 0) return null
  return `M${tableNumber}${linked.map((n) => `+M${n}`).join('')} - ${word}`
}
```

Locales — `es/kitchen.ts`: add `mergedWord: 'Unidas',` and `orderDetailsHeadingMerged: 'Detalles de Orden - {{label}}',`; `en/kitchen.ts`: `mergedWord: 'Joined',` and `orderDetailsHeadingMerged: 'Order Details - {{label}}',`. `es/admin.ts` after `tableNumberLabel`: `mergedWithTables: 'Fusionada con {{tables}}',`; `en/admin.ts`: `mergedWithTables: 'Merged with {{tables}}',`.

`QueueCard.tsx` (add `import { mergedTableLabel } from '../lib/mergedTableLabel'`) — replace the `CardTitle` content:

```tsx
                    {mergedTableLabel(order.tableNumber, order.linkedTableNumbers, t('mergedWord')) ?? (order.tableNumber || "?")}
```

`FocusedCard.tsx` (same import) — replace the heading body:

```tsx
              {(() => {
                const merged = mergedTableLabel(order.tableNumber, order.linkedTableNumbers, t('mergedWord'))
                return merged
                  ? t('orderDetailsHeadingMerged', { label: merged })
                  : t('orderDetailsHeading', { tableNumber: order.tableNumber ?? '' })
              })()}
```

`TableAnalytics.tsx` — inside the left `<span>` row, make the left side a column:

```tsx
                  <span className="font-medium text-foreground">
                    {t('tableNumberLabel', { tableNumber: table.tableNumber ?? '—' })}
                    {(table.mergedWithTableNumbers?.length ?? 0) > 0 && (
                      <span className="ml-2 text-xs font-normal text-muted-foreground">
                        {t('mergedWithTables', {
                          tables: (table.mergedWithTableNumbers ?? []).map((n) => `M${n}`).join(', '),
                        })}
                      </span>
                    )}
                  </span>
```

`ShiftHistoryTable.tsx:116` → `{payment.tableLabel ?? (payment.tableNumber != null ? `#${payment.tableNumber}` : '—')}` (keep the surrounding JSX structure).

- [ ] **Step 4: Run** — `cd frontend && pnpm exec vitest run src/pages/kitchen src/pages/admin/analytics src/pages/admin/cashRegister && pnpm run build && pnpm run lint` → PASS, 0 lint errors.

---

### Task 8: Waiter floor — grouped cards, "Unir mesa" button flow, "Separar"

**Files:**
- Modify: `frontend/src/store/uiStore.ts` (add `'LINK_TABLE'` to `ModalType`)
- Create: `frontend/src/pages/waiter/components/TableCard.tsx` (presentation only in this task; DnD hooks arrive in Task 9)
- Create: `frontend/src/pages/waiter/components/LinkTableModal.tsx`, `LinkTableModal.test.tsx`
- Modify: `frontend/src/pages/waiter/Tables.tsx`
- Modify: `frontend/src/locales/{es,en}/waiter.ts`
- Test: `frontend/src/pages/waiter/Tables.test.tsx` (append)

**Interfaces:**
- Consumes (Task 7): `SessionTableService.linkTable/unlinkTable`, `DashboardResponse` fields.
- Produces: `<TableCard table={DashboardResponse} selected={boolean} canBrowse={boolean} onSelect={() => void} />` (task 9 extends it with DnD props); `<LinkTableModal />` (opened with `openModal('LINK_TABLE', { sessionId, tableNumber })`, lists free tables from the `['dashboardData', restaurantId]` query); i18n keys below.

- [ ] **Step 1: Write the failing tests**

Append to `Tables.test.tsx` (same file, new describe; it reuses the file's `wrap`, mocks and `useAuthStore`; add `SessionTableService` to the `@/lib/api` mock block as `SessionTableService: { ...actual.SessionTableService, linkTable: vi.fn(), unlinkTable: vi.fn() }` and import it):

```tsx
describe('Tables with merged tables', () => {
  const merged = [
    {
      tableId: 't3', tableNumber: 3, isOccupied: true,
      currentSession: { sessionId: 'sess-3', waiterName: 'Fe', currentParticipant: 4 },
      linkedTables: [{ tableId: 't4', tableNumber: 4 }],
    },
    {
      tableId: 't4', tableNumber: 4, isOccupied: true,
      currentSession: { sessionId: 'sess-3', waiterName: 'Fe', currentParticipant: 4 },
      linkedToTableId: 't3', linkedToTableNumber: 3, linkedTables: [],
    },
    { tableId: 't5', tableNumber: 5, isOccupied: false, linkedTables: [] },
  ]

  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ restaurantId: 'restaurant-1', role: 'WAITER' })
    vi.mocked(cashShiftService.current).mockResolvedValue({ status: 'OPEN' } as never)
    vi.mocked(DashboardService.getDashboardData).mockResolvedValue(merged as never)
  })

  test('a linked table shows as occupied and says which table it is joined to', async () => {
    wrap()
    expect(await screen.findByText('Unida a M3')).toBeVisible()
  })

  test('the primary offers "Unir mesa" and lists its linked table with a Separar action', async () => {
    wrap()
    fireEvent.click(await screen.findByText('M3'))

    expect(await screen.findByRole('button', { name: 'Unir mesa' })).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Separar M4' }))
    await waitFor(() => expect(SessionTableService.unlinkTable).toHaveBeenCalledWith('sess-3', 't4'))
  })

  test('a free table has no "Unir mesa" button', async () => {
    wrap()
    fireEvent.click(await screen.findByText('M5'))

    expect(screen.queryByRole('button', { name: 'Unir mesa' })).not.toBeInTheDocument()
  })

  test('picking a free table in the modal links it to the open session', async () => {
    vi.mocked(SessionTableService.linkTable).mockResolvedValue(undefined)
    wrap()
    fireEvent.click(await screen.findByText('M3'))
    fireEvent.click(await screen.findByRole('button', { name: 'Unir mesa' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Mesa 5' }))
    fireEvent.click(screen.getByRole('button', { name: 'Unir' }))

    await waitFor(() => expect(SessionTableService.linkTable).toHaveBeenCalledWith('sess-3', 't5'))
  })
})
```
(import `waitFor` from `@testing-library/react` in that file.)

- [ ] **Step 2: Run** — `cd frontend && pnpm exec vitest run src/pages/waiter/Tables.test.tsx` → FAIL.

- [ ] **Step 3: Implement**

`uiStore.ts`: change `'ADD_ITEM' | 'TRANSFER_TABLE' |` to `'ADD_ITEM' | 'TRANSFER_TABLE' | 'LINK_TABLE' |`.

Locales (`waiter.ts`). ES: `linkTableButton: 'Unir mesa'`, `linkModalTitle: 'Unir mesa a M{{table}}'`, `linkModalDescription: 'Elige una mesa libre para sumarla a esta cuenta.'`, `linkNoFreeTables: 'No hay mesas libres para unir.'`, `linkSubmit: 'Unir'`, `linkSuccessToast: 'M{{table}} unida'`, `linkErrorToast: 'No se pudo unir la mesa. Puede que ya esté ocupada.'`, `unlinkButton: 'Separar'`, `unlinkAria: 'Separar M{{table}}'`, `unlinkSuccessToast: 'M{{table}} separada'`, `unlinkErrorToast: 'No se pudo separar la mesa.'`, `linkedToLabel: 'Unida a M{{table}}'`, `linkedTablesHeading: 'Mesas unidas'`, `linkTableOption: 'Mesa {{table}}'`, `linkDragHint: 'Mantén presionada una mesa libre y arrástrala sobre una ocupada para unirla.'`. EN: `'Link table'`, `'Link a table to M{{table}}'`, `'Pick a free table to add to this bill.'`, `'No free tables to link.'`, `'Link'`, `'M{{table}} linked'`, `'Could not link the table. It may already be taken.'`, `'Unlink'`, `'Unlink M{{table}}'`, `'M{{table}} unlinked'`, `'Could not unlink the table.'`, `'Linked to M{{table}}'`, `'Linked tables'`, `'Table {{table}}'`, `'Press and hold a free table, then drag it onto an occupied one to link it.'`.

`TableCard.tsx` — extract the card JSX that is currently inline in `Tables.tsx` (lines 116-157) with these changes: props `{ table, selected, canBrowse, onSelect }`; for `table.linkedToTableId` show `t('linkedToLabel', { table: table.linkedToTableNumber ?? '?' })` in the bottom-left instead of the waiter avatar and show no participant count (`0`/count badge hidden); everything else identical (classes, pulse overlay, avatar for primaries):

```tsx
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Armchair, Users } from 'lucide-react'
import { AvatarInitials, getAvatarColor } from '@/components/AvatarInitials'
import { useTranslation } from '@/lib/i18n'
import type { DashboardResponse } from '@/lib/api'

interface TableCardProps {
  table: DashboardResponse
  selected: boolean
  canBrowse: boolean
  onSelect: () => void
}

export const TableCard = ({ table, selected, canBrowse, onSelect }: TableCardProps) => {
  const { t } = useTranslation('waiter')
  const isLinked = !!table.linkedToTableId

  return (
    <Card
      onClick={onSelect}
      className={`${selected ? 'shadow-[0_0_8px_1px_rgba(140,23,23,0.35)]' : 'shadow-sm'} border-zinc-100
        h-40 flex flex-col justify-between rounded-2xl relative
        ${canBrowse ? 'cursor-pointer' : 'pointer-events-none cursor-not-allowed blur-sm'}
        ${table.isOccupied ? 'border-2 bg-[#8c1717] text-white' : 'bg-white text-black'}`}
    >
      {selected && (
        <div className="pointer-events-none absolute inset-0 z-20 animate-pulse rounded-2xl border-2 border-[#8c1717] shadow-[inset_0_0_0_3px_white,inset_0_0_6px_1px_rgba(140,23,23,0.35)]" />
      )}
      {isLinked ? (
        <span className="absolute bottom-4 left-4 z-10 rounded-full bg-white/90 px-2 py-0.5 text-[11px] font-bold text-[#8c1717]">
          {t('linkedToLabel', { table: table.linkedToTableNumber ?? '?' })}
        </span>
      ) : (
        table.isOccupied &&
        table.currentSession?.waiterName && (
          <div
            title={table.currentSession.waiterName}
            className={`absolute bottom-4 left-4 z-10 flex size-7 items-center justify-center rounded-full text-[11px] font-bold ${getAvatarColor(table.currentSession.waiterName)}`}
          >
            {AvatarInitials(table.currentSession.waiterName)}
          </div>
        )
      )}
      <CardHeader className="p-4 pb-0 flex justify-between">
        <span className=" text-2xl font-bold">M{table.tableNumber}</span>
        {!isLinked && (
          <div
            className={`flex items-center justify-center gap-1 rounded-full h-6 w-11 bg-white text-black ${table.isOccupied ? 'border-2 border-[#8b0000]' : ''}`}
          >
            <Users className="h-4 w-4" />
            {table.isOccupied ? table.currentSession?.currentParticipant : '0'}
          </div>
        )}
      </CardHeader>
      <div className="absolute inset-0 pointer-events-none flex items-center justify-center">
        <Armchair className={`${table.isOccupied ? 'text-white' : 'text-zinc-400'} w-7 h-7`} />
      </div>
      {table.isOccupied && <CardContent className="p-4"></CardContent>}
    </Card>
  )
}
```

`LinkTableModal.tsx`:

```tsx
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { DashboardService, SessionTableService } from '@/lib/api'
import { useAuthStore } from '@/store/authStore'
import { useUIStore } from '@/store/uiStore'
import { useTranslation } from '@/lib/i18n'

export const LinkTableModal = () => {
  const { t } = useTranslation('waiter')
  const { activeModal, modalPayload, closeModal } = useUIStore()
  const { restaurantId } = useAuthStore()
  const queryClient = useQueryClient()

  const isOpen = activeModal === 'LINK_TABLE'
  const sessionId: string | undefined = modalPayload?.sessionId
  const tableNumber: number | undefined = modalPayload?.tableNumber
  const [selectedId, setSelectedId] = useState<string | null>(null)

  const { data: tables = [] } = useQuery({
    queryKey: ['dashboardData', restaurantId],
    queryFn: () => DashboardService.getDashboardData(),
    enabled: isOpen && !!restaurantId,
  })
  const freeTables = tables.filter((table) => !table.isOccupied)

  const handleClose = () => {
    setSelectedId(null)
    closeModal()
  }

  const mutation = useMutation({
    mutationFn: () => SessionTableService.linkTable(sessionId!, selectedId!),
    onSuccess: () => {
      const linked = freeTables.find((table) => table.tableId === selectedId)
      toast.success(t('linkSuccessToast', { table: linked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
      handleClose()
    },
    onError: () => {
      toast.error(t('linkErrorToast'))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
  })

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && handleClose()}>
      <DialogContent className="sm:max-w-md rounded-3xl p-6">
        <DialogHeader className="mb-2">
          <DialogTitle className="text-2xl font-bold text-zinc-800">
            {t('linkModalTitle', { table: tableNumber ?? '' })}
          </DialogTitle>
          <DialogDescription className="text-zinc-500 text-sm mt-1">
            {t('linkModalDescription')}
          </DialogDescription>
        </DialogHeader>

        {freeTables.length === 0 ? (
          <p className="text-sm text-zinc-500 py-4 text-center">{t('linkNoFreeTables')}</p>
        ) : (
          <div className="grid grid-cols-3 gap-2 max-h-72 overflow-y-auto pr-1">
            {freeTables.map((table) => (
              <button
                key={table.tableId}
                type="button"
                onClick={() => setSelectedId(table.tableId!)}
                className={`rounded-2xl border-2 px-3 py-4 font-semibold transition-colors ${
                  selectedId === table.tableId
                    ? 'border-[#8B0000] bg-[#8B0000]/5'
                    : 'border-zinc-200 hover:border-zinc-300'
                }`}
              >
                {t('linkTableOption', { table: table.tableNumber })}
              </button>
            ))}
          </div>
        )}

        <DialogFooter>
          <Button
            className="w-full"
            onClick={() => mutation.mutate()}
            disabled={!selectedId || mutation.isPending}
          >
            {t('linkSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

`Tables.tsx` changes:
1. Imports: `TableCard`, `LinkTableModal`, `SessionTableService`, `useMutation`, `useQueryClient`, `toast from 'react-hot-toast'`; remove the now-unused `Card, CardContent, CardHeader`, `Users`, `AvatarInitials/getAvatarColor` imports if lint flags them (keep `Armchair`, `Button`).
2. Replace the `dashboardData?.map((table) => ( <Card …>…</Card> ))` block with:

```tsx
          {dashboardData?.map((table) => (
            <TableCard
              key={table.tableId}
              table={table}
              selected={table.tableId === selectedTable}
              canBrowse={canBrowse}
              onSelect={() => canBrowse && setSelectedTable(table.tableId)}
            />
          ))}
```
3. Add below the legend a hint: `<p className="mb-3 text-xs text-zinc-500">{t('linkDragHint')}</p>` (Task 9 makes it true; ship both together — only hide it until Task 9 lands if you commit partial work, which this plan does not).
4. Add the unlink mutation next to the other hooks:

```tsx
  const queryClient = useQueryClient()
  const unlinkMutation = useMutation({
    mutationFn: ({ sessionId, tableId }: { sessionId: string; tableId: string }) =>
      SessionTableService.unlinkTable(sessionId, tableId),
    onSuccess: (_data, vars) => {
      const unlinked = tableDetails?.linkedTables?.find((l) => l.tableId === vars.tableId)
      toast.success(t('unlinkSuccessToast', { table: unlinked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
    onError: () => toast.error(t('unlinkErrorToast')),
  })
```
5. In the detail panel, inside the `flex flex-col gap-4 mt-6` block, before the "Asignar Mesa" button, add (primary = occupied and not linked):

```tsx
                {tableDetails.isOccupied && !tableDetails.linkedToTableId && (
                  <>
                    <Button
                      variant="outline"
                      className="w-full text-md"
                      onClick={() =>
                        openModal('LINK_TABLE', {
                          sessionId: tableDetails.currentSession?.sessionId,
                          tableNumber: tableDetails.tableNumber,
                        })
                      }
                    >
                      {t('linkTableButton')}
                    </Button>
                    {(tableDetails.linkedTables?.length ?? 0) > 0 && (
                      <div className="flex flex-col gap-2">
                        <span className="text-xs text-zinc-500">{t('linkedTablesHeading')}</span>
                        {tableDetails.linkedTables!.map((linked) => (
                          <div key={linked.tableId} className="flex items-center justify-between">
                            <span className="font-semibold">M{linked.tableNumber}</span>
                            <Button
                              variant="outline"
                              size="sm"
                              aria-label={t('unlinkAria', { table: linked.tableNumber })}
                              disabled={unlinkMutation.isPending}
                              onClick={() =>
                                unlinkMutation.mutate({
                                  sessionId: tableDetails.currentSession!.sessionId!,
                                  tableId: linked.tableId!,
                                })
                              }
                            >
                              {t('unlinkButton')}
                            </Button>
                          </div>
                        ))}
                      </div>
                    )}
                  </>
                )}
```
6. Render `<LinkTableModal />` next to `<ParticipantQrModal />`.
7. For a linked table selected in the panel (`tableDetails.linkedToTableId`), the status row already shows "Ocupada"; add under the title `<span className="text-xs text-zinc-500">{t('linkedToLabel', { table: tableDetails.linkedToTableNumber ?? '?' })}</span>` (inside the title `div`). "Ver info" keeps working (it uses the shared `sessionId`).

`LinkTableModal.test.tsx` is covered by the `Tables.test.tsx` flow above; no separate file needed — do not create an empty one.

- [ ] **Step 4: Run** — `cd frontend && pnpm exec vitest run src/pages/waiter && pnpm run build && pnpm run lint` → PASS. The existing `Tables.test.tsx` cases (empty state, caja closed, selected highlight) must still pass unchanged.

---

### Task 9: Drag-and-drop with `@dnd-kit/core` (long-press, drag-time pulse)

**Files:**
- Modify: `frontend/package.json`, `frontend/pnpm-lock.yaml` (via `pnpm add`)
- Create: `frontend/src/pages/waiter/lib/linkDrop.ts`, `linkDrop.test.ts`
- Modify: `frontend/src/pages/waiter/components/TableCard.tsx`, `frontend/src/pages/waiter/Tables.tsx`
- Test: `frontend/src/pages/waiter/Tables.test.tsx` (append)

**Interfaces:**
- Consumes (Task 8): `TableCard`, `SessionTableService.linkTable`, `linkErrorToast`, `linkSuccessToast`.
- Produces: `resolveLinkDrop(activeId: string, over: { id: string; sessionId?: string } | null): { sessionId: string; tableId: string } | null` — returns the request to send, or null when the drop is not a valid merge (no target, dropped on itself, or the target has no session).

- [ ] **Step 1: Install the approved dependency**

Run: `cd frontend && pnpm add @dnd-kit/core@6.3.1`
Expected: added to `dependencies`, lockfile updated; peer `react >=16.8` satisfied by React 19. Then `pnpm audit --prod` → no new advisories attributable to `@dnd-kit/*` (if `pnpm audit` is unavailable offline, note it in the report instead of skipping silently).

- [ ] **Step 2: Write the failing tests**

`linkDrop.test.ts`:

```ts
import { describe, expect, test } from 'vitest'
import { resolveLinkDrop } from './linkDrop'

describe('resolveLinkDrop', () => {
  test('a free table dropped on an occupied one resolves to a link request', () => {
    expect(resolveLinkDrop('t5', { id: 't3', sessionId: 'sess-3' })).toEqual({ sessionId: 'sess-3', tableId: 't5' })
  })

  test('no drop target resolves to nothing', () => {
    expect(resolveLinkDrop('t5', null)).toBeNull()
  })

  test('dropping a table on itself resolves to nothing', () => {
    expect(resolveLinkDrop('t3', { id: 't3', sessionId: 'sess-3' })).toBeNull()
  })

  test('a target without a session (a free table) resolves to nothing', () => {
    expect(resolveLinkDrop('t5', { id: 't6' })).toBeNull()
  })
})
```

Append to `Tables.test.tsx` (inside the "Tables with merged tables" describe from Task 8):

```tsx
  test('only free tables are draggable; occupied tables are not', async () => {
    wrap()
    await screen.findByText('M5')

    expect(screen.getByText('M5').closest('[aria-roledescription="draggable"]')).not.toBeNull()
    expect(screen.getByText('M3').closest('[aria-roledescription="draggable"]')).toBeNull()
  })
```
(`@dnd-kit` sets `aria-roledescription="draggable"` via `attributes`, and `aria-disabled` when disabled; the assertion above relies on rendering draggable attributes ONLY for free tables — implement accordingly in `TableCard` by spreading `attributes`/`listeners` only when `draggable`.)

- [ ] **Step 3: Run** — `cd frontend && pnpm exec vitest run src/pages/waiter` → FAIL. If a `ResizeObserver is not defined` error appears once `DndContext` mounts in jsdom, add at the top of `Tables.test.tsx`: `vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })`.

- [ ] **Step 4: Implement**

`linkDrop.ts`:

```ts
// A free table dropped on an occupied table becomes a link request; anything else is not a merge.
export const resolveLinkDrop = (
  activeId: string,
  over: { id: string; sessionId?: string } | null,
): { sessionId: string; tableId: string } | null => {
  if (!over || over.id === activeId || !over.sessionId) return null
  return { sessionId: over.sessionId, tableId: activeId }
}
```

`TableCard.tsx` — add DnD (keep the Task 8 markup; wrap it):

```tsx
import { useDndContext, useDraggable, useDroppable } from '@dnd-kit/core'
// props: add  draggable: boolean  (free table, caja usable)  and  dropTarget: boolean  (occupied primary)
```
Inside the component:

```tsx
  const { active } = useDndContext()
  const drag = useDraggable({ id: table.tableId!, disabled: !draggable })
  const drop = useDroppable({
    id: table.tableId!,
    disabled: !dropTarget,
    data: { sessionId: table.currentSession?.sessionId },
  })
  const dragging = active != null
  const isBeingDragged = active?.id === table.tableId
  const dragProps = draggable ? { ...drag.attributes, ...drag.listeners } : {}
```
Apply on the `<Card>`: `ref={(node) => { drag.setNodeRef(node); drop.setNodeRef(node) }}`, `{...dragProps}`, and extend `className` with:

```tsx
        ${draggable ? 'touch-manipulation select-none' : ''}
        ${isBeingDragged ? 'opacity-40' : ''}
        ${dragging && !isBeingDragged && !dropTarget ? 'opacity-50' : ''}
        ${dragging && dropTarget ? 'motion-safe:animate-pulse ring-2 ring-[#8c1717]' : ''}
        ${drop.isOver ? 'ring-4 ring-[#8c1717]' : ''}
```
The `Card` forwards `ref`? If the shadcn `Card` is a plain function component without `ref` forwarding in this repo's React 19 setup, React 19 passes `ref` as a prop and a function component that spreads `...props` onto its root `div` receives it — verify with the draggable test; if the ref does not attach, wrap the card in a `<div ref=… {...dragProps}>` instead and keep the `Card` untouched.

`Tables.tsx`:
1. Imports: `DndContext, DragOverlay, MouseSensor, TouchSensor, useSensor, useSensors, type DragEndEvent, type DragStartEvent` from `@dnd-kit/core`; `resolveLinkDrop`.
2. Hooks (next to the other hooks):

```tsx
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 350, tolerance: 8 } }),
  )
  const [draggingId, setDraggingId] = useState<string | null>(null)
  const linkMutation = useMutation({
    mutationFn: ({ sessionId, tableId }: { sessionId: string; tableId: string }) =>
      SessionTableService.linkTable(sessionId, tableId),
    onSuccess: (_data, vars) => {
      const linked = dashboardData?.find((table) => table.tableId === vars.tableId)
      toast.success(t('linkSuccessToast', { table: linked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
    onError: () => {
      toast.error(t('linkErrorToast'))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
  })

  const handleDragStart = (event: DragStartEvent) => setDraggingId(String(event.active.id))
  const handleDragEnd = (event: DragEndEvent) => {
    setDraggingId(null)
    const request = resolveLinkDrop(
      String(event.active.id),
      event.over ? { id: String(event.over.id), sessionId: event.over.data.current?.sessionId } : null,
    )
    if (request) linkMutation.mutate(request)
  }
  const draggingTable = dashboardData?.find((table) => table.tableId === draggingId)
```
3. Wrap the table grid `<div id="waiter-tour-grid" …>` in `<DndContext sensors={sensors} onDragStart={handleDragStart} onDragEnd={handleDragEnd} onDragCancel={() => setDraggingId(null)}> … </DndContext>`, and pass to each card `draggable={canBrowse && isCajaOpen && !table.isOccupied}` and `dropTarget={!!table.isOccupied && !table.linkedToTableId && !!table.currentSession?.sessionId}`.
4. Inside the `DndContext`, after the grid add:

```tsx
        <DragOverlay>
          {draggingTable ? (
            <div className="rounded-2xl bg-[#8c1717] px-5 py-3 text-2xl font-bold text-white shadow-lg">
              M{draggingTable.tableNumber}
            </div>
          ) : null}
        </DragOverlay>
```
(Keep `DragOverlay` a child of `DndContext`; place the grid `div` and the overlay in a fragment.)

- [ ] **Step 5: Run** — `cd frontend && pnpm exec vitest run src/pages/waiter && pnpm run build && pnpm run lint` → PASS.

---

### Task 10: Full verification, manual device check, report, PROGRESS, single commit

**Files:**
- Create: `reports/661-table-merge-link-tables-to-open-session.md`
- Modify: `PROGRESS.md`
- Modify: `docs/superpowers/specs/2026-10-01-table-merge-design.md` (only if implementation deviated again)

- [ ] **Step 1: Full verification (evidence before any "done")**

Run, in order, and record the real output counts:
1. `cd backend && ./mvnw test` — expect all green except the two documented MinIO tests that pass alone (re-run them alone: `./mvnw test -Dtest=<those two classes>`).
2. `cd frontend && pnpm run test:run` — expect all green except the documented load-dependent `MenuJoin` flake (re-run alone).
3. `cd frontend && pnpm run build && pnpm run lint` — 0 errors.
4. `git status` — confirm only this feature's files changed plus the pre-existing unrelated ones.

- [ ] **Step 2: Manual check on a real device** (cannot be automated; do it before claiming the DnD works). Run `./mvnw spring-boot:run` and `pnpm run dev`, apply the two `ALTER TABLE` statements to the local dev DB if not done, then on a phone/tablet:
   1. Open a session on M3; long-press (≈350 ms) free M4 and drag onto M3 → M4 turns occupied, labelled "Unida a M3"; page scroll still works when you swipe without holding.
   2. Occupied tables pulse only while dragging; nothing moves at rest; with OS "reduce motion" on, targets highlight without pulsing.
   3. "Unir mesa" button path works; "Separar M4" frees M4.
   4. Seat a second party on M4 while linked → rejected; from a second browser try linking the same free table to two sessions at once → one wins, the other gets the error toast and a refreshed floor.
   5. Send a dish from the merged table → KDS card and printed ticket read `M3+M4 - Unidas`; unlink → next ticket reads `Mesa 3`.
   6. Close the session → M3 and M4 both free. Analytics table row shows "Fusionada con M4"; revenue totals unchanged.
   Write down anything that did not behave; do not claim success for a step you did not run.

- [ ] **Step 3: Report** — `reports/661-table-merge-link-tables-to-open-session.md` with the five required sections: (1) Identification: report 661, task `EMB-TABLE-MERGE`, predecessor `RELEASE-V0.3.4` (report 660); (2) Objective; (3) Modified Files (exact paths from `git status`); (4) What Changed?; (5) Why It Changed? (incl. the race fix rationale, the "Mesa individual vs M3+M4 - Unidas" kitchen decision, revenue never split, `@dnd-kit/core` approval 2026-10-01). State plainly what was verified and what was not (device check result).

- [ ] **Step 4: PROGRESS.md** — keep the 3-section schema and the file under 180 lines: set `Last Completed Task` to report 661 EMB-TABLE-MERGE with the verified test counts, `Current Active Task: none`, add one bullet to Active Context ("Table merge: `Session.linkedTables` JSON + `TableOccupancy`/`TableLock`; V24 adds `sessions.linked_tables` and `kitchen_orders.linked_table_numbers`; prod runs V24 automatically on next deploy, never by hand; linked tables get no revenue/turnover of their own"), tick the task in the queue, and compress the oldest line of the "Last Completed" run-on to stay under the limit.

- [ ] **Step 5: One squashed commit, scoped staging**

```bash
git add backend/src/main backend/src/test backend/src/main/resources/db/migration/V24__table_merge.sql \
        frontend/src frontend/package.json frontend/pnpm-lock.yaml \
        reports/661-table-merge-link-tables-to-open-session.md PROGRESS.md \
        docs/superpowers/specs/2026-10-01-table-merge-design.md docs/superpowers/plans/2026-10-01-table-merge.md
git status          # confirm backend/mvnw.cmd, landing/CLAUDE.md, docs/legal/ and the other docs/superpowers files are NOT staged
git commit -m "feat: merge tables into an open session (link/unlink, dnd, kitchen and report labels)"
```
No trailers, no co-author, no signature line. Then end the final message with the reminder to run `/clear`.

---

## Plan-time refinements (already folded back into the spec)

1. **One event instead of two:** `TableLinksChanged` (`type` = `TABLES_LINKED` | `TABLE_UNLINKED`) replaces the spec's `TablesLinked`/`TableUnlinked` — one handler per listener, same full-list semantics.
2. **Lock mechanism:** `TableLock` (programmatic `TransactionTemplate` + `findByIdForUpdate`) instead of `@Transactional` on the service methods, so events are published after the commit, outside the lock; precedent: `HubProvisioningRunner`.
3. **`KitchenDisplayEntry` unchanged:** each `KitchenOrder` already carries `linkedTableNumbers`, and the frontend renders per order.
4. **`PaymentResponse.tableLabel`** (new nullable field) instead of changing the type of `tableNumber`.
5. **`TableStatusResponse`** carries `linkedTables` (primary), `linkedToTableId`/`linkedToTableNumber` (linked table).
6. **Drag direction fixed:** a free table is dragged onto an occupied primary table (spec §6 updated).
