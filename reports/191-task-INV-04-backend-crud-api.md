# Report 191 — Task INV-04 (backend half): CRUD API at `/catalog/inventory`

## 1. Identification
- **Report number:** 191
- **Task ID:** INV-04 (backend half) — Task 4 of `docs/superpowers/plans/2026-08-22-emb-inv.md`
- **Predecessor Task:** INV-03 frontend half (report 190)

## 2. Objective
Add the admin-facing CRUD REST API for `InventoryItem` at `/catalog/inventory` (list, create/opt-in, update unit/threshold, restock, stop tracking), completing the backend surface EMB-INV's admin UI (Task 5) will consume.

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/inventory/dto/InventoryItemRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/inventory/dto/InventoryItemUpdateRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/inventory/dto/RestockRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/inventory/dto/InventoryItemResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/inventory/controller/InventoryItemController.java`
- Modify: `backend/src/main/java/com/vanter/ember/inventory/service/InventoryService.java`
- Modify: `backend/src/test/java/com/vanter/ember/inventory/service/InventoryServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- Modify (regenerated): `frontend/src/lib/backend-types.ts`

## 4. What Changed?
- Four new request/response DTOs: `InventoryItemRequest` (menuItemId/unit/currentStock/lowStockThreshold, bean-validated, `currentStock`/`lowStockThreshold` `>= 0`), `InventoryItemUpdateRequest` (unit/lowStockThreshold only), `RestockRequest` (delta), and `InventoryItemResponse` (id, menuItemId, menuItemName, menuItemAvailable, unit, currentStock, lowStockThreshold, updatedAt) with a static `from(InventoryItem, MenuItem)` factory.
- `InventoryService` gained `findAll()`, `create(InventoryItemRequest)`, `update(Long, InventoryItemUpdateRequest)`, `restock(Long, BigDecimal)`, `delete(Long)`, and a private `requireMenuItem(Long)` helper. `create` rejects a `menuItemId` that already has an `InventoryItem` row (`IllegalArgumentException` → 409) and, after saving, routes through the existing `applyStockSideEffects` so a zero-stock initial value immediately auto-86s the linked `MenuItem`. `restock` reuses the existing `applyDelta` (from Task 2) unchanged.
- New `InventoryItemController` at `/catalog/inventory`, class-level `@PreAuthorize("hasRole('ADMIN')")`: `GET` (list), `POST` (create, 201), `PATCH /{id}` (update), `POST /{id}/restock`, `DELETE /{id}` (204).
- `SecurityAuditTest`'s `@CsvSource` gained the 5 new routes (401-without-auth matrix).
- `InventoryServiceTest` gained 3 tests: `create_menuItemAlreadyTracked_throws`, `create_initialStockZero_autoDisablesMenuItem`, `update_replacesUnitAndThreshold`.
- Regenerated `frontend/src/lib/backend-types.ts` against the live backend (`pnpm run openapi`) so `InventoryItemResponse`/`InventoryItemRequest`/`InventoryItemUpdateRequest`/`RestockRequest` schemas are available for Task 5's admin UI.

## 5. Why It Changed?
Per the EMB-INV plan/spec, admins need a way to opt menu items into stock tracking, edit tracking parameters, and manually restock/untrack — this is the last backend piece before the admin UI (Task 5) can be built. The single class-level `@PreAuthorize` (vs. per-method) mirrors `AnalyticsController`'s convention and is safe here because, unlike `ModifierGroupController`, no inventory endpoint has a legitimate non-admin caller (documented as a deliberate deviation from the spec's literal endpoint table in the plan's header). `create`'s duplicate-tracking guard prevents two `InventoryItem` rows silently racing on the same `menuItemId` (the DB has a `UNIQUE` constraint from Task 1, but a friendly 409 beats an unhandled constraint-violation 500). Routing `create`'s zero-stock case through `applyStockSideEffects` avoids duplicating the auto-86 check at a second call site, per the plan's explicit "one shared method" design note from Task 2.

## Verification
- `cd backend && ./mvnw test` → **790/790 PASS** (backend, full suite, includes the new CRUD service tests and the extended `SecurityAuditTest`).
- `cd backend && ./mvnw spring-boot:run` (background) + `cd frontend && pnpm run openapi` → `backend-types.ts` regenerated, confirmed to contain `InventoryItemResponse`/`InventoryItemRequest`/`InventoryItemUpdateRequest`/`RestockRequest`.
- Frontend `pnpm run build` not run this task — no frontend source changed beyond the regenerated types file, and Task 5 (which consumes these types) is still queued.
