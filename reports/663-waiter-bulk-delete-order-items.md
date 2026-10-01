# Report 663 — WAITER-BULK-DELETE-ORDER-ITEMS

## 1. Identification
- Report: 663
- Task ID: WAITER-BULK-DELETE-ORDER-ITEMS
- Predecessor: 662 (EMB-TABLE-MERGE-ANIMATION)

## 2. Objective
In the waiter's individual table view, add a "Seleccionar todos" control (plus per-item checkboxes) so the waiter can delete the not-yet-prepared dishes in one confirmation instead of clicking trash + confirm on every row. Dishes already being prepared stay undeletable, exactly as today.

## 3. Modified Files
- Backend: `backend/src/main/java/com/vanter/ember/session/dto/RemoveItemsRequest.java` (new), `.../session/service/SessionService.java`, `.../session/controller/SessionController.java`, tests `.../session/service/SessionServiceTest.java`, `.../session/controller/SessionControllerTest.java`
- Frontend: `frontend/src/pages/waiter/components/BulkDeleteItemsModal.tsx` (new), `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/lib/api.ts`, `frontend/src/locales/{es,en}/waiter.ts`, test `frontend/src/pages/waiter/TableInformation.bulkdelete.test.tsx` (new)
- Docs: `PROGRESS.md`, this report.

## 4. What Changed?
- **Endpoint.** `POST /sessions/{id}/items/bulk-delete` `{itemIds}` (WAITER only; validation `@NotEmpty`, non-blank ids) returns the session detail. `POST` rather than `DELETE`-with-body because some proxies (Cloudflare among them) can drop a `DELETE` body.
- **`SessionService.removeItems`.** All or nothing: only the session's assigned waiter, session must be OPEN, ids de-duplicated, every id validated before anything is touched — unknown id → 404, an item PREPARING/READY/DELIVERED → 409 naming the dish ("…already been sent to the kitchen"). Then one `save`, one `ITEM_DELETED` activity entry per dish, and one `DeleteItem` event per dish (published after the save) so the KDS and the rest behave as with the single delete. Needed because `Session` is `@Version`ed: N parallel single deletes from the client would conflict.
- **UI (`TableInformation`).** Header of "Detalles de pedidos": "Seleccionar todos" / "Deseleccionar todos" and, when something is picked, "Eliminar seleccionados (N)". A checkbox per row; items already sent to the kitchen have it disabled (same tooltip as the trash button) and "select all" never picks them. The selection is intersected with the live list, so a stale selection cannot include an item that moved to the kitchen. One confirmation dialog ("¿Eliminar N platos?"). On success: invalidate, toast, clear selection. On error (409 gets its own message "Un plato ya pasó a cocina, así que no se eliminó nada…") the list is refreshed and the selection cleared. Controls are disabled when the session is not OPEN. The per-row trash button is unchanged.

## 5. Why It Changed?
Repeatedly clicking trash + confirm for each dish is slow when a table needs to be cleared. A single batch is also the only safe way with the optimistic lock on `Session`. All-or-nothing keeps the order from ending half-deleted if the kitchen picks up a dish between selecting and confirming. The rule that preparing dishes cannot be removed is deliberately untouched.

### Verification
- Backend full `./mvnw test`: **1756/1756**. New: 9 service tests (removes all + activity + one event per item; whole batch rejected if one is in the kitchen; PREPARING/READY/DELIVERED never removed; only the assigned waiter; unknown id; repeated ids count once; empty list and closed session) and 3 controller tests (200 + service call, 400 empty/missing, 403 customer).
- Frontend: `TableInformation.bulkdelete.test.tsx` 7/7 (select-all picks only deletable, deselect, one-by-one, confirm → ONE request and selection cleared, cancel deletes nothing, 409 message + refresh, closed session disabled). Full `pnpm run test:run` 319/320 (the `MenuJoin` flake listed in `PROGRESS.md`), `pnpm run build` exit 0, `pnpm run lint` 0 errors.
- The user tried the feature and reported it works.

### NOT verified
- Layout of the header with both buttons on very narrow screens was not inspected by me.
- The 409 path (a dish reaching the kitchen during the batch) is covered by unit tests only, not by a live race.
