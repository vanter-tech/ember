# EMB-TABLE-MERGE — Fusionar mesas a una sesión abierta — Design Spec

Date: 2026-10-01. Status: design agreed in brainstorming, pending written-spec review.

## 1. Problem

Ember models a physical table as a `DiningTables` row and "occupied" as *an OPEN `Session` exists whose `tableId` equals the table*. When a large party pushes several physical tables together, only the table that got the session shows as occupied. The others show **free** in the floor view and `SessionService.createSession` happily seats another party on them.

## 2. Decisions (from brainstorming)

| Topic | Decision |
|---|---|
| Model | A session keeps one **primary table** (`Session.tableId`, unchanged) plus a list of **linked tables**. No new "virtual merged table" entity. |
| When a merge can happen | Only into an **existing OPEN session**. Two free tables cannot be merged, and two occupied tables cannot be merged (that would be merging two bills — out of scope). |
| Primary table | Always the table that opened the session. Linked tables are attached afterwards and never become primary. |
| Attribution | Revenue, turnover, duration, bill and kitchen flow stay on the **primary table**. Revenue is never split across tables. |
| Reporting | Wherever the primary table appears, show that it was merged: "Fusionada con M4". |
| Lifecycle | Closing, cancelling or force-closing the session frees the primary and every linked table. A linked table can be unlinked mid-session. |
| Who | The WAITER who owns the session (same rule as `transferTable`). |
| UI | A **"Unir mesa" action** in the table detail panel is the primary path (works on touch). Drag-and-drop is an enhancement on top of the same endpoint. |

## 3. Data model

`sessions` gets one JSON column `linked_tables` (default `[]`), mapped with `@JdbcTypeCode(SqlTypes.JSON)` like `participants`/`items`:

```
LinkedTable { UUID tableId; int tableNumber; LocalDateTime linkedAt; }
```

`tableNumber` is denormalised so reports and tickets never need an extra lookup, and so history survives a table being renumbered/deactivated. `linkedAt` costs nothing now and enables per-table "time occupied" later.

**Flyway `V24__session_linked_tables.sql`:** `ALTER TABLE sessions ADD COLUMN IF NOT EXISTS linked_tables jsonb NOT NULL DEFAULT '[]'::jsonb`, and `ALTER TABLE kitchen_orders ADD COLUMN IF NOT EXISTS linked_table_numbers jsonb NOT NULL DEFAULT '[]'::jsonb` — idempotent (prod Flyway is not baselined; never pre-run migration DDL on prod). The column carries no `CHECK`, and `SessionActivity.Type` lives inside JSON, so there is no enum-constraint trap here. Local dev DB is baselined past V15: add the column by hand there.

## 4. Backend

### 4.1 Endpoints (`SessionController`)

- `POST /sessions/{id}/linked-tables` `{tableId}` — `WAITER`. Merge.
- `DELETE /sessions/{id}/linked-tables/{tableId}` — `WAITER`. Unmerge.

Both delegate to `SessionService`, modelled on `transferTable`: caller must be `session.getWaiterId()`, session must be OPEN, an activity-log entry is appended, an event is published.

### 4.2 Merge rules (`SessionService.linkTable`)

Reject with a clear message when:
- the session is not OPEN or the caller is not its waiter;
- the target table is the session's own primary table, or already linked to it;
- the target table does not belong to the tenant or is not active;
- **the target table is occupied** — it has its own OPEN session as primary, **or** is linked to another OPEN session.

"Occupied" must now be computed from both sides, in one place (a helper used by `createSession`, `linkTable` and `DashboardService`): load the tenant's OPEN sessions (a few dozen at most) and build `occupiedTableId -> session` from `tableId` plus each `linkedTables[].tableId`. The check is done in Java, not SQL, for the same reason `findByTenantIdAndParticipants_UserId` is: Postgres JSON containment operators are not portable to the H2 test datasource.

**Concurrency (decided).** Two waiters linking the same free table to two different sessions at once is a check-then-write race that `Session.@Version` cannot close, because the two writes land on two different session rows. Fix: **pessimistic row lock on the target `DiningTables` row**, taken at the start of a short programmatic transaction (`TableLock`, a `TransactionTemplate` precedent exists in `HubProvisioningRunner`) so events are published after the commit, outside the lock, *before* the occupancy check:

1. `linkTable` and `createSession` both call a new `DiningTableRepository` finder annotated `@Lock(LockModeType.PESSIMISTIC_WRITE)` for the target table (same pattern as `BillRepository`/`CashShiftRepository`/`PaymentRepository`).
2. The second transaction blocks on that row until the first commits, then runs the occupancy helper, now sees the table as linked/occupied, and fails with "M4 ya está ocupada o unida a otra mesa". The UI refreshes the floor view on that error.
3. Each operation locks exactly one row, so there is no lock-ordering/deadlock risk. Concurrent edits to the *session* itself still fall under `@Version` and map to the existing 409 handling.
4. Side benefit: this also closes the pre-existing identical race in `createSession` (two waiters seating the same free table).

Rejected: moving linked tables to a join table with a partial unique index. It gives a DB-level guarantee for link-vs-link but not for link-vs-primary, and it replaces the simple JSON column with more schema for little gain.

Required test: two threads link the same free table to two different open sessions; exactly one succeeds. Same for link vs `createSession` on that table.

### 4.3 Other changes

- `createSession`: use the shared occupancy helper, so a table linked to an open session cannot be seated.
- `DashboardService.getLiveStatus`: linked tables are returned as `isOccupied = true` with the owning session summary (so the card can open it) plus `linkedToTableId`/`linkedToTableNumber`. The primary table additionally returns `linkedTables` (`[{tableId, tableNumber}]`). `TableStatusResponse` gains those three optional fields.
- Close paths (`CLOSED` at the three existing sites, cancel, force-close): no change needed — occupancy is derived from OPEN sessions, so everything frees automatically. Verify with tests.
- Events: one `TableLinksChanged` record (`type` = `TABLES_LINKED` | `TABLE_UNLINKED`, full linked list inside) published through `ApplicationEventPublisher` (no Kafka) so the floor view refreshes over the existing WebSocket path.

## 5. Reporting

Principle: **totals never change**; the merge is added as information on the primary table.

- **Analytics, table performance:** `TablePerformance` gains `mergedWithTableNumbers: List<Integer>` — the distinct linked table numbers over all PAID-bill sessions of that primary table in the window. `TableAnalytics.tsx` renders "Fusionada con M4" (or "M4, M5") on the row. Linked tables get **no** turnover or revenue of their own in v1. Known, accepted distortion: M4's rotation is understated and M3's capacity-adjusted numbers are inflated.
- **Bill receipt (`ReceiptRenderer`):** table label becomes "M3+M4" when linked tables exist. Totals untouched. (Receipt preview in Settings → Ticket should keep matching.)
- **Excel export (`ExportService`):** table cell shows "3+4"-style label via a text cell when merged; numeric cell otherwise, so existing consumers of the column are unaffected for unmerged sessions.
- **Payments list (`PaymentService`):** `PaymentResponse` gains a nullable `tableLabel` (`M3+M4` when merged) next to the unchanged numeric `tableNumber`; the Corte Z view prefers the label.
- **Cash-drawer events (`CashDrawerEventListener`):** keep the primary table number only (it is a lookup key for the accountant, not a report). Revisit only if it causes confusion.
- **Kitchen (decided):** KDS and kitchen tickets show the plain table name for an individual table ("M3") and **"M3+M4 - Unidas"** when the session has linked tables (primary first, then linked tables in the order they were linked, e.g. "M3+M4+M5 - Unidas"). This keeps the physical tables identifiable and stays unambiguous when several merged groups are active at once, since each group is keyed by its own primary table.
  - `KitchenOrder` snapshots `tableNumber` once, at the session's first confirmation, and there is one `KitchenOrder` per session, so a merge made *after* items were sent would leave the KDS stale. Therefore: `KitchenOrder` gets a `linked_table_numbers` JSON int list (default `[]`, column added in `V24`), set at creation from a new field on `KitchenItemsConfirmed`, and kept in sync by a `KitchenService` listener on `TableLinksChanged`. The event carries the session's full current list of linked table numbers, so the listener just overwrites the field (no incremental add/remove drift). The listener also pushes a refresh on the existing `/topic/kitchen/{tenantId}` so open KDS screens update live.
  - `KitchenDisplayEntry` is unchanged (each `KitchenOrder` in it already carries `linkedTableNumbers`); grouping/sorting still uses the primary `tableNumber`. The frontend KDS card and `KitchenTicketPrintService` (its "Mesa N" line) build the label from the primary number plus the list; an empty list renders exactly as today.
  - The "Unidas" wording is part of the label, so it goes through the existing i18n on the frontend; the printed ticket stays Spanish like the rest of the ticket text.
  - Tickets already printed before the merge are not reprinted; only new tickets and the live KDS reflect it.

## 6. Frontend (`pages/waiter/Tables.tsx` and friends)

- Floor grid: a linked table renders as occupied, visually tied to its primary (same colour, label "unida a M3", no avatar/participant count of its own). Selecting a linked table selects/opens the primary session.
- Detail panel of an occupied table: **"Unir mesa"** button opens a picker listing only free tables; confirming calls `POST /sessions/{id}/linked-tables`. Linked tables are listed with an "Separar" action.
- Drag-and-drop, same endpoint as the button. Direction is fixed to keep the gesture unambiguous: **a free table is dragged onto an occupied one**. Dropping on a free or already-linked table is simply not a valid target (no merge, no error dialog).
  - **Activation:** long-press. Touch: ~350 ms hold with a small movement tolerance, so normal scrolling of the grid on a phone still works. Mouse: a short drag distance (~8 px) instead of a hold, which feels natural on desktop and still prevents accidental drags from clicks.
  - **Feedback:** nothing animates at rest. Once a drag starts, valid targets (occupied tables) pulse using the same pulsing-border treatment already used for the selected table, and invalid tables dim; a drag overlay follows the pointer. Animations are `motion-safe` only (`prefers-reduced-motion` gets a static highlight). A permanent shake/pulse on every table was considered and rejected: constant motion on the main working screen is distracting, drains phone batteries, and stops signalling anything once everything moves.
  - The "Unir mesa" button remains for keyboard/accessibility and as the discoverable path.
  - **Library: `@dnd-kit/core` 6.3.1 (stable), approved by the user 2026-10-01** (new dependency, CLAUDE.md). MIT, ~31M weekly downloads, no install scripts, three tiny dependencies (`tslib`, `@dnd-kit/accessibility`, `@dnd-kit/utilities`), peer `react >=16.8` so React 19 installs cleanly. Only `useDraggable`/`useDroppable`/`DragOverlay` and activation constraints are needed; `@dnd-kit/sortable` is not. Caveats: single maintainer and last published Dec 2024 (stable/maintenance mode); the newer `@dnd-kit/react` 0.5.0 is pre-1.0 and rejected for API instability. Plan must include `pnpm audit --prod` after install and a live check on a real phone/tablet. Without approval, v1 ships with the button flow only and DnD follows.
- i18n keys for ES/EN under the `waiter` namespace. Waiter mobile layout must keep working (reports 594/589).
- Customer-facing views are not changed.

## 7. Testing

- Backend: `linkTable` happy path; every rejection in 4.2; linked table cannot be seated by `createSession`; closing/cancelling/force-closing frees linked tables; dashboard shows linked tables occupied; analytics/receipt/export/payments label; `@DataJpaTest` classes keep `@Import(TenantIdentifierResolver.class)`; concurrency test for the double-link race per the decision in 4.2.
- Frontend: Tables grid with a linked table; merge and unmerge flows; free-on-free rejection message; analytics row text. `pnpm run build`/`lint` clean.

## 8. Out of scope

- Merging two OPEN sessions/bills.
- Merging free tables without a session ("pre-merge").
- Splitting revenue or rotation across tables; per-table occupancy-time reporting (data is captured via `linkedAt`, reporting is future work).
- Persisting a "standing" table arrangement (e.g. M3+M4 always together).

## 9. Open items for the plan phase

None. `@dnd-kit/core` 6.3.1 approved; long-press activation and drag-time-only animation decided.

Resolved in review: race protection (pessimistic lock on the target table row, 4.2) and kitchen label ("M3+M4 - Unidas", 5).
