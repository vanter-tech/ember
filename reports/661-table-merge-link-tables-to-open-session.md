# Report 661 — EMB-TABLE-MERGE

## 1. Identification
- Report: 661
- Task ID: EMB-TABLE-MERGE (plan `docs/superpowers/plans/2026-10-01-table-merge.md`, spec `docs/superpowers/specs/2026-10-01-table-merge-design.md`)
- Predecessor: 660 (RELEASE-V0.3.4)

## 2. Objective
When a large party pushes several physical tables together, Ember showed the extra tables as free and let another party be seated on them. A waiter can now attach a free table to an already-open session (button or long-press drag-and-drop), every joined table shows as occupied until the session closes, the kitchen sees `M3+M4 - Unidas`, and reports show the merge without changing any total.

## 3. Modified Files
Backend (`backend/src/main/java/com/vanter/ember/…`):
- New: `session/model/LinkedTable.java`, `session/model/TableLabels.java`, `session/service/TableOccupancy.java`, `session/service/TableLock.java`, `session/event/TableLinksChanged.java`, `session/dto/LinkTableRequest.java`, `session/dto/LinkedTableSummary.java`, `resources/db/migration/V24__table_merge.sql`
- Modified: `session/model/Session.java`, `session/model/SessionActivity.java`, `session/repository/SessionRepository.java`, `settings/repository/DiningTableRepository.java`, `session/service/SessionService.java`, `session/service/DashboardService.java`, `session/controller/SessionController.java`, `session/dto/TableStatusResponse.java`, `session/event/KitchenItemsConfirmed.java`, `session/listener/WaiterWebSocketListener.java`, `kitchen/model/KitchenOrder.java`, `kitchen/service/KitchenService.java`, `kitchen/listener/KitchenWebSocketListener.java`, `printing/service/KitchenTicketPrintService.java`, `printing/listener/PrintingEventListener.java`, `printing/service/ReceiptLayout.java`, `printing/service/ReceiptRenderer.java`, `export/service/ExportService.java`, `billing/dto/PaymentResponse.java`, `billing/service/PaymentService.java`, `analytics/dto/TablePerformance.java`, `analytics/service/AnalyticsService.java`
- Tests: new `TableLabelsTest`, `TableOccupancyTest`, `TableLockTest`, `DashboardServiceTest`, `TableLinkConcurrencyIntegrationTest`; extended `SessionServiceTest` (existing `createSession` tests migrated to the lock), `SessionControllerTest`, `SessionRepositoryTest`, `WaiterWebSocketListenerTest`, `KitchenServiceTest`, `KitchenWebSocketListenerTest`, `KitchenOrderRepositoryTest`, `KitchenTicketPrintServiceTest`, `PrintingEventListenerTest`, `ReceiptRendererTest`, `ExportServiceTest`, `PaymentServiceTest`, `AnalyticsServiceTest`; constructor fixes in `CashShiftServiceTest`, `BillingControllerTest`, `AnalyticsControllerTest`

Frontend (`frontend/…`):
- New: `src/pages/waiter/components/TableCard.tsx`, `src/pages/waiter/components/LinkTableModal.tsx`, `src/pages/waiter/lib/linkDrop.ts` (+ `linkDrop.test.ts`), `src/pages/kitchen/lib/mergedTableLabel.ts` (+ test), `src/pages/admin/analytics/components/TableAnalytics.test.tsx`
- Modified: `package.json`, `pnpm-lock.yaml`, `src/lib/api.ts`, `src/lib/backend-types.ts` (hand-edited), `src/store/uiStore.ts`, `src/locales/{es,en}/{waiter,kitchen,admin}.ts`, `src/pages/waiter/Tables.tsx` (+ `Tables.test.tsx`), `src/pages/kitchen/components/{QueueCard,FocusedCard}.tsx` (+ `FocusedCard.test.tsx`), `src/pages/admin/analytics/components/TableAnalytics.tsx`, `src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`

Docs: `PROGRESS.md`, `docs/superpowers/specs/2026-10-01-table-merge-design.md`, `docs/superpowers/plans/2026-10-01-table-merge.md`, this report.

## 4. What Changed?
- **Model.** `Session.tableId` stays the primary table; `Session.linkedTables` (JSON list of `{tableId, tableNumber, linkedAt}`) holds the attached tables. `V24__table_merge.sql` adds `sessions.linked_tables` and `kitchen_orders.linked_table_numbers` (both `jsonb NOT NULL DEFAULT '[]'`, idempotent).
- **Occupancy.** One pure definition, `TableOccupancy.byTable(openSessions)`, used by `DashboardService`, `createSession` and `linkTable`. The dashboard returns linked tables as occupied with the owning session, plus `linkedToTableId`/`linkedToTableNumber`; the primary returns `linkedTables`.
- **Race fix.** `TableLock` runs a short programmatic transaction that takes `PESSIMISTIC_WRITE` on the target `DiningTables` row before the occupancy check; events are published after the commit. `createSession` and `linkTable` both go through it, which also closes the pre-existing race of two waiters seating the same free table. `TableLinkConcurrencyIntegrationTest` uses real threads; with the lock disabled both race tests fail (mutation-checked).
- **Rules.** Merge only into an OPEN session, by its assigned waiter; free+free and occupied+occupied are rejected; inactive, occupied, own-primary and already-linked targets are rejected. `POST /sessions/{id}/linked-tables` and `DELETE /sessions/{id}/linked-tables/{tableId}` (WAITER only, return the session detail).
- **Kitchen.** `KitchenItemsConfirmed` carries `linkedTableNumbers` (4-arg constructor kept); `KitchenOrder` is born with them and kept in sync by a `TableLinksChanged` listener (full list, overwrite), which also pushes a refresh on `/topic/kitchen/{tenant}`. Tickets (manual reprint and auto-print) and the KDS show `Mesa 5` for an individual table and `M5+M6 - Unidas` when merged.
- **Reports.** Revenue, turnover and duration stay on the primary table, never split. Receipt line, Excel Ventas "Mesa" cell (text `M7+M8` only when merged) and `PaymentResponse.tableLabel` use the joined label; `TablePerformance.mergedWithTableNumbers` drives "Fusionada con M4, M5" in the analytics table.
- **Frontend.** `TableCard` (linked tables occupied, "Unida a M3"), `LinkTableModal` ("Unir mesa" free-table picker), "Separar" per linked table, and drag-and-drop with `@dnd-kit/core` 6.3.1: a free table is dragged onto an occupied primary table; mouse activates after 8 px, touch after a 350 ms hold (tolerance 8 px) so swiping still scrolls; nothing animates at rest, occupied targets pulse only while dragging and only under `motion-safe`. Link/unlink UI and dragging are WAITER-only.

## 5. Why It Changed?
Physical tables joined for a big party showed as free, so a second party could be seated on them. Modelling the merge as a list on the session (instead of a new "virtual table" entity) keeps bill, kitchen, analytics and printing on one key. The row lock is needed because `Session.@Version` cannot stop two writes landing on two different sessions. The kitchen label keeps the physical tables identifiable and unambiguous when several merged groups run at once. Revenue is never split because that would invent numbers; the cost is that a linked table gets no turnover of its own in analytics (accepted in the spec).

### Verification (final state)
- Backend `./mvnw test`: **1744/1744**, 0 failures (the two known MinIO tests did not flake this run).
- Frontend `pnpm run test:run`: **305/305** (79 files). `pnpm run build` exit 0. `pnpm run lint` 0 errors (15 pre-existing warnings).
- `pnpm audit --prod`: no known vulnerabilities. Adding `@dnd-kit/core` made pnpm also drop `supports-color` peer suffixes from many lockfile keys; no package versions changed.
- Review pass by the author (no fresh reviewer): found that an ADMIN could browse the floor and be offered "Unir mesa"/dragging while the endpoints are WAITER-only (403). Fixed with a test that failed first (`an ADMIN is not offered to link or unlink tables, nor to drag them`), then 60/60 waiter tests.

### NOT verified
- **Real device.** The long-press drag on a phone/tablet, the drag-time pulse, the reduced-motion behaviour, the two-browser race, and the end-to-end kitchen/ticket/analytics flow against a running backend were NOT exercised: no dev server was running here and jsdom cannot simulate the long-press. The checklist is in plan Task 10 step 2 and in `PROGRESS.md`.
- `V24` was only exercised on H2 (tests use generated schema); it has not been run against Postgres. Local dev Postgres needs the two `ALTER TABLE` statements by hand; prod runs it automatically on the next deploy.

### Deferred minors
- A linked table gets no turnover/revenue of its own (M4's rotation is understated, M3's inflated) — by design, revisit with per-table occupancy time (`linkedAt` is already captured).
- The `Separar`/`Unir mesa` flows do not show the server's specific rejection reason, only a generic toast, then refresh the floor.
