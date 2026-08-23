# Report 183 — Task MOD-02: Backend CRUD admin de modificadores + wiring en MenuItemResponse

## 1. Identification
- **Report:** 183
- **Task ID:** MOD-02
- **Predecessor Task:** MOD-01 (report 182)

## 2. Objective
Exponer CRUD administrativo de `ModifierGroup`/`ModifierOption` vía REST y propagar los grupos de modificadores activos de cada `MenuItem` a través de `MenuItemResponse`, tanto para el catálogo admin como para el menú digital del cliente.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/ModifierOptionRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/ModifierOptionResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/ModifierGroupRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/ModifierGroupResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/ModifierGroupAssignment.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/service/ModifierGroupService.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/controller/ModifierGroupController.java` (new)
- `backend/src/main/java/com/vanter/ember/catalog/model/dto/MenuItemResponse.java` (modified)
- `backend/src/main/java/com/vanter/ember/catalog/service/MenuItemService.java` (modified)
- `backend/src/main/java/com/vanter/ember/catalog/controller/MenuItemController.java` (modified)
- `backend/src/main/java/com/vanter/ember/catalog/service/MenuService.java` (modified)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` (modified)
- `backend/src/test/java/com/vanter/ember/catalog/service/ModifierGroupServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/catalog/service/MenuItemServiceTest.java` (modified — not in original plan file list, required to fix a new-mock NPE, see §5)
- `frontend/src/lib/backend-types.ts` (regenerated)

## 4. What Changed?
- Added 5 new DTOs under `catalog.model.dto`: `ModifierOptionRequest/Response`, `ModifierGroupRequest/Response`, `ModifierGroupAssignment` (record).
- Added `ModifierGroupService`: `findAll` (admin listing, incl. inactive), `findActiveGroupsForMenuItem` (active groups + active options only), `create`/`update`/`setActive`, `addOption`/`updateOption`/`deactivateOption`, `replaceMenuItemAssignments`. Selection-rule validation (`SINGLE_REQUIRED`→min=max=1, `MULTI_OPTIONAL`→min=0/max=null, `MULTI_LIMITED`→both required, min≤max) throws `IllegalArgumentException` (409 via existing `GlobalExceptionHandler`).
- Added `ModifierGroupController` at `/catalog/modifier-groups` (route-prefix correction vs. spec's literal `/admin/...` text, matching the real `CategoryController`/`MenuItemController` convention — mutations gated by method-level `@PreAuthorize("hasRole('ADMIN')")`, not a path prefix).
- `MenuItemResponse` gained `modifierGroups: List<ModifierGroupResponse>`; its `from` factory now requires the list explicitly (no zero-arg overload) — all 5 call sites across `MenuItemService`/`MenuService` updated.
- `MenuItemService` gained `assignModifierGroups(id, assignments)`, exposed as `PATCH /catalog/items/{id}/modifier-groups` on `MenuItemController` (ADMIN-only).
- `MenuService.getMenu` (customer digital menu) now populates `modifierGroups` on every item the same way.
- Added 5 rows to `SecurityAuditTest`'s 401 `@CsvSource` matrix for the new routes.
- Added `ModifierGroupServiceTest` (6 cases: selection-rule enforcement per type, active-group/option filtering).
- Regenerated `frontend/src/lib/backend-types.ts` against the live `/v1/v3/api-docs` (new schemas: `ModifierGroupResponse`, `ModifierOptionResponse`, `ModifierGroupRequest`, `ModifierOptionRequest`; `MenuItemResponse` gains `modifierGroups`).

## 5. Why It Changed?
Implements Task 2 (MOD-02) of the approved `docs/superpowers/plans/2026-08-22-emb-mod.md`, the second of 6 EMB-MOD tasks — lets future admin UI (MOD-04) and cart/order capture (MOD-03) work against a real modifier CRUD surface and a `MenuItemResponse` that already carries each item's assigned modifier groups.

`MenuItemServiceTest.java` was not in the plan's file list but required a fix: `MenuItemService`'s new `ModifierGroupService` constructor dependency left `@InjectMocks` with a null mock, causing an NPE in every test that reaches `MenuItemResponse.from(item, modifierGroupService.findActiveGroupsForMenuItem(...))`. Added `@Mock ModifierGroupService modifierGroupService` and a `findActiveGroupsForMenuItem(any()) → List.of()` stub to the 7 affected test methods (the 2 `delete_*` and 2 early-throw tests didn't need it). Same "grep/verify against live code, don't trust a stale plan file list" pattern noted in EMB-i18N-08's report 181.

A stale backend dev process (PID 3468, pre-existing on port 8080, no modifier code) had to be stopped and restarted with the new build to regenerate `backend-types.ts` against the live OpenAPI schema — confirmed with the user before killing it.

## Verification
- `cd backend && ./mvnw test` — full suite, 767/767 passing (incl. new `ModifierGroupServiceTest` and updated `SecurityAuditTest`/`MenuItemServiceTest`).
- `cd frontend && pnpm run build` (`tsc -b && vite build`) — PASS, no compilation errors.
