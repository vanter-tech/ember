# Report 465

## 1. Identification
- **Report Number:** 465
- **Task ID:** KDS-BULK-STATUS-UPDATE Task 2 (bulk endpoint)
- **Predecessor Task:** report 464 (Task 1, `KitchenService.updateItemsStatus`)

## 2. Objective
Expose Task 1's `KitchenService.updateItemsStatus` over HTTP so the frontend can bulk-move several kitchen-order items to a target status in one request, mirroring the existing single-item endpoint's role restrictions exactly.

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/kitchen/dto/UpdateItemsStatusRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/controller/KitchenController.java`
- Modify: `backend/src/test/java/com/vanter/ember/kitchen/controller/KitchenControllerTest.java`

## 4. What Changed?
New record `UpdateItemsStatusRequest(@NotEmpty List<String> itemIds, @NotNull OrderItemStatus status)`, sibling to the existing `UpdateItemStatusRequest`. New endpoint `PATCH /kitchen/orders/{orderId}/items/status` on `KitchenController`, guarded by `@PreAuthorize("hasRole('KITCHEN')")` (same restriction as the single-item endpoint, not the broader `hasAnyRole('KITCHEN','ADMIN')` used by the read endpoints), delegating straight to `kitchenService.updateItemsStatus(orderId, request.itemIds(), request.status())`. The new path (`/orders/{orderId}/items/status`, 3 segments) coexists with the existing `/orders/{orderId}/items/{itemId}/status` (4 segments) without any routing ambiguity — Spring resolves them by segment count/literal match, no extra config needed.

Added 5 controller tests mirroring the existing endpoint's exact coverage pattern: KITCHEN → 200 + body assertion, ADMIN/WAITER/CUSTOMER → 403, unauthenticated → 401.

## 5. Why It Changed?
Directly implements Task 2 of the approved plan. Kept as a separate endpoint (not folded into the existing one) for the same reason Task 1 kept a separate service method: the single-item path's semantics (one step, forward-only) must not change.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=KitchenControllerTest` — **23/23** (18 pre-existing + 5 new).
- `cd backend && ./mvnw test` (full suite) — **1261/1261**, BUILD SUCCESS.
