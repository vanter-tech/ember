# KDS Bulk Status Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. **This project's convention (see `PROGRESS.md`/`CLAUDE.md`): one task per context window, `/clear` between tasks, and a sequential numbered report in `/reports/` per completed task** — follow that cadence regardless of which sub-skill executes the tasks.

**Goal:** Let kitchen staff select one, several, or all dishes on the currently-focused ticket and jump them all straight to a chosen status via a dropdown, instead of clicking through `Pendiente → Preparando → Listo → Entregado` one step at a time per dish — while keeping every intermediate step in the persisted history and leaving the existing one-click-per-step buttons (and the smaller queue cards) completely unchanged.

**Architecture:** One new backend method (`KitchenService.updateItemsStatus`) walks each selected item, one status at a time (forward *or* backward — `OrderItemStatus`'s declared order is `DRAFT, PENDING, PREPARING, READY, DELIVERED`, so "one step" is just `ordinal() ± 1`), until it reaches the chosen target, publishing the same `KitchenItemUpdated` event per step the single-item flow already does. One new endpoint (`PATCH /kitchen/orders/{orderId}/items/status`) exposes it, kept entirely separate from the existing `PATCH /kitchen/orders/{orderId}/items/{itemId}/status` (which keeps its current one-step-only validation, unchanged). On the frontend, `FocusedCard.tsx` (the large "next ticket to work on" card — `QueueCard.tsx`'s smaller per-table cards are explicitly out of scope) gains a per-item circular checkbox, a "select all" toggle, and a status dropdown that appears once at least one item is checked; picking a value fires the bulk call immediately (no separate "apply" button) and clears the selection on success.

**Tech Stack:** Spring Boot (Java 17) backend, existing `OrderItemStatus` enum + `KitchenOrder`/`KitchenItem` JSON-column model; React 19 + TanStack Query + shadcn/ui (`Checkbox`, `Select`) + Tailwind on the frontend; WebSocket/STOMP already broadcasts every `KitchenItemUpdated`/`KitchenOrderRetired` event to `/topic/kitchen/{tenantId}`, where the KDS just invalidates its `kitchenOrders` query — no changes needed there, firing more events of the same shape is already handled.

**Spec:** No standalone spec doc — the requirements were hashed out directly with the user in conversation (recorded in this session's history) rather than a separate design doc, given the feature's scope fit in one sitting. This plan's Global Constraints section below captures every decision that would otherwise live in a spec.

## Global Constraints

- The existing single-item endpoint (`PATCH /kitchen/orders/{orderId}/items/{itemId}/status`, `KitchenService.updateItemStatus`, `isValidTransition`) is **not modified in any way** — same validation, same behavior, same tests untouched. The new bulk capability is a fully separate method/endpoint.
- `QueueCard.tsx` (the smaller per-table queue cards) is **out of scope** — bulk selection only applies to `FocusedCard.tsx`, the one large "next ticket" card.
- The bulk dropdown never offers `DRAFT` as a target — only the four states the KDS itself shows: `PENDING`, `PREPARING`, `READY`, `DELIVERED`. `DRAFT` is a pre-kitchen state (items enter the kitchen model already `PENDING`) and was never meant to be kitchen-facing.
- `DELIVERED` items are already filtered out of `FocusedCard`'s rendered list (`order.items?.filter((item) => item.status !== 'DELIVERED')`, unchanged) — so bulk-selecting "Entregado" makes the chosen dishes disappear from the card immediately after, same as the individual "Entregar" button already does today; this plan does not change that filter.
- No new npm/pnpm packages — `Checkbox` (`frontend/src/components/ui/checkbox.tsx`) and `Select` (`frontend/src/components/ui/select.tsx`) already exist and are used elsewhere in the codebase.
- Every new i18n key goes in `frontend/src/locales/es/kitchen.ts` first, then `frontend/src/locales/en/kitchen.ts` — the `en` file's `satisfies typeof esKitchen` fails `tsc -b` if the two ever drift, so there's no separate parity script to remember.
- Follow CLAUDE.md's report/commit convention: one task per context, a sequential `/reports/NN-...md` per completed task, `PROGRESS.md` updated, exactly one squashed commit per task.

---

## Task 1: Backend — `KitchenService.updateItemsStatus`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- Test: `backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java`

**Interfaces:**
- Consumes: `KitchenOrderRepository.findByIdAndTenantId` (existing), `KitchenItem.getStatus()/setStatus()/setUpdatedAt()` (existing), `OrderItemStatus` enum's declared order (existing — `DRAFT, PENDING, PREPARING, READY, DELIVERED`), `KitchenItemUpdated`/`KitchenOrderRetired` events (existing, unchanged shape).
- Produces: `KitchenService.updateItemsStatus(String orderId, List<String> itemIds, OrderItemStatus targetStatus): KitchenOrder` — Task 2's controller calls this exact signature.

- [ ] **Step 1: Write the failing tests**

Add to `KitchenServiceTest.java`, right after the existing `// --- updateItemStatus tests ---` block (after the `updateItemStatus_publishesKitchenItemUpdatedEvent` test, before `// --- handleSessionClosed tests ---`):

```java
    // --- updateItemsStatus (bulk) tests ---

    private KitchenOrder orderWithItems(java.util.Map<String, OrderItemStatus> itemIdToStatus) {
        List<KitchenItem> items = itemIdToStatus.entrySet().stream()
                .map(e -> KitchenItem.builder()
                        .itemId(e.getKey()).name("Tacos").participantName("Alice")
                        .status(e.getValue()).updatedAt(LocalDateTime.now()).build())
                .collect(java.util.stream.Collectors.toList());
        return KitchenOrder.builder()
                .id("ko-1").tenantId(TENANT_ID).sessionId("sess-1").tableNumber(5)
                .active(true).items(items).build();
    }

    @Test
    void updateItemsStatus_walksForwardThroughEveryIntermediateStep() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.PENDING))));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1"), OrderItemStatus.DELIVERED);

        assertThat(result.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.DELIVERED);

        ArgumentCaptor<KitchenItemUpdated> captor = ArgumentCaptor.forClass(KitchenItemUpdated.class);
        verify(eventPublisher, org.mockito.Mockito.times(3)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().stream().map(KitchenItemUpdated::newStatus))
                .containsExactly(OrderItemStatus.PREPARING, OrderItemStatus.READY, OrderItemStatus.DELIVERED);
    }

    @Test
    void updateItemsStatus_walksBackwardThroughEveryIntermediateStep() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.READY))));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1"), OrderItemStatus.PENDING);

        assertThat(result.getItems().get(0).getStatus()).isEqualTo(OrderItemStatus.PENDING);

        ArgumentCaptor<KitchenItemUpdated> captor = ArgumentCaptor.forClass(KitchenItemUpdated.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues().stream().map(KitchenItemUpdated::newStatus))
                .containsExactly(OrderItemStatus.PREPARING, OrderItemStatus.PENDING);
    }

    @Test
    void updateItemsStatus_advancesSeveralItemsIndependentlyFromWhereverTheyAre() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.LinkedHashMap.class.cast(
                        new java.util.LinkedHashMap<>() {{
                            put("order-item-1", OrderItemStatus.PENDING);
                            put("order-item-2", OrderItemStatus.PREPARING);
                        }}))));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1", "order-item-2"), OrderItemStatus.READY);

        assertThat(result.getItems()).extracting(KitchenItem::getStatus)
                .containsExactly(OrderItemStatus.READY, OrderItemStatus.READY);
    }

    @Test
    void updateItemsStatus_isANoOpWhenItemAlreadyAtTargetStatus() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.READY))));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        kitchenService.updateItemsStatus("ko-1", List.of("order-item-1"), OrderItemStatus.READY);

        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(KitchenItemUpdated.class));
    }

    @Test
    void updateItemsStatus_retiresOrderWhenAllItemsReachDelivered() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.READY))));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1"), OrderItemStatus.DELIVERED);

        assertThat(result.isActive()).isFalse();
        verify(eventPublisher).publishEvent(any(KitchenOrderRetired.class));
    }

    @Test
    void updateItemsStatus_reactivatesOrderWhenMovedAwayFromAllDelivered() {
        KitchenOrder order = orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.DELIVERED));
        order.setActive(false);
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID)).thenReturn(Optional.of(order));
        when(kitchenOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitchenOrder result = kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1"), OrderItemStatus.READY);

        assertThat(result.isActive()).isTrue();
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(KitchenOrderRetired.class));
    }

    @Test
    void updateItemsStatus_throwsWhenOrderNotFound() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-999", TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kitchenService.updateItemsStatus(
                "ko-999", List.of("order-item-1"), OrderItemStatus.READY))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemsStatus_throwsWhenAnyItemNotFound() {
        when(kitchenOrderRepository.findByIdAndTenantId("ko-1", TENANT_ID))
                .thenReturn(Optional.of(orderWithItems(java.util.Map.of("order-item-1", OrderItemStatus.PENDING))));

        assertThatThrownBy(() -> kitchenService.updateItemsStatus(
                "ko-1", List.of("order-item-1", "nonexistent-item"), OrderItemStatus.READY))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=KitchenServiceTest`
Expected: compile error — `updateItemsStatus` does not exist yet on `KitchenService`.

- [ ] **Step 3: Implement `updateItemsStatus`**

In `KitchenService.java`, add this method right after `updateItemStatus` (after its closing `}`, before the `handleItemDeleted` method):

```java
    /**
     * Bulk-moves several items on the same order straight to {@code targetStatus} — forward or
     * backward — walking each one through every intermediate {@link OrderItemStatus} one step at
     * a time (never skipping a step in the persisted history/events) instead of requiring one
     * click per step per item. This is what backs the KDS's "select several dishes, pick a
     * status" action; {@link #updateItemStatus} (one click, one step, forward only) is untouched
     * and still the only path the individual per-item buttons use.
     */
    public KitchenOrder updateItemsStatus(String orderId, List<String> itemIds, OrderItemStatus targetStatus) {
        KitchenOrder order = kitchenOrderRepository
                .findByIdAndTenantId(orderId, TenantContextHolder.requireTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Kitchen order not found: " + orderId));

        OrderItemStatus[] allStatuses = OrderItemStatus.values();
        List<KitchenItemUpdated> events = new ArrayList<>();
        for (String itemId : itemIds) {
            KitchenItem item = order.getItems().stream()
                    .filter(i -> itemId.equals(i.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

            int direction = Integer.signum(targetStatus.ordinal() - item.getStatus().ordinal());
            while (item.getStatus() != targetStatus) {
                OrderItemStatus nextStep = allStatuses[item.getStatus().ordinal() + direction];
                item.setStatus(nextStep);
                item.setUpdatedAt(LocalDateTime.now());
                events.add(new KitchenItemUpdated(
                        TenantContextHolder.requireTenantId(), order.getSessionId(), itemId, nextStep));
            }
        }

        boolean allDelivered = order.getItems().stream()
                .allMatch(i -> i.getStatus() == OrderItemStatus.DELIVERED);
        order.setActive(!allDelivered);

        KitchenOrder saved = kitchenOrderRepository.save(order);
        events.forEach(eventPublisher::publishEvent);
        if (allDelivered) {
            eventPublisher.publishEvent(new KitchenOrderRetired(saved.getTenantId(), saved.getSessionId()));
        }
        return saved;
    }
```

Note the `order.setActive(!allDelivered)` (rather than the single-item method's one-way `if (allDelivered) { order.setActive(false); }`) is deliberate here: this method can move an item *backward* out of `DELIVERED`, so unlike the forward-only single-item flow, "no longer all delivered" needs to be able to flip `active` back to `true` too — covered by the `updateItemsStatus_reactivatesOrderWhenMovedAwayFromAllDelivered` test above.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=KitchenServiceTest`
Expected: all tests in the file PASS (17 existing + 8 new = 25).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java backend/src/test/java/com/vanter/ember/kitchen/service/KitchenServiceTest.java
git commit -m "feat(kitchen): add bulk multi-step item status transitions"
```

---

## Task 2: Backend — bulk endpoint (`PATCH /kitchen/orders/{orderId}/items/status`)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/kitchen/dto/UpdateItemsStatusRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/controller/KitchenController.java`
- Test: `backend/src/test/java/com/vanter/ember/kitchen/controller/KitchenControllerTest.java`

**Interfaces:**
- Consumes: `KitchenService.updateItemsStatus(String, List<String>, OrderItemStatus)` from Task 1.
- Produces: `PATCH /kitchen/orders/{orderId}/items/status` with body `{"itemIds": ["..."], "status": "READY"}` → `KitchenOrder` JSON, `hasRole('KITCHEN')` — Task 3's frontend client calls this exact path/body shape.

- [ ] **Step 1: Write the failing tests**

Add to `KitchenControllerTest.java`, right after the existing `// --- PATCH /kitchen/orders/{orderId}/items/{itemId}/status ---` block's last test (`updateItemStatus_unauthenticatedReturns401`), before `// --- GET /kitchen/display ---`:

```java
    // --- PATCH /kitchen/orders/{orderId}/items/status (bulk) ---

    @Test
    @WithMockUser(roles = "KITCHEN")
    void updateItemsStatus_returnsUpdatedOrderForKitchen() throws Exception {
        KitchenOrder updated = sampleOrder();
        updated.getItems().get(0).setStatus(OrderItemStatus.READY);
        when(kitchenService.updateItemsStatus("ko-1", List.of("order-item-1"), OrderItemStatus.READY))
                .thenReturn(updated);

        mockMvc.perform(patch("/kitchen/orders/ko-1/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateItemsStatusRequest(List.of("order-item-1"), OrderItemStatus.READY))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("READY"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateItemsStatus_forbiddenForAdmin() throws Exception {
        mockMvc.perform(patch("/kitchen/orders/ko-1/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateItemsStatusRequest(List.of("order-item-1"), OrderItemStatus.READY))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void updateItemsStatus_forbiddenForWaiter() throws Exception {
        mockMvc.perform(patch("/kitchen/orders/ko-1/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateItemsStatusRequest(List.of("order-item-1"), OrderItemStatus.READY))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void updateItemsStatus_forbiddenForCustomer() throws Exception {
        mockMvc.perform(patch("/kitchen/orders/ko-1/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateItemsStatusRequest(List.of("order-item-1"), OrderItemStatus.READY))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateItemsStatus_unauthenticatedReturns401() throws Exception {
        mockMvc.perform(patch("/kitchen/orders/ko-1/items/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateItemsStatusRequest(List.of("order-item-1"), OrderItemStatus.READY))))
                .andExpect(status().isUnauthorized());
    }
```

Add the import next to the existing `UpdateItemStatusRequest` import at the top of the file:

```java
import com.vanter.ember.kitchen.dto.UpdateItemsStatusRequest;
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=KitchenControllerTest`
Expected: compile error — `UpdateItemsStatusRequest` class and `updateItemsStatus` controller method/mapping don't exist yet.

- [ ] **Step 3: Create the request DTO**

`backend/src/main/java/com/vanter/ember/kitchen/dto/UpdateItemsStatusRequest.java`:

```java
package com.vanter.ember.kitchen.dto;

import com.vanter.ember.session.model.OrderItemStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateItemsStatusRequest(@NotEmpty List<String> itemIds, @NotNull OrderItemStatus status) {}
```

- [ ] **Step 4: Add the controller endpoint**

In `KitchenController.java`, add the import (`import com.vanter.ember.kitchen.dto.UpdateItemsStatusRequest;`, next to the existing `UpdateItemStatusRequest` import), then add this method right after `updateItemStatus`:

```java
    @Operation(summary = "Bulk-update several items to the same status at once (KITCHEN)")
    @PatchMapping("/orders/{orderId}/items/status")
    @PreAuthorize("hasRole('KITCHEN')")
    public KitchenOrder updateItemsStatus(@PathVariable String orderId,
                                           @Valid @RequestBody UpdateItemsStatusRequest request) {
        return kitchenService.updateItemsStatus(orderId, request.itemIds(), request.status());
    }
```

(`/orders/{orderId}/items/status` and the existing `/orders/{orderId}/items/{itemId}/status` are different path-segment counts — Spring's request mapping already disambiguates them without any extra configuration, the same way `/users/{id}` and `/users/me` coexist elsewhere.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=KitchenControllerTest`
Expected: all tests PASS (20 existing + 5 new = 25).

- [ ] **Step 6: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: full suite green, same count as before Task 1 plus the 13 new tests (8 service + 5 controller).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/kitchen/dto/UpdateItemsStatusRequest.java backend/src/main/java/com/vanter/ember/kitchen/controller/KitchenController.java backend/src/test/java/com/vanter/ember/kitchen/controller/KitchenControllerTest.java
git commit -m "feat(kitchen): expose bulk item status endpoint"
```

---

## Task 3: Frontend — API client method + i18n keys

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/locales/es/kitchen.ts`
- Modify: `frontend/src/locales/en/kitchen.ts`

**Interfaces:**
- Consumes: `PATCH /kitchen/orders/{orderId}/items/status` from Task 2.
- Produces: `kitchenServices.updateItemsStatus(orderId: string, itemIds: string[], status: OrderItemStatus): Promise<kitchenOrders>` and 5 new `kitchen` i18n keys — Task 4's `FocusedCard.tsx` calls/uses these exact names.

- [ ] **Step 1: Add the API client method**

In `frontend/src/lib/api.ts`, the `kitchenServices` object currently ends like this (lines 560-568):

```ts
  updateItemStatus: async (orderId: string, itemId: string, status: OrderItemStatus): Promise<kitchenOrders> => {
    const { data } = await api.patch<kitchenOrders>(
      `/kitchen/orders/${orderId}/items/${itemId}/status`,
      { status }
    )
    return data
  }

}
```

Change it to add a trailing comma and the new method:

```ts
  updateItemStatus: async (orderId: string, itemId: string, status: OrderItemStatus): Promise<kitchenOrders> => {
    const { data } = await api.patch<kitchenOrders>(
      `/kitchen/orders/${orderId}/items/${itemId}/status`,
      { status }
    )
    return data
  },
  updateItemsStatus: async (orderId: string, itemIds: string[], status: OrderItemStatus): Promise<kitchenOrders> => {
    const { data } = await api.patch<kitchenOrders>(
      `/kitchen/orders/${orderId}/items/status`,
      { itemIds, status }
    )
    return data
  }

}
```

(No new generated type needed — `itemIds`/`status` are passed as a plain object literal, same style `updatePlan`/other ad-hoc bodies elsewhere in this file already use; `OrderItemStatus` is the existing exported type.)

- [ ] **Step 2: Add the Spanish i18n keys**

In `frontend/src/locales/es/kitchen.ts`, add after `itemStatusUpdateErrorToast`:

```ts
  kdsSelectAll: 'Seleccionar todo',
  kdsDeselectAll: 'Deseleccionar todo',
  kdsBulkStatusPlaceholder: 'Cambiar estado a...',
  kdsSelectItemAriaLabel: 'Seleccionar {{name}}',
```

- [ ] **Step 3: Add the matching English i18n keys**

In `frontend/src/locales/en/kitchen.ts`, add after `itemStatusUpdateErrorToast` (before the closing `} satisfies typeof esKitchen`):

```ts
  kdsSelectAll: 'Select all',
  kdsDeselectAll: 'Deselect all',
  kdsBulkStatusPlaceholder: 'Change status to...',
  kdsSelectItemAriaLabel: 'Select {{name}}',
```

- [ ] **Step 4: Verify the build catches any ES/EN drift**

Run: `cd frontend && pnpm run build`
Expected: clean (the `en/kitchen.ts` file's `satisfies typeof esKitchen` would fail `tsc -b` here first if a key were missing/misspelled in either file).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/locales/es/kitchen.ts frontend/src/locales/en/kitchen.ts
git commit -m "feat(kitchen): add bulk-status API client method and i18n keys"
```

---

## Task 4: Frontend — selection UI + bulk dropdown in `FocusedCard.tsx`

**Files:**
- Modify: `frontend/src/pages/kitchen/components/FocusedCard.tsx`
- Test: Create `frontend/src/pages/kitchen/components/FocusedCard.test.tsx`

**Interfaces:**
- Consumes: `kitchenServices.updateItemsStatus` and the 4 new i18n keys from Task 3; `Checkbox` (`frontend/src/components/ui/checkbox.tsx`), `Select`/`SelectContent`/`SelectItem`/`SelectTrigger`/`SelectValue` (`frontend/src/components/ui/select.tsx`) — both already exist, no new dependency.
- Produces: nothing consumed by a later task — this is the last task of the plan.

- [ ] **Step 1: Write the failing test file**

Create `frontend/src/pages/kitchen/components/FocusedCard.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FocusedCard } from './FocusedCard'
import { kitchenServices, type kitchenOrders } from '@/lib/api'

vi.mock('@/lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/api')>()
  return {
    ...actual,
    kitchenServices: {
      ...actual.kitchenServices,
      updateItemStatus: vi.fn(),
      updateItemsStatus: vi.fn(),
    },
  }
})

const sampleOrder: kitchenOrders = {
  id: 'ko-1',
  sessionId: 'sess-1',
  tableNumber: 5,
  createdAt: '2026-09-13T12:00:00',
  items: [
    { itemId: 'item-1', name: 'Tacos', status: 'PENDING', modifiers: [] },
    { itemId: 'item-2', name: 'Burrito', status: 'PREPARING', modifiers: [] },
  ],
}

const wrap = (order: kitchenOrders) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <FocusedCard order={order} />
    </QueryClientProvider>,
  )

describe('FocusedCard bulk status selection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  test('the status dropdown is hidden until at least one item is selected', () => {
    wrap(sampleOrder)

    expect(screen.queryByText('Cambiar estado a...')).not.toBeInTheDocument()
  })

  test('selecting one item reveals the dropdown; choosing a status bulk-updates just that item', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByLabelText('Seleccionar Tacos'))
    expect(screen.getByText('Cambiar estado a...')).toBeInTheDocument()

    await userEvent.click(screen.getByText('Cambiar estado a...'))
    await userEvent.click(screen.getByText('Listo'))

    await waitFor(() =>
      expect(kitchenServices.updateItemsStatus).toHaveBeenCalledWith('ko-1', ['item-1'], 'READY'),
    )
  })

  test('"Seleccionar todo" selects every visible item, and toggles to "Deseleccionar todo"', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Seleccionar todo'))
    expect(screen.getByText('Deseleccionar todo')).toBeInTheDocument()

    await userEvent.click(screen.getByText('Deseleccionar todo'))
    await waitFor(() => expect(screen.getByText('Seleccionar todo')).toBeInTheDocument())

    await userEvent.click(screen.getByText('Seleccionar todo'))
    await userEvent.click(screen.getByText('Cambiar estado a...'))
    await userEvent.click(screen.getByText('Listo'))

    await waitFor(() =>
      expect(kitchenServices.updateItemsStatus).toHaveBeenCalledWith('ko-1', ['item-1', 'item-2'], 'READY'),
    )
  })

  test('selection clears after a successful bulk update', async () => {
    vi.mocked(kitchenServices.updateItemsStatus).mockResolvedValue(sampleOrder)
    wrap(sampleOrder)

    await userEvent.click(screen.getByText('Seleccionar todo'))
    await userEvent.click(screen.getByText('Cambiar estado a...'))
    await userEvent.click(screen.getByText('Listo'))

    await waitFor(() => expect(screen.queryByText('Cambiar estado a...')).not.toBeInTheDocument())
    expect(screen.getByText('Seleccionar todo')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd frontend && pnpm exec vitest run src/pages/kitchen/components/FocusedCard.test.tsx`
Expected: FAIL — no checkboxes, no "Seleccionar todo" button, no dropdown exist yet in `FocusedCard.tsx`.

- [ ] **Step 3: Implement the selection UI**

Replace the full contents of `frontend/src/pages/kitchen/components/FocusedCard.tsx` with:

```tsx
import { useState } from 'react'
import { kitchenServices, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { Clock, TicketCheck, UserCheck } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS, STATUS_LABEL } from '../lib/itemStatus'
import { useTranslation } from '@/lib/i18n'

const BULK_TARGET_STATUSES: OrderItemStatus[] = ['PENDING', 'PREPARING', 'READY', 'DELIVERED']

export const FocusedCard = ({ order }: { order: kitchenOrders }) => {
  const queryClient = useQueryClient()
  const { t } = useTranslation('kitchen')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  const visibleItems = order.items?.filter((item) => item.status !== 'DELIVERED') ?? []

  const updateItemStatusMutation = useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: OrderItemStatus }) =>
      kitchenServices.updateItemStatus(order.id!, itemId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] })
    },
    onError: () => {
      toast.error(t('itemStatusUpdateErrorToast'))
    },
  })

  const bulkUpdateMutation = useMutation({
    mutationFn: (status: OrderItemStatus) =>
      kitchenServices.updateItemsStatus(order.id!, Array.from(selectedIds), status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] })
      setSelectedIds(new Set())
    },
    onError: () => {
      toast.error(t('itemStatusUpdateErrorToast'))
    },
  })

  const toggleItem = (itemId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })
  }

  const allSelected = visibleItems.length > 0 && selectedIds.size === visibleItems.length

  const toggleSelectAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(visibleItems.map((item) => item.itemId!)))
  }

  return (
    <>
      <div className="flex flex-col gap-6 shrink-0">
        <Card
          className={`p-5 rounded-3xl border-l-8 ${getColorForTable(order.sessionId!)}`}
        >
          <CardHeader className="flex flex-col gap-2 border-b">
            <h2 className="text-2xl font-bold text-[#8c1717] tracking-tight">
              {t('orderDetailsHeading', { tableNumber: order.tableNumber ?? '' })}
            </h2>
            <div className="w-full flex items-center justify-between">
              <div className="flex gap-3">
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <TicketCheck />{' '}
                  {t('ticketLabel', { code: order.id!.substring(0, 6).toUpperCase() })}
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <UserCheck /> {t('clientPlaceholder')}
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <Clock /> {t('entryTimeLabel', { time: order.createdAt ?? '' })}
                </span>
              </div>

              <div className="flex flex-row gap-3">
                <Button className="p-6 ">{t('printButton')}</Button>
                <Button className="p-6 " variant={'destructive'}>
                  {t('voidButton')}
                </Button>
              </div>
            </div>
            <div className="w-full flex items-center gap-3">
              <Button
                size="sm"
                variant="outline"
                onClick={toggleSelectAll}
                disabled={visibleItems.length === 0}
              >
                {allSelected ? t('kdsDeselectAll') : t('kdsSelectAll')}
              </Button>
              {selectedIds.size > 0 && (
                <Select
                  disabled={bulkUpdateMutation.isPending}
                  onValueChange={(value) => bulkUpdateMutation.mutate(value as OrderItemStatus)}
                >
                  <SelectTrigger className="rounded-xl">
                    <SelectValue placeholder={t('kdsBulkStatusPlaceholder')} />
                  </SelectTrigger>
                  <SelectContent>
                    {BULK_TARGET_STATUSES.map((status) => (
                      <SelectItem key={status} value={status}>
                        {STATUS_LABEL[status]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            </div>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-wrap gap-3">
              {visibleItems.map((item) => {
                const status = item.status ?? 'PENDING'
                const next = NEXT_STATUS[status]
                return (
                  <li
                    key={item.itemId}
                    className="flex items-center gap-3 rounded-2xl border border-gray-200 px-4 py-2"
                  >
                    <Checkbox
                      className="rounded-full"
                      aria-label={t('kdsSelectItemAriaLabel', { name: item.name ?? '' })}
                      checked={selectedIds.has(item.itemId!)}
                      onCheckedChange={() => toggleItem(item.itemId!)}
                    />
                    <div className="flex flex-col gap-1">
                      <span className="text-sm font-semibold text-gray-800">
                        {item.name}
                      </span>
                      {item.modifiers && item.modifiers.length > 0 && (
                        <span className="text-xs text-gray-500">
                          {item.modifiers.join(', ')}
                        </span>
                      )}
                      <Badge variant="outline" className="w-fit">
                        {STATUS_LABEL[status]}
                      </Badge>
                    </div>
                    {next && (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={updateItemStatusMutation.isPending}
                        onClick={() =>
                          updateItemStatusMutation.mutate({ itemId: item.itemId!, status: next })
                        }
                      >
                        {NEXT_ACTION_LABEL[status]}
                      </Button>
                    )}
                  </li>
                )
              })}
            </ul>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
```

The only differences from today's file: the new `selectedIds` state + `bulkUpdateMutation` + `toggleItem`/`toggleSelectAll`/`allSelected`, the new toolbar row (select-all button + conditional dropdown) under the header, and each `<li>` gaining a leading `Checkbox`. The existing per-item "advance one step" button, the `DELIVERED` filter, and everything else is untouched.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd frontend && pnpm exec vitest run src/pages/kitchen/components/FocusedCard.test.tsx`
Expected: all 4 tests PASS.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && pnpm run test:run && pnpm run build && pnpm run build:hub`
Expected: full suite green (previous count + 4), both builds clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/kitchen/components/FocusedCard.tsx frontend/src/pages/kitchen/components/FocusedCard.test.tsx
git commit -m "feat(kitchen): add bulk dish selection and status dropdown to the focused ticket"
```

---

## Self-Review Notes

- **Spec coverage:** every requirement pinned down in conversation is covered — per-dish circular checkboxes + "select all" (Task 4), dropdown offering any of the 4 kitchen-facing statuses in either direction (Task 1's bidirectional walk + Task 4's `BULK_TARGET_STATUSES`), items in different starting states each independently walking to the same chosen target (Task 1's per-item loop, tested explicitly), backward movement included as agreed-feasible (Task 1), the existing one-click buttons and `QueueCard.tsx` left untouched (Global Constraints + Task 1/4 explicitly not modifying `updateItemStatus`/`QueueCard.tsx`).
- **Placeholder scan:** no TBD/TODO; every step has real, complete code matching the actual files read during planning (exact line numbers/content quoted from `KitchenService.java`, `KitchenController.java`, `KitchenServiceTest.java`, `KitchenControllerTest.java`, `api.ts`, `FocusedCard.tsx`, both locale files).
- **Type consistency:** `updateItemsStatus(String orderId, List<String> itemIds, OrderItemStatus targetStatus)` in Task 1 matches the controller call in Task 2 and the frontend client signature `updateItemsStatus(orderId: string, itemIds: string[], status: OrderItemStatus)` in Task 3, matches `FocusedCard.tsx`'s `bulkUpdateMutation` call in Task 4. i18n keys `kdsSelectAll`/`kdsDeselectAll`/`kdsBulkStatusPlaceholder`/`kdsSelectItemAriaLabel` are defined identically in Task 3 (both locale files) and consumed by exactly those names in Task 4.
