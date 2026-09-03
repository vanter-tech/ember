# Waiter Table-Detail Action Buttons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the three dead header buttons in `TableInformation.tsx` — **Add item**, **Print bill** (only once the table is paid & closed), **Transfer** (hand the table to another waiter).

**Architecture:** Add item goes through a new WAITER-only `POST /sessions/{id}/waiter-items` that creates the `OrderItem` at `PENDING` and fires the same kitchen events a customer confirm does. Print bill: the auto-redirect on `CLOSED` is replaced by a stay-on-page "paid & closed" state; a new `POST /printing/bills/{billId}/receipt` builds a `BILL_RECEIPT` job on demand (gated on `Bill.status == PAID`), reusing a `ReceiptRenderer` extracted from the existing listener. Transfer: a new `GET /identity/waiters` feeds a picker; `POST /sessions/{id}/transfer` reassigns `Session.waiterId` and broadcasts on `/topic/waiter/{tenantId}` + `/topic/session/{id}` so both waiters' views refresh.

**Tech Stack:** Java 17 · Spring Boot 3.5 · Spring Security · Spring `ApplicationEventPublisher` + STOMP/`SimpMessagingTemplate` · JPA/Postgres · React 19 · TypeScript · Zustand 5 · TanStack Query 5 · Vitest + RTL · JUnit 5 + Mockito + AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-03-waiter-table-detail-actions-design.md`

## Global Constraints

- Backend build/test: `./mvnw test` from `backend/`. Never `mvn`.
- Frontend: `pnpm run build` (`tsc -b && vite build`), `pnpm run lint`, `pnpm run test:run` — from `frontend/`. `pnpm` only.
- Zero TypeScript / ESLint errors is a merge blocker.
- Locale files are parity-locked by `satisfies typeof es<Ns>` — every new key goes in BOTH `es` and `en` in the same task.
- Commits: Conventional Commits, lowercase, imperative. NO `Co-authored-by:` / `Signed-off-by:` / AI-attribution trailers (repo policy, `CLAUDE.md §4`).
- Stage only files the task touched, plus its report. Never `git add -A` / `.`.
- `@DataJpaTest` classes MUST carry `@Import(TenantIdentifierResolver.class)` (project-wide `@TenantId` entities).
- Domain facts (verified):
  - `Session.waiterId` (`varchar`, no `@Version` on it but `Session` has `@Version Long version`) stores the **waiter's email** — `SessionController.createSession` passes `authentication.getName()`.
  - `SessionService.getSessionDetails(String): SessionDetailResponseDto` is the shared read model returned by `GET /sessions/{id}`.
  - `OrderItemStatus`: `DRAFT, PENDING, PREPARING, READY, DELIVERED`.
  - `confirmDraftsForUser` is the reference for "send to kitchen": sets `PENDING`, appends a `SessionActivity(type=ITEM_SENT)`, `sessionRepository.save`, then publishes `ItemSent(savedSession)` and `KitchenItemsConfirmed(tenantId, sessionId, tableNumber, items)`.
  - `SessionActivity.Type` currently has only `ITEM_SENT, ITEM_DELETED` — a new `TABLE_TRANSFERRED` value is added in Task 6.
  - `BillStatus`: `OPEN, PAID, VOIDED`. `BillRepository.findBySessionIdAndStatusNot(sessionId, VOIDED)` and `findById(Long)` exist.
  - `PrintingEventListener.createAndDispatch(role, sourceType, sourceId, payload)` uses `printJobRepository.saveAndFlush(job)` then `printDispatchService.dispatch(job)`; `PrinterRole.RECEIPT`, `PrintJobSourceType.BILL_RECEIPT`.
  - WS: `/topic/session/{id}` frames carry a `type` string (`ITEM_ADDED`, `SESSION_CLOSED`, …). `websocket.ts` `subscribeToWaiterSession` invalidates `['sessionDetails', id]` on `ITEM_ADDED`/`ITEMS_CONFIRMED`/`ITEM_DELETED`/`PARTICIPANT_LEFT`/`SESSION_CLOSED`. `subscribeToWaiter` invalidates `['dashboardData']` on any `/topic/waiter/{tenantId}` frame.

---

## File Structure

**Backend — C1 Add item**
- Create `session/dto/AddWaiterItemRequest.java` — `{ menuItemId, selectedOptionIds, participantName }`.
- Modify `session/service/SessionService.java` — `addItemAsWaiter(...)`.
- Modify `session/controller/SessionController.java` — `POST /{id}/waiter-items`.

**Backend — C2 Print**
- Create `printing/service/ReceiptRenderer.java` — pure receipt-payload builder.
- Create `printing/service/BillReceiptPrintService.java` — enqueue a `BILL_RECEIPT` job on demand (PAID gate).
- Create `printing/controller/BillReceiptController.java` — `POST /printing/bills/{billId}/receipt`.
- Create `printing/exception/BillNotPaidException.java` — → HTTP 409, `code: BILL_NOT_PAID`.
- Modify `printing/listener/PrintingEventListener.java` — use `ReceiptRenderer` (no behavior change).
- Modify `config/GlobalExceptionHandler.java` — map `BillNotPaidException`.

**Backend — C3 Transfer**
- Modify `identity/repository/UserRepository.java` — `findByRestaurantId_IdAndRoleAndActiveTrue`.
- Create `identity/dto/WaiterSummary.java` — `{ id, name, email }`.
- Create `identity/controller/WaiterDirectoryController.java` — `GET /identity/waiters`.
- Create `session/dto/TransferTableRequest.java` — `{ targetWaiterId }`.
- Create `session/event/TableTransferred.java` — WS frame `type = "TABLE_TRANSFERRED"`.
- Modify `session/model/SessionActivity.java` — add `TABLE_TRANSFERRED` enum value.
- Modify `session/service/SessionService.java` — `transferTable(...)`.
- Modify `session/controller/SessionController.java` — `POST /{id}/transfer`.
- Modify `session/listener/SessionWebSocketListener.java` + `session/listener/WaiterWebSocketListener.java` — broadcast `TableTransferred`.

**Frontend**
- Modify `src/lib/api.ts` — `SessionTableService.addWaiterItem` / `listWaiters` / `transferTable`; `printingService.printBillReceipt`.
- Modify `src/store/uiStore.ts` — `ADD_ITEM`, `TRANSFER_TABLE` modal keys.
- Create `src/pages/waiter/components/AddItemModal.tsx` (+ test).
- Create `src/pages/waiter/components/TransferTableModal.tsx` (+ test).
- Modify `src/pages/waiter/TableInformation.tsx` — wire 3 buttons; closed stay-state; remove auto-redirect (+ test).
- Modify `src/store/websocket.ts` — handle `TABLE_TRANSFERRED` frame.
- Modify `src/locales/es/waiter.ts`, `src/locales/en/waiter.ts` — new keys.

---

## Task 1: Backend — `SessionService.addItemAsWaiter`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/session/dto/AddWaiterItemRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- Test: `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java` (append)

**Interfaces:**
- Produces: `SessionService.addItemAsWaiter(String sessionId, Long menuItemId, List<Long> selectedOptionIds, String participantName): Session`
  - Throws `IllegalStateException` if session not `OPEN` or if a non-voided `Bill` exists for the session (message `"Bill already requested for this session"` — mapped to 409 by the existing `IllegalStateException` handler).
  - Item created at `OrderItemStatus.PENDING`, attributed to `participantName` if it matches a current participant, else to `"Mesa"` (with `participantId = null`).
  - Appends a `SessionActivity(type = ITEM_SENT, itemName, participantName, timestamp = now)`.
  - Publishes `ItemAdded(sessionId, name, price, participantName, PENDING, items)` and `KitchenItemsConfirmed(tenantId, sessionId, tableNumber, List.of(newItem))` — identical constructors to those used by `addItem` / `confirmDraftsForUser`.
- Consumes: `BillRepository` (add as a `@RequiredArgsConstructor` field if not already injected — check imports first), `diningTableRepository` (already a field), `menuItemService`, `resolveSelectedModifiers` (existing private method).

- [ ] **Step 1: Create the request DTO**

`AddWaiterItemRequest.java`:

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AddWaiterItemRequest(
        @NotNull Long menuItemId,
        List<Long> selectedOptionIds,
        String participantName) {

    public AddWaiterItemRequest {
        if (selectedOptionIds == null) {
            selectedOptionIds = List.of();
        }
    }
}
```

- [ ] **Step 2: Write the failing tests**

Append to `SessionServiceTest.java` (match its existing mock setup — it mocks `sessionRepository`, `userRepository`, `menuItemService`, `diningTableRepository`, `eventPublisher`, etc. Add `@Mock BillRepository billRepository;` if not present and pass it to the `SessionService` under test the same way the class already constructs it).

```java
    @Test
    void addItemAsWaiter_addsPendingItemAttributedToMesa_andPublishesKitchenEvent() {
        Session session = openSessionWithNoParticipants("s1");           // test helper in this class or inline-build
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(billRepository.findBySessionIdAndStatusNot(eq("s1"), any())).thenReturn(Optional.empty());
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem(10L, "Pizza", "12.00"));
        when(diningTableRepository.findById(session.getTableId()))
                .thenReturn(Optional.of(diningTable(7)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.addItemAsWaiter("s1", 10L, List.of(), null);

        OrderItem added = result.getItems().get(result.getItems().size() - 1);
        assertThat(added.getStatus()).isEqualTo(OrderItemStatus.PENDING);
        assertThat(added.getParticipantName()).isEqualTo("Mesa");
        verify(eventPublisher).publishEvent(isA(KitchenItemsConfirmed.class));
        verify(eventPublisher).publishEvent(isA(ItemAdded.class));
    }

    @Test
    void addItemAsWaiter_attributesToNamedParticipant_whenNameMatches() {
        Session session = openSessionWithParticipant("s1", "p1", "Ana");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(billRepository.findBySessionIdAndStatusNot(eq("s1"), any())).thenReturn(Optional.empty());
        when(menuItemService.findById(10L)).thenReturn(availableMenuItem(10L, "Pizza", "12.00"));
        when(diningTableRepository.findById(session.getTableId())).thenReturn(Optional.of(diningTable(7)));
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Session result = sessionService.addItemAsWaiter("s1", 10L, List.of(), "Ana");

        assertThat(result.getItems().get(result.getItems().size() - 1).getParticipantName()).isEqualTo("Ana");
    }

    @Test
    void addItemAsWaiter_rejects_whenSessionClosed() {
        Session session = openSessionWithNoParticipants("s1");
        session.setStatus(SessionStatus.CLOSED);
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.addItemAsWaiter("s1", 10L, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void addItemAsWaiter_rejects_whenBillAlreadyExists() {
        Session session = openSessionWithNoParticipants("s1");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(billRepository.findBySessionIdAndStatusNot(eq("s1"), any()))
                .thenReturn(Optional.of(new Bill()));

        assertThatThrownBy(() -> sessionService.addItemAsWaiter("s1", 10L, List.of(), null))
                .isInstanceOf(IllegalStateException.class);
    }
```

Use / add small builders (`openSessionWithNoParticipants`, `openSessionWithParticipant`, `availableMenuItem`, `diningTable`) consistent with helpers already in this test file; if none exist, inline-build the objects.

- [ ] **Step 3: Run, expect FAIL**

Run: `./mvnw test -Dtest=SessionServiceTest` → FAIL to compile.

- [ ] **Step 4: Implement `addItemAsWaiter`**

In `SessionService.java`. Add `BillRepository` to the constructor deps if absent (import `com.vanter.ember.billing.repository.BillRepository`, `com.vanter.ember.billing.model.BillStatus`). Then:

```java
    public Session addItemAsWaiter(String sessionId, Long menuItemId,
                                   List<Long> selectedOptionIds, String participantName) {
        Session session = findById(sessionId);

        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Cannot add items to a session that is not open");
        }
        if (billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED).isPresent()) {
            throw new IllegalStateException("Bill already requested for this session");
        }

        MenuItemResponse menuItem = menuItemService.findById(menuItemId);
        if (!menuItem.isAvailable()) {
            throw new IllegalStateException("Menu item " + menuItemId + " is not available");
        }

        DiningTables table = diningTableRepository.findById(session.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found"));

        Participant named = participantName == null ? null : session.getParticipants().stream()
                .filter(p -> participantName.equals(p.getName()))
                .findFirst().orElse(null);
        String attributedName = named != null ? named.getName() : "Mesa";
        String attributedId = named != null ? named.getUserId() : null;

        List<SelectedModifier> selectedModifiers = resolveSelectedModifiers(menuItem, selectedOptionIds);
        BigDecimal totalPrice = menuItem.getPrice().add(selectedModifiers.stream()
                .map(SelectedModifier::getPriceDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        LocalDateTime now = LocalDateTime.now();
        OrderItem newItem = OrderItem.builder()
                .id(UUID.randomUUID().toString())
                .itemId(menuItem.getId())
                .name(menuItem.getName())
                .price(totalPrice)
                .participantId(attributedId)
                .participantName(attributedName)
                .status(OrderItemStatus.PENDING)
                .modifiers(selectedModifiers)
                .addedAt(now)
                .build();
        session.getItems().add(newItem);
        session.getActivityLog().add(SessionActivity.builder()
                .type(SessionActivity.Type.ITEM_SENT)
                .itemName(newItem.getName())
                .participantName(attributedName)
                .timestamp(now)
                .build());

        Session saved = sessionRepository.save(session);

        eventPublisher.publishEvent(new ItemAdded(
                saved.getId(), newItem.getName(), newItem.getPrice(),
                newItem.getParticipantName(), newItem.getStatus(), saved.getItems()));
        eventPublisher.publishEvent(new KitchenItemsConfirmed(
                saved.getTenantId(), saved.getId(), table.getTableNumber(), List.of(newItem)));

        return saved;
    }
```

Match the exact `ItemAdded` / `KitchenItemsConfirmed` constructor arg order to the existing call sites in this same file (copy from `addItem` and `confirmDraftsForUser`). `DiningTables` / `Participant` / `SelectedModifier` / `MenuItemResponse` are already imported.

- [ ] **Step 5: Run, expect PASS**

Run: `./mvnw test -Dtest=SessionServiceTest` → PASS.

- [ ] **Step 6: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/session/dto/AddWaiterItemRequest.java \
  backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
  backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java
git commit -m "feat(session): waiter can add a menu item straight to the kitchen"
```

---

## Task 2: Backend — `POST /sessions/{id}/waiter-items` endpoint

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- Test: `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java` (append)

**Interfaces:**
- Consumes: `SessionService.addItemAsWaiter` (Task 1), `SessionService.getSessionDetails`.
- Produces: `POST /sessions/{id}/waiter-items` `@PreAuthorize("hasRole('WAITER')")`, body `AddWaiterItemRequest`, returns `SessionDetailResponseDto`. `403` for `CUSTOMER` / `KITCHEN`.

- [ ] **Step 1: Write the failing controller tests**

Append to `SessionControllerTest.java` (a `@WebMvcTest(SessionController.class)` with `@MockBean SessionService` etc. — match the existing style, including how it authenticates roles: `@WithMockUser(roles = "WAITER")` or a JWT helper).

```java
    @Test
    @WithMockUser(roles = "WAITER")
    void addWaiterItem_200_returnsSessionDetail() throws Exception {
        when(sessionService.addItemAsWaiter(eq("s1"), eq(10L), any(), any()))
                .thenReturn(new Session());
        when(sessionService.getSessionDetails("s1")).thenReturn(sampleDetailDto("s1"));

        mockMvc.perform(post("/sessions/s1/waiter-items").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":10,\"selectedOptionIds\":[],\"participantName\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void addWaiterItem_403_forCustomer() throws Exception {
        mockMvc.perform(post("/sessions/s1/waiter-items").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void addWaiterItem_400_whenMenuItemIdMissing() throws Exception {
        mockMvc.perform(post("/sessions/s1/waiter-items").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"selectedOptionIds\":[]}"))
                .andExpect(status().isBadRequest());
    }
```

`sampleDetailDto` — reuse the helper the file already uses for `getSession` tests, or inline a `new SessionDetailResponseDto("s1", UUID.randomUUID(), 1, true, "w@test.com", SessionStatus.OPEN, 4, List.of(), List.of(), List.of(), LocalDateTime.now())`.

- [ ] **Step 2: Run, expect FAIL**

Run: `./mvnw test -Dtest=SessionControllerTest` → FAIL.

- [ ] **Step 3: Implement the endpoint**

In `SessionController.java`, after the existing `addItem` mapping:

```java
    @Operation(summary = "Add a menu item to the table straight to the kitchen (WAITER)")
    @PostMapping("/{id}/waiter-items")
    @PreAuthorize("hasRole('WAITER')")
    public SessionDetailResponseDto addWaiterItem(@PathVariable String id,
                                                  @Valid @RequestBody AddWaiterItemRequest request) {
        sessionService.addItemAsWaiter(
                id, request.menuItemId(), request.selectedOptionIds(), request.participantName());
        return sessionService.getSessionDetails(id);
    }
```

`AddWaiterItemRequest` resolves via the existing `import com.vanter.ember.session.dto.*;`.

- [ ] **Step 4: Run, expect PASS**

Run: `./mvnw test -Dtest=SessionControllerTest` → PASS.

- [ ] **Step 5: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
  backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java
git commit -m "feat(session): POST /sessions/{id}/waiter-items endpoint"
```

---

## Task 3: Frontend — Add Item modal + wire the button

**Files:**
- Modify: `frontend/src/lib/api.ts`, `frontend/src/store/uiStore.ts`, `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`
- Create: `frontend/src/pages/waiter/components/AddItemModal.tsx`, `frontend/src/pages/waiter/components/AddItemModal.test.tsx`

**Interfaces:**
- Consumes: `POST /sessions/{id}/waiter-items` (Task 2), `inventoryMenuItemService.listAll` (`api.ts`, already returns `MenuItemResponse[]` via `size:500`).
- Produces: `SessionTableService.addWaiterItem(sessionId, body)` where `body: { menuItemId: number; selectedOptionIds: number[]; participantName: string | null }`.

- [ ] **Step 1: Add the api method**

In `api.ts`, inside `SessionTableService` (next to `addItem` at ~L427):

```ts
  addWaiterItem: async (
    sessionId: string,
    body: { menuItemId: number; selectedOptionIds: number[]; participantName: string | null },
  ): Promise<void> => {
    await api.post<void>(`/sessions/${sessionId}/waiter-items`, body)
  },
```

- [ ] **Step 2: Add the modal key**

In `uiStore.ts` `ModalType` union add `'ADD_ITEM'`.

- [ ] **Step 3: Add i18n keys (BOTH locales)**

`locales/{es,en}/waiter.ts` (ES shown; add natural EN):

```ts
  addItemModalTitle: 'Agregar platillo',
  addItemSearchPlaceholder: 'Buscar platillo...',
  addItemParticipantLabel: 'Asignar a',
  addItemParticipantMesa: 'Mesa (general)',
  addItemQuantityLabel: 'Cantidad',
  addItemSubmit: 'Agregar a la comanda',
  addItemSuccessToast: 'Platillo agregado y enviado a cocina',
  addItemErrorToast: 'No se pudo agregar el platillo',
  addItemBillExistsToast: 'La cuenta ya fue solicitada. Anúlala para agregar platillos.',
```

- [ ] **Step 4: Write the failing test**

`AddItemModal.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AddItemModal } from '@/pages/waiter/components/AddItemModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService, inventoryMenuItemService } from '@/lib/api'

vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return {
    ...actual,
    inventoryMenuItemService: { listAll: vi.fn() },
    SessionTableService: { ...actual.SessionTableService, addWaiterItem: vi.fn() },
  }
})

const wrap = (ui: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>)
}

describe('AddItemModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'ADD_ITEM', modalPayload: { sessionId: 's1' } })
    ;(inventoryMenuItemService.listAll as vi.Mock).mockResolvedValue([
      { id: 10, name: 'Pizza', price: 12, available: true, modifierGroups: [] },
      { id: 11, name: 'Ensalada', price: 8, available: true, modifierGroups: [] },
    ])
  })

  test('filters the list by search text', async () => {
    wrap(<AddItemModal />)
    expect(await screen.findByText('Pizza')).toBeVisible()
    fireEvent.change(screen.getByPlaceholderText('addItemSearchPlaceholder'), { target: { value: 'ens' } })
    expect(screen.queryByText('Pizza')).not.toBeInTheDocument()
    expect(screen.getByText('Ensalada')).toBeVisible()
  })

  test('submits the selected item with participantName null for "Mesa"', async () => {
    ;(SessionTableService.addWaiterItem as vi.Mock).mockResolvedValue(undefined)
    wrap(<AddItemModal />)
    fireEvent.click(await screen.findByText('Pizza'))
    fireEvent.click(screen.getByText('addItemSubmit'))
    await waitFor(() =>
      expect(SessionTableService.addWaiterItem).toHaveBeenCalledWith('s1', {
        menuItemId: 10, selectedOptionIds: [], participantName: null,
      }))
  })
})
```

- [ ] **Step 5: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run AddItemModal` → FAIL.

- [ ] **Step 6: Implement `AddItemModal.tsx`**

Pattern-match `ChargeTableModal.tsx` (shared `Dialog`, `useUIStore`, `useMutation`, `useTranslation('waiter')`). Behavior:

- `isOpen = activeModal === 'ADD_ITEM'`; `sessionId = modalPayload?.sessionId`.
- `useQuery(['menuItemsAll'], inventoryMenuItemService.listAll)` — only when `isOpen`.
- Local state: `search`, `selected: MenuItemResponse | null`, `optionIds: number[]`, `qty: number` (min 1), `participant: string` (`''` = Mesa).
- Read participants from the payload if provided (`modalPayload.participants`), else render only the "Mesa" option. (Pass `participants` from `TableInformation` in Step 7.)
- If `selected?.modifierGroups?.length`, render a minimal picker: for each group, radio inputs when the group is single-select, checkboxes otherwise; collect chosen option ids into `optionIds`. Reference `SelectModifiersModal.tsx` for the option/group field names (`group.options`, `option.id`, `option.priceDelta`); do not import it if it drags in cart/session state — reimplement the few lines here.
- `mutation.mutationFn = async () => { for (let i = 0; i < qty; i++) await SessionTableService.addWaiterItem(sessionId, { menuItemId: selected!.id!, selectedOptionIds: optionIds, participantName: participant || null }) }`.
- `onSuccess`: `queryClient.invalidateQueries({ queryKey: ['sessionDetails', sessionId] })`, `queryClient.invalidateQueries({ queryKey: ['bill', sessionId] })`, `toast.success(t('addItemSuccessToast'))`, close + reset.
- `onError`: if `axios.isAxiosError(e) && e.response?.status === 409` → `toast.error(t('addItemBillExistsToast'))`, else `toast.error(t('addItemErrorToast'))`.
- Submit button disabled when `!selected`.

- [ ] **Step 7: Wire it into `TableInformation.tsx`**

- Import `AddItemModal`; render `<AddItemModal />` alongside `<ChargeTableModal/>` etc. at the bottom.
- The "Agregar platillo" button (`:208-210`):
  ```tsx
  onClick={() => openModal('ADD_ITEM', { sessionId: id, participants: sessionData?.participants ?? [] })}
  disabled={sessionData?.status !== 'OPEN'}
  ```
  (`openModal` comes from `useUIStore` — already destructured in this file.)

- [ ] **Step 8: Run test + build + lint**

Run: `cd frontend && pnpm run test:run AddItemModal && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/store/uiStore.ts \
  frontend/src/pages/waiter/components/AddItemModal.tsx frontend/src/pages/waiter/components/AddItemModal.test.tsx \
  frontend/src/pages/waiter/TableInformation.tsx \
  frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): add-item modal on the waiter table view"
```

---

## Task 4: Backend — extract `ReceiptRenderer` (no behavior change)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/printing/service/ReceiptRenderer.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java`
- Test: `backend/src/test/java/com/vanter/ember/printing/service/ReceiptRendererTest.java` (create)

**Interfaces:**
- Produces: `ReceiptRenderer.render(Long billId, SettingsPayload settings): String` — byte-for-byte the same output `PrintingEventListener.renderReceiptPayload` produced for the same inputs (header line if non-blank, `"Bill #" + billId`, footer line if non-blank).
- Consumes: nothing new.

- [ ] **Step 1: Write the characterization test**

`ReceiptRendererTest.java`:

```java
package com.vanter.ember.printing.service;

import com.vanter.ember.settings.model.SettingsPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptRendererTest {

    private final ReceiptRenderer renderer = new ReceiptRenderer();

    @Test
    void render_includesHeaderBillLineAndFooter() {
        SettingsPayload settings = new SettingsPayload();
        settings.getTicket().setHeaderMessage("Gracias por su visita");
        settings.getTicket().setFooterMessage("Vuelva pronto");

        String out = renderer.render(42L, settings);

        assertThat(out).isEqualTo("Gracias por su visita\nBill #42\nVuelva pronto\n");
    }

    @Test
    void render_omitsBlankHeaderAndFooter() {
        SettingsPayload settings = new SettingsPayload();
        String out = renderer.render(7L, settings);
        assertThat(out).isEqualTo("Bill #7\n");
    }
}
```

If `new SettingsPayload()` doesn't initialise `getTicket()` non-null, build it the way existing settings tests do (check `settings` module tests) — the assertion strings are the contract.

- [ ] **Step 2: Run, expect FAIL**

Run: `./mvnw test -Dtest=ReceiptRendererTest` → FAIL (class missing).

- [ ] **Step 3: Implement `ReceiptRenderer`**

Move the body of `PrintingEventListener.renderReceiptPayload` into:

```java
package com.vanter.ember.printing.service;

import com.vanter.ember.settings.model.SettingsPayload;
import org.springframework.stereotype.Component;

/** Builds the plain-text {@code BILL_RECEIPT} payload. Extracted so both the automatic
 *  {@code PaymentCompleted} path and the on-demand reprint endpoint render identically. */
@Component
public class ReceiptRenderer {

    public String render(Long billId, SettingsPayload settings) {
        StringBuilder sb = new StringBuilder();
        String header = settings.getTicket().getHeaderMessage();
        if (header != null && !header.isBlank()) {
            sb.append(header).append('\n');
        }
        sb.append("Bill #").append(billId).append('\n');
        String footer = settings.getTicket().getFooterMessage();
        if (footer != null && !footer.isBlank()) {
            sb.append(footer).append('\n');
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Rewire `PrintingEventListener`**

Add `private final ReceiptRenderer receiptRenderer;` (it is `@RequiredArgsConstructor`). Replace the `renderReceiptPayload(event, settings)` call in `onPaymentCompleted` with `receiptRenderer.render(event.billId(), settings)`. Delete the now-dead private `renderReceiptPayload` method. Leave `renderKitchenPayload` untouched.

- [ ] **Step 5: Run tests, expect PASS**

Run: `./mvnw test -Dtest=ReceiptRendererTest,PrintingEventListenerTest` (run whatever the listener's existing test class is called) → PASS. Then `./mvnw test` → PASS (count unchanged + 2).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/printing/service/ReceiptRenderer.java \
  backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java \
  backend/src/test/java/com/vanter/ember/printing/service/ReceiptRendererTest.java
git commit -m "refactor(printing): extract ReceiptRenderer from the payment listener"
```

---

## Task 5: Backend — on-demand `POST /printing/bills/{billId}/receipt`

**Files:**
- Create: `printing/service/BillReceiptPrintService.java`, `printing/controller/BillReceiptController.java`, `printing/exception/BillNotPaidException.java`
- Modify: `config/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/vanter/ember/printing/service/BillReceiptPrintServiceTest.java`, `.../printing/controller/BillReceiptControllerTest.java`

**Interfaces:**
- Produces:
  - `BillReceiptPrintService.enqueue(Long billId): PrintJob` — loads the bill (`404` via `ResourceNotFoundException` if missing), throws `BillNotPaidException` unless `bill.getStatus() == BillStatus.PAID`, renders via `ReceiptRenderer`, builds a `PrintJob` (`role = RECEIPT`, `sourceType = BILL_RECEIPT`, `sourceId = String.valueOf(billId)`, `status = PENDING`, `attempts = 0`, timestamps now), `printJobRepository.saveAndFlush(job)`, `printDispatchService.dispatch(job)`, returns the job.
  - `POST /printing/bills/{billId}/receipt` `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")` → `200 { jobId: <uuid>, status: <PrintJobStatus> }`.
  - `BillNotPaidException` → `409`, `code: BILL_NOT_PAID`.
- Consumes: `BillRepository`, `SettingService.getSettings(tenantId).getPayload()`, `TenantContextHolder.requireTenantId()`, `ReceiptRenderer` (Task 4), `PrintDispatchService`, `PrintJobRepository`.

- [ ] **Step 1: Create the exception**

```java
package com.vanter.ember.printing.exception;

public class BillNotPaidException extends RuntimeException {
    public BillNotPaidException(Long billId) { super("Bill " + billId + " is not fully paid"); }
}
```

- [ ] **Step 2: Write the failing service test**

`BillReceiptPrintServiceTest.java` (Mockito unit test):

```java
package com.vanter.ember.printing.service;

import com.vanter.ember.billing.model.Bill;
import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.printing.exception.BillNotPaidException;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.settings.model.Settings;
import com.vanter.ember.settings.model.SettingsPayload;
import com.vanter.ember.settings.service.SettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillReceiptPrintServiceTest {

    @Mock BillRepository billRepository;
    @Mock SettingService settingService;
    @Mock ReceiptRenderer receiptRenderer;
    @Mock PrintJobRepository printJobRepository;
    @Mock PrintDispatchService printDispatchService;
    @InjectMocks BillReceiptPrintService service;

    private Bill bill(BillStatus status) {
        Bill b = new Bill();
        b.setId(42L);
        b.setStatus(status);
        return b;
    }

    @Test
    void enqueue_buildsPendingReceiptJob_whenBillPaid() {
        when(billRepository.findById(42L)).thenReturn(Optional.of(bill(BillStatus.PAID)));
        Settings s = mock(Settings.class);
        when(s.getPayload()).thenReturn(new SettingsPayload());
        when(settingService.getSettings(any())).thenReturn(s);
        when(receiptRenderer.render(eq(42L), any())).thenReturn("Bill #42\n");
        when(printJobRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        PrintJob job = service.enqueue(42L);

        assertThat(job.getRole()).isEqualTo(PrinterRole.RECEIPT);
        assertThat(job.getSourceId()).isEqualTo("42");
        verify(printDispatchService).dispatch(job);
    }

    @Test
    void enqueue_throwsBillNotPaid_whenBillOpen() {
        when(billRepository.findById(42L)).thenReturn(Optional.of(bill(BillStatus.OPEN)));
        assertThatThrownBy(() -> service.enqueue(42L)).isInstanceOf(BillNotPaidException.class);
        verifyNoInteractions(printDispatchService);
    }

    @Test
    void enqueue_throwsNotFound_whenBillMissing() {
        when(billRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enqueue(99L))
                .isInstanceOf(com.vanter.ember.common.exception.ResourceNotFoundException.class);
    }
}
```

(Use the project's actual `ResourceNotFoundException` FQN — grep for it; the `GlobalExceptionHandler` imports it.)

- [ ] **Step 3: Run, expect FAIL**

Run: `./mvnw test -Dtest=BillReceiptPrintServiceTest` → FAIL.

- [ ] **Step 4: Implement `BillReceiptPrintService`**

```java
package com.vanter.ember.printing.service;

import com.vanter.ember.billing.model.BillStatus;
import com.vanter.ember.billing.repository.BillRepository;
import com.vanter.ember.common.exception.ResourceNotFoundException; // match project FQN
import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.printing.exception.BillNotPaidException;
import com.vanter.ember.printing.model.PrintJob;
import com.vanter.ember.printing.model.PrintJobSourceType;
import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.model.PrinterRole;
import com.vanter.ember.printing.repository.PrintJobRepository;
import com.vanter.ember.settings.service.SettingService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillReceiptPrintService {

    private final BillRepository billRepository;
    private final SettingService settingService;
    private final ReceiptRenderer receiptRenderer;
    private final PrintJobRepository printJobRepository;
    private final PrintDispatchService printDispatchService;

    public PrintJob enqueue(Long billId) {
        var bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill not found: " + billId));
        if (bill.getStatus() != BillStatus.PAID) {
            throw new BillNotPaidException(billId);
        }

        UUID tenantId = TenantContextHolder.requireTenantId();
        String payload = receiptRenderer.render(
                billId, settingService.getSettings(tenantId).getPayload());

        PrintJob job = PrintJob.builder()
                .id(UUID.randomUUID())
                .role(PrinterRole.RECEIPT)
                .sourceType(PrintJobSourceType.BILL_RECEIPT)
                .sourceId(String.valueOf(billId))
                .payload(payload)
                .status(PrintJobStatus.PENDING)
                .attempts(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        printJobRepository.saveAndFlush(job);   // @TenantId generated at flush — see PrintingEventListener
        printDispatchService.dispatch(job);
        return job;
    }
}
```

- [ ] **Step 5: Create the controller + response**

`BillReceiptController.java`:

```java
package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.model.PrintJobStatus;
import com.vanter.ember.printing.service.BillReceiptPrintService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/printing/bills")
@RequiredArgsConstructor
public class BillReceiptController {

    private final BillReceiptPrintService billReceiptPrintService;

    public record PrintReceiptResponse(UUID jobId, PrintJobStatus status) {}

    @Operation(summary = "Print (or reprint) the receipt for a paid bill (WAITER/ADMIN)")
    @PostMapping("/{billId}/receipt")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public PrintReceiptResponse printReceipt(@PathVariable Long billId) {
        var job = billReceiptPrintService.enqueue(billId);
        return new PrintReceiptResponse(job.getId(), job.getStatus());
    }
}
```

- [ ] **Step 6: Map `BillNotPaidException`**

In `GlobalExceptionHandler.java`:

```java
    @ExceptionHandler(com.vanter.ember.printing.exception.BillNotPaidException.class)
    public ProblemDetail handleBillNotPaid(
            com.vanter.ember.printing.exception.BillNotPaidException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
        problem.setProperty("code", "BILL_NOT_PAID");
        return problem;
    }
```

- [ ] **Step 7: Write the failing controller test**

`BillReceiptControllerTest.java` — `@WebMvcTest(BillReceiptController.class)`, `@MockBean BillReceiptPrintService`, `@Import(GlobalExceptionHandler.class)` if that is the local convention:

```java
    @Test
    @WithMockUser(roles = "WAITER")
    void printReceipt_200_withJobId() throws Exception {
        PrintJob job = PrintJob.builder().id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .status(PrintJobStatus.PENDING).build();
        when(service.enqueue(42L)).thenReturn(job);

        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "WAITER")
    void printReceipt_409_withCode_whenBillNotPaid() throws Exception {
        when(service.enqueue(42L)).thenThrow(new com.vanter.ember.printing.exception.BillNotPaidException(42L));
        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BILL_NOT_PAID"));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void printReceipt_403_forCustomer() throws Exception {
        mockMvc.perform(post("/printing/bills/42/receipt").with(csrf()))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 8: Run tests, expect PASS**

Run: `./mvnw test -Dtest=BillReceiptPrintServiceTest,BillReceiptControllerTest` → PASS.

- [ ] **Step 9: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/printing/service/BillReceiptPrintService.java \
  backend/src/main/java/com/vanter/ember/printing/controller/BillReceiptController.java \
  backend/src/main/java/com/vanter/ember/printing/exception/BillNotPaidException.java \
  backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java \
  backend/src/test/java/com/vanter/ember/printing/service/BillReceiptPrintServiceTest.java \
  backend/src/test/java/com/vanter/ember/printing/controller/BillReceiptControllerTest.java
git commit -m "feat(printing): on-demand bill receipt endpoint gated on paid status"
```

---

## Task 6: Backend — `TableTransferred` event + `SessionActivity.TABLE_TRANSFERRED` + `SessionService.transferTable`

**Files:**
- Create: `session/event/TableTransferred.java`
- Modify: `session/model/SessionActivity.java`, `session/service/SessionService.java`, `session/listener/SessionWebSocketListener.java`, `session/listener/WaiterWebSocketListener.java`
- Test: `SessionServiceTest.java` (append)

**Interfaces:**
- Produces:
  - `record TableTransferred(String type, UUID tenantId, String sessionId, UUID tableId, String fromWaiterId, String toWaiterId, String toWaiterName)` with a convenience ctor defaulting `type = "TABLE_TRANSFERRED"` (mirrors `SessionClosed`).
  - `SessionService.transferTable(String sessionId, String callerEmail, String targetWaiterId): Session`
    - `IllegalStateException` if session not `OPEN`.
    - `AccessDeniedException` if `!session.getWaiterId().equals(callerEmail)`.
    - `IllegalArgumentException` if the target user does not exist, is not `Role.WAITER` in this tenant, is inactive, or resolves to the same email as the current owner.
    - Sets `session.setWaiterId(targetUser.getEmail())` (the `waiterId` column stores email — see Global Constraints), appends `SessionActivity(type = TABLE_TRANSFERRED, itemName = null, participantName = targetUser.getName(), timestamp = now)`, `sessionRepository.save`, publishes `TableTransferred`.
- Consumes: `UserRepository` (already a field), `TenantContextHolder`.

- [ ] **Step 1: Add the enum value**

In `SessionActivity.java` `Type`: `ITEM_SENT, ITEM_DELETED, TABLE_TRANSFERRED`.

- [ ] **Step 2: Create the event**

```java
package com.vanter.ember.session.event;

import java.util.UUID;

public record TableTransferred(
        String type, UUID tenantId, String sessionId, UUID tableId,
        String fromWaiterId, String toWaiterId, String toWaiterName) {

    public TableTransferred(UUID tenantId, String sessionId, UUID tableId,
                            String fromWaiterId, String toWaiterId, String toWaiterName) {
        this("TABLE_TRANSFERRED", tenantId, sessionId, tableId,
                fromWaiterId, toWaiterId, toWaiterName);
    }
}
```

- [ ] **Step 3: Write the failing service tests**

Append to `SessionServiceTest.java`:

```java
    @Test
    void transferTable_reassignsWaiterAndPublishesEvent() {
        Session session = openSessionWithNoParticipants("s1");
        session.setWaiterId("old@test.com");
        session.setTenantId(UUID.randomUUID());
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        User target = User.builder().id("u9").email("new@test.com").name("Nueva")
                .role(Role.WAITER).active(true).build();
        when(userRepository.findById("u9")).thenReturn(Optional.of(target));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Session result = sessionService.transferTable("s1", "old@test.com", "u9");

        assertThat(result.getWaiterId()).isEqualTo("new@test.com");
        assertThat(result.getActivityLog())
                .anyMatch(a -> a.getType() == SessionActivity.Type.TABLE_TRANSFERRED);
        verify(eventPublisher).publishEvent(isA(TableTransferred.class));
    }

    @Test
    void transferTable_rejects_whenCallerNotOwner() {
        Session session = openSessionWithNoParticipants("s1");
        session.setWaiterId("owner@test.com");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.transferTable("s1", "intruder@test.com", "u9"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void transferTable_rejects_whenTargetNotWaiter() {
        Session session = openSessionWithNoParticipants("s1");
        session.setWaiterId("old@test.com");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));
        when(userRepository.findById("u9")).thenReturn(Optional.of(
                User.builder().id("u9").email("k@test.com").role(Role.KITCHEN).active(true).build()));

        assertThatThrownBy(() -> sessionService.transferTable("s1", "old@test.com", "u9"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transferTable_rejects_whenSessionClosed() {
        Session session = openSessionWithNoParticipants("s1");
        session.setStatus(SessionStatus.CLOSED);
        session.setWaiterId("old@test.com");
        when(sessionRepository.findById("s1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.transferTable("s1", "old@test.com", "u9"))
                .isInstanceOf(IllegalStateException.class);
    }
```

- [ ] **Step 4: Run, expect FAIL**

Run: `./mvnw test -Dtest=SessionServiceTest` → FAIL.

- [ ] **Step 5: Implement `transferTable`**

```java
    public Session transferTable(String sessionId, String callerEmail, String targetWaiterId) {
        Session session = findById(sessionId);
        if (session.getStatus() != SessionStatus.OPEN) {
            throw new IllegalStateException("Only an open table can be transferred");
        }
        if (!callerEmail.equals(session.getWaiterId())) {
            throw new AccessDeniedException("Only the current waiter can transfer this table");
        }
        User target = userRepository.findById(targetWaiterId)
                .orElseThrow(() -> new IllegalArgumentException("Target waiter not found"));
        if (target.getRole() != Role.WAITER || !Boolean.TRUE.equals(target.getActive())
                || target.getEmail().equals(session.getWaiterId())) {
            throw new IllegalArgumentException("Invalid transfer target");
        }

        String from = session.getWaiterId();
        session.setWaiterId(target.getEmail());
        session.getActivityLog().add(SessionActivity.builder()
                .type(SessionActivity.Type.TABLE_TRANSFERRED)
                .participantName(target.getName())
                .timestamp(LocalDateTime.now())
                .build());

        Session saved = sessionRepository.save(session);
        eventPublisher.publishEvent(new TableTransferred(
                saved.getTenantId(), saved.getId(), saved.getTableId(),
                from, target.getEmail(), target.getName()));
        return saved;
    }
```

Imports: `com.vanter.ember.identity.model.Role`, `com.vanter.ember.identity.model.User`, `com.vanter.ember.session.event.TableTransferred`, `org.springframework.security.access.AccessDeniedException` (check — `confirmDraftsForUser` already uses `AccessDeniedException`, so it is imported).

- [ ] **Step 6: Broadcast the event**

In `SessionWebSocketListener.java`:

```java
    @EventListener
    public void onTableTransferred(TableTransferred event) {
        messagingTemplate.convertAndSend("/topic/session/" + event.sessionId(), event);
    }
```

In `WaiterWebSocketListener.java` (add the import for `TableTransferred`):

```java
    @EventListener
    public void onTableTransferred(TableTransferred event) {
        messagingTemplate.convertAndSend("/topic/waiter/" + event.tenantId(), event);
    }
```

- [ ] **Step 7: Run tests, expect PASS**

Run: `./mvnw test -Dtest=SessionServiceTest` → PASS. If `SessionWebSocketListenerTest` / `WaiterWebSocketListenerTest` assert an exhaustive event list, extend them with a `TableTransferred` case.

- [ ] **Step 8: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/session/event/TableTransferred.java \
  backend/src/main/java/com/vanter/ember/session/model/SessionActivity.java \
  backend/src/main/java/com/vanter/ember/session/service/SessionService.java \
  backend/src/main/java/com/vanter/ember/session/listener/SessionWebSocketListener.java \
  backend/src/main/java/com/vanter/ember/session/listener/WaiterWebSocketListener.java \
  backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java
# plus the two listener test files if you extended them
git commit -m "feat(session): transfer a table to another waiter"
```

---

## Task 7: Backend — `GET /identity/waiters` + `POST /sessions/{id}/transfer`

**Files:**
- Modify: `identity/repository/UserRepository.java`, `session/controller/SessionController.java`
- Create: `identity/dto/WaiterSummary.java`, `identity/controller/WaiterDirectoryController.java`, `session/dto/TransferTableRequest.java`
- Test: `backend/src/test/java/com/vanter/ember/identity/controller/WaiterDirectoryControllerTest.java`, `SessionControllerTest.java` (append)

**Interfaces:**
- Produces:
  - `UserRepository.findByRestaurantId_IdAndRoleAndActiveTrue(UUID restaurantId, Role role): List<User>`.
  - `record WaiterSummary(String id, String name, String email)` + `static WaiterSummary from(User u)`.
  - `GET /identity/waiters` `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")` → `List<WaiterSummary>` (never full `User` — no `passwordHash`/`pinHash`).
  - `record TransferTableRequest(@NotBlank String targetWaiterId)`.
  - `POST /sessions/{id}/transfer` `@PreAuthorize("hasRole('WAITER')")`, body `TransferTableRequest`, returns `SessionDetailResponseDto`.
- Consumes: `SessionService.transferTable` (Task 6), `TenantContextHolder.requireTenantId()`, `Authentication.getName()`.

- [ ] **Step 1: Add the repository finder**

In `UserRepository.java`:

```java
    /** Active waiters for a tenant — the transfer-table picker. Untenanted-by-FK like the siblings above. */
    List<User> findByRestaurantId_IdAndRoleAndActiveTrue(UUID restaurantId, Role role);
```

- [ ] **Step 2: Create `WaiterSummary`**

```java
package com.vanter.ember.identity.dto;

import com.vanter.ember.identity.model.User;

public record WaiterSummary(String id, String name, String email) {
    public static WaiterSummary from(User u) {
        return new WaiterSummary(u.getId(), u.getName(), u.getEmail());
    }
}
```

- [ ] **Step 3: Create `TransferTableRequest`**

```java
package com.vanter.ember.session.dto;

import jakarta.validation.constraints.NotBlank;

public record TransferTableRequest(@NotBlank String targetWaiterId) {}
```

- [ ] **Step 4: Write the failing `WaiterDirectoryController` test**

`WaiterDirectoryControllerTest.java` — `@WebMvcTest(WaiterDirectoryController.class)`, mock whatever the controller depends on (`UserRepository` + a tenant-context test helper, or a thin `WaiterDirectoryService` — keep it a controller + repo, mock `UserRepository`, and stub `TenantContextHolder` the way other `@WebMvcTest`s in this repo do; if they use a `@MockBean` for a tenant filter, match it):

```java
    @Test
    @WithMockUser(roles = "WAITER")
    void listWaiters_returnsSummariesOnly() throws Exception {
        when(userRepository.findByRestaurantId_IdAndRoleAndActiveTrue(any(), eq(Role.WAITER)))
                .thenReturn(List.of(User.builder().id("u1").name("Ana").email("ana@x.com")
                        .role(Role.WAITER).passwordHash("SECRET").build()));

        mockMvc.perform(get("/identity/waiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("u1"))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].email").value("ana@x.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "KITCHEN")
    void listWaiters_403_forKitchen() throws Exception {
        mockMvc.perform(get("/identity/waiters")).andExpect(status().isForbidden());
    }
```

- [ ] **Step 5: Run, expect FAIL**

Run: `./mvnw test -Dtest=WaiterDirectoryControllerTest` → FAIL.

- [ ] **Step 6: Implement `WaiterDirectoryController`**

```java
package com.vanter.ember.identity.controller;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.dto.WaiterSummary;
import com.vanter.ember.identity.model.Role;
import com.vanter.ember.identity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/identity")
@RequiredArgsConstructor
public class WaiterDirectoryController {

    private final UserRepository userRepository;

    @Operation(summary = "List active waiters for the current tenant (WAITER/ADMIN)")
    @GetMapping("/waiters")
    @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
    public List<WaiterSummary> listWaiters() {
        return userRepository
                .findByRestaurantId_IdAndRoleAndActiveTrue(
                        TenantContextHolder.requireTenantId(), Role.WAITER)
                .stream().map(WaiterSummary::from).toList();
    }
}
```

- [ ] **Step 7: Write the failing transfer-endpoint test**

Append to `SessionControllerTest.java`:

```java
    @Test
    @WithMockUser(username = "w@test.com", roles = "WAITER")
    void transfer_200_returnsSessionDetail() throws Exception {
        when(sessionService.transferTable(eq("s1"), eq("w@test.com"), eq("u9")))
                .thenReturn(new Session());
        when(sessionService.getSessionDetails("s1")).thenReturn(sampleDetailDto("s1"));

        mockMvc.perform(post("/sessions/s1/transfer").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetWaiterId\":\"u9\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    @WithMockUser(username = "w@test.com", roles = "WAITER")
    void transfer_400_whenTargetMissing() throws Exception {
        mockMvc.perform(post("/sessions/s1/transfer").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void transfer_403_forCustomer() throws Exception {
        mockMvc.perform(post("/sessions/s1/transfer").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"targetWaiterId\":\"u9\"}"))
                .andExpect(status().isForbidden());
    }
```

- [ ] **Step 8: Implement the endpoint**

In `SessionController.java`:

```java
    @Operation(summary = "Transfer this table to another waiter (WAITER)")
    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasRole('WAITER')")
    public SessionDetailResponseDto transfer(@PathVariable String id,
                                             @Valid @RequestBody TransferTableRequest request,
                                             Authentication authentication) {
        sessionService.transferTable(id, authentication.getName(), request.targetWaiterId());
        return sessionService.getSessionDetails(id);
    }
```

- [ ] **Step 9: Run tests, expect PASS**

Run: `./mvnw test -Dtest=WaiterDirectoryControllerTest,SessionControllerTest` → PASS.

- [ ] **Step 10: Full suite + commit**

Run: `./mvnw test` → PASS.

```bash
git add backend/src/main/java/com/vanter/ember/identity/repository/UserRepository.java \
  backend/src/main/java/com/vanter/ember/identity/dto/WaiterSummary.java \
  backend/src/main/java/com/vanter/ember/identity/controller/WaiterDirectoryController.java \
  backend/src/main/java/com/vanter/ember/session/dto/TransferTableRequest.java \
  backend/src/main/java/com/vanter/ember/session/controller/SessionController.java \
  backend/src/test/java/com/vanter/ember/identity/controller/WaiterDirectoryControllerTest.java \
  backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java
git commit -m "feat(session): waiters directory and transfer-table endpoint"
```

---

## Task 8: Frontend — `TableInformation` closed stay-state + remove auto-redirect

**Files:**
- Modify: `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`
- Test: `frontend/src/pages/waiter/TableInformation.closedstate.test.tsx` (create)

**Interfaces:**
- No new API. Behavior: on fresh mount with `sessionData.status === 'CLOSED'` → redirect to `/waiter/tables`. On a `OPEN → CLOSED` transition while mounted → show a banner and disable every action except Print bill.

- [ ] **Step 1: Add i18n keys (BOTH locales)**

```ts
  tablePaidClosedBanner: 'Mesa pagada y cerrada. Puedes imprimir la cuenta antes de salir.',
  // EN: 'Table paid and closed. You can print the bill before leaving.'
```

- [ ] **Step 2: Write the failing test**

`TableInformation.closedstate.test.tsx` — mock `@/lib/api` so `SessionTableService.sessionInformation` and `billingService.getBillState` return controllable values; render inside `MemoryRouter` + `QueryClientProvider`; mock `react-router-dom`'s `useNavigate` to a spy.

```tsx
test('redirects to /waiter/tables when the session is already CLOSED on mount', async () => {
  // sessionInformation resolves status: 'CLOSED' immediately
  // expect navigate spy called with '/waiter/tables' and NOT rendering the banner
})

test('shows the paid-and-closed banner when status transitions OPEN -> CLOSED while mounted', async () => {
  // first resolve status 'OPEN', then update the query cache / refetch to 'CLOSED'
  // expect getByText('tablePaidClosedBanner') visible; navigate NOT called
})
```

Flesh these out against the file's existing test helpers (there is already a `WaiterTour.test.tsx` and `SectionTour` usage — reuse their provider wrapper). Keep assertions on: banner text presence, `navigate` spy calls, and the "Imprimir cuenta" button being enabled while "Agregar platillo"/"Transferir" are disabled.

- [ ] **Step 3: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run TableInformation.closedstate` → FAIL.

- [ ] **Step 4: Implement**

In `TableInformation.tsx`:

- Add `const wasOpenRef = useRef(false)` (import `useRef`).
- Add an effect:
  ```tsx
  useEffect(() => {
    if (sessionData?.status === 'OPEN') wasOpenRef.current = true
  }, [sessionData?.status])
  ```
- **Replace** the existing effect at `:76-81`:
  ```tsx
  useEffect(() => {
    if (sessionData?.status === 'CLOSED' && !wasOpenRef.current) {
      navigate('/waiter/tables', { replace: true })
    }
  }, [sessionData?.status, navigate])
  ```
  (Drop the `toast.success(t('tableClosedPaidToast'))` from the old effect, OR keep a single success toast fired only on the `wasOpenRef.current === true` transition — choose one; do not navigate on the transition.)
- Add derived flags near the other derived values:
  ```tsx
  const isClosedStayState = sessionData?.status === 'CLOSED' && wasOpenRef.current
  const actionsDisabled = sessionData?.status !== 'OPEN'
  ```
- Render the banner above the header actions when `isClosedStayState`:
  ```tsx
  {isClosedStayState && (
    <div className="mb-4 rounded-2xl bg-amber-50 border border-amber-200 px-5 py-3 text-amber-800 font-medium">
      {t('tablePaidClosedBanner')}
    </div>
  )}
  ```
- Apply `disabled={actionsDisabled}` to the "Agregar platillo" and "Transferir" buttons (Print bill is handled in Task 10). Also guard the "Cobrar mesa" / settle / per-item delete controls with `actionsDisabled` (or hide the whole bill-action column when `isClosedStayState` and only show totals + Print).

- [ ] **Step 5: Run test + build + lint**

Run: `cd frontend && pnpm run test:run TableInformation && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/waiter/TableInformation.tsx \
  frontend/src/pages/waiter/TableInformation.closedstate.test.tsx \
  frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): keep the waiter on a paid-and-closed table with a banner"
```

---

## Task 9: Frontend — wire the Print bill button

**Files:**
- Modify: `frontend/src/lib/api.ts`, `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`
- Test: `frontend/src/pages/waiter/TableInformation.printbill.test.tsx` (create)

**Interfaces:**
- Consumes: `POST /printing/bills/{billId}/receipt` (Task 5).
- Produces: `printingService.printBillReceipt(billId: number): Promise<{ jobId: string; status: string }>` in `api.ts`.

- [ ] **Step 1: Add the api method**

In `api.ts` — add a `printingService` object (or extend an existing one if present; grep for `printing`):

```ts
export const printingService = {
  printBillReceipt: async (billId: number): Promise<{ jobId: string; status: string }> => {
    const { data } = await api.post<{ jobId: string; status: string }>(
      `/printing/bills/${billId}/receipt`,
    )
    return data
  },
}
```

- [ ] **Step 2: Add i18n keys (BOTH locales)**

```ts
  printSentToast: 'Cuenta enviada a la impresora',                 // EN: 'Bill sent to the printer'
  printQueuedNoAgentToast: 'Cuenta en cola (sin impresora conectada)',
  printFailedToast: 'No se pudo imprimir la cuenta',
```

- [ ] **Step 3: Write the failing test**

`TableInformation.printbill.test.tsx`:

```tsx
// Arrange sessionInformation -> status 'CLOSED' (after an OPEN tick so wasOpenRef is set),
// billingService.getBillState -> { id: 5, total: 30, splits: [...] }
// printingService.printBillReceipt -> mock

test('Print bill is disabled until the table is CLOSED', async () => {
  // status 'OPEN' -> button [name=/printBillLabel/] is disabled
})

test('clicking Print bill calls printBillReceipt with the bill id and toasts on SENT', async () => {
  // status CLOSED (post-transition), click Print -> expect printBillReceipt(5)
  // resolve { status: 'SENT' } -> expect toast 'printSentToast'
})

test('PENDING status toasts the no-agent message', async () => {
  // resolve { status: 'PENDING' } -> expect toast 'printQueuedNoAgentToast'
})
```

- [ ] **Step 4: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run TableInformation.printbill` → FAIL.

- [ ] **Step 5: Implement**

In `TableInformation.tsx`:

```tsx
const printBillMutation = useMutation({
  mutationFn: (billId: number) => printingService.printBillReceipt(billId),
  onSuccess: (res) => {
    toast.success(res.status === 'PENDING' ? t('printQueuedNoAgentToast') : t('printSentToast'))
  },
  onError: () => toast.error(t('printFailedToast')),
})
```

"Imprimir cuenta" button (`:195-200`):

```tsx
onClick={() => billData && printBillMutation.mutate(billData.id)}
disabled={!billData || sessionData?.status !== 'CLOSED' || printBillMutation.isPending}
```

Import `printingService` from `@/lib/api`.

- [ ] **Step 6: Run test + build + lint**

Run: `cd frontend && pnpm run test:run TableInformation && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/pages/waiter/TableInformation.tsx \
  frontend/src/pages/waiter/TableInformation.printbill.test.tsx \
  frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): print the bill from a paid-and-closed table"
```

---

## Task 10: Frontend — Transfer Table modal + wire the button + WS frame

**Files:**
- Modify: `frontend/src/lib/api.ts`, `frontend/src/store/uiStore.ts`, `frontend/src/store/websocket.ts`, `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`
- Create: `frontend/src/pages/waiter/components/TransferTableModal.tsx`, `frontend/src/pages/waiter/components/TransferTableModal.test.tsx`

**Interfaces:**
- Consumes: `GET /identity/waiters`, `POST /sessions/{id}/transfer` (Task 7).
- Produces:
  - `SessionTableService.listWaiters(): Promise<{ id: string; name: string; email: string }[]>`
  - `SessionTableService.transferTable(sessionId: string, targetWaiterId: string): Promise<void>`

- [ ] **Step 1: Add api methods**

In `api.ts` `SessionTableService`:

```ts
  listWaiters: async (): Promise<{ id: string; name: string; email: string }[]> => {
    const { data } = await api.get<{ id: string; name: string; email: string }[]>('/identity/waiters')
    return data
  },
  transferTable: async (sessionId: string, targetWaiterId: string): Promise<void> => {
    await api.post<void>(`/sessions/${sessionId}/transfer`, { targetWaiterId })
  },
```

- [ ] **Step 2: Add modal key + i18n keys (BOTH locales)**

`uiStore.ts` `ModalType`: add `'TRANSFER_TABLE'`.

```ts
  transferModalTitle: 'Transferir mesa',
  transferModalDescription: 'Selecciona el mesero que se hará cargo de esta mesa.',
  transferNoWaiters: 'No hay otros meseros activos',
  transferSubmit: 'Transferir',
  transferSuccessToast: 'Mesa transferida a {name}',
  transferErrorToast: 'No se pudo transferir la mesa',
```

- [ ] **Step 3: Write the failing test**

`TransferTableModal.test.tsx`:

```tsx
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { TransferTableModal } from '@/pages/waiter/components/TransferTableModal'
import { useUIStore } from '@/store/uiStore'
import { SessionTableService } from '@/lib/api'

const navigate = vi.fn()
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigate }
})
vi.mock('@/lib/api', async (orig) => {
  const actual = await orig<typeof import('@/lib/api')>()
  return { ...actual, SessionTableService: { ...actual.SessionTableService, listWaiters: vi.fn(), transferTable: vi.fn() } }
})

const wrap = (ui: React.ReactNode) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

describe('TransferTableModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useUIStore.setState({ activeModal: 'TRANSFER_TABLE', modalPayload: { sessionId: 's1', currentWaiterEmail: 'me@x.com' } })
    ;(SessionTableService.listWaiters as vi.Mock).mockResolvedValue([
      { id: 'u1', name: 'Ana', email: 'ana@x.com' },
      { id: 'u2', name: 'Yo', email: 'me@x.com' },
    ])
  })

  test('excludes the current waiter from the list', async () => {
    wrap(<TransferTableModal />)
    expect(await screen.findByText('Ana')).toBeVisible()
    expect(screen.queryByText('Yo')).not.toBeInTheDocument()
  })

  test('submitting transfers and navigates to /waiter/tables', async () => {
    ;(SessionTableService.transferTable as vi.Mock).mockResolvedValue(undefined)
    wrap(<TransferTableModal />)
    fireEvent.click(await screen.findByText('Ana'))
    fireEvent.click(screen.getByText('transferSubmit'))
    await waitFor(() => expect(SessionTableService.transferTable).toHaveBeenCalledWith('s1', 'u1'))
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('/waiter/tables'))
  })
})
```

- [ ] **Step 4: Run, expect FAIL**

Run: `cd frontend && pnpm run test:run TransferTableModal` → FAIL.

- [ ] **Step 5: Implement `TransferTableModal.tsx`**

Pattern-match `ChargeTableModal.tsx`:

- `isOpen = activeModal === 'TRANSFER_TABLE'`; `sessionId`, `currentWaiterEmail` from `modalPayload`.
- `useQuery(['waiters'], SessionTableService.listWaiters, { enabled: isOpen })`.
- Filter out `w.email === currentWaiterEmail`. If the filtered list is empty, show `t('transferNoWaiters')` and disable submit.
- Local `selectedId: string | null`; radio-style list rows.
- `mutation.mutationFn = () => SessionTableService.transferTable(sessionId, selectedId!)`.
- `onSuccess`: `toast.success(t('transferSuccessToast', { name: <selected name> }))`, `closeModal()`, `navigate('/waiter/tables')`.
- `onError`: `toast.error(t('transferErrorToast'))`.

- [ ] **Step 6: Wire into `TableInformation.tsx`**

- Render `<TransferTableModal />` with the other modals.
- "Transferir" button (`:201-206`):
  ```tsx
  onClick={() => openModal('TRANSFER_TABLE', {
    sessionId: id,
    currentWaiterEmail: sessionData?.waiterId,
  })}
  disabled={sessionData?.status !== 'OPEN'}
  ```

- [ ] **Step 7: Handle the WS frame**

In `websocket.ts` `subscribeToWaiterSession`, add `'TABLE_TRANSFERRED'` to the `if (eventData.type === 'ITEM_ADDED' || ...)` list that invalidates `['sessionDetails', sessionId]`. The `/topic/waiter/{tenantId}` subscription (`subscribeToWaiter`) already invalidates `['dashboardData']` for **any** frame, so the receiving waiter's `Tables.tsx` refreshes with no further change.

- [ ] **Step 8: Run test + build + lint**

Run: `cd frontend && pnpm run test:run TransferTableModal TableInformation && pnpm run build && pnpm run lint` → PASS.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/lib/api.ts frontend/src/store/uiStore.ts frontend/src/store/websocket.ts \
  frontend/src/pages/waiter/components/TransferTableModal.tsx \
  frontend/src/pages/waiter/components/TransferTableModal.test.tsx \
  frontend/src/pages/waiter/TableInformation.tsx \
  frontend/src/locales/es/waiter.ts frontend/src/locales/en/waiter.ts
git commit -m "feat(frontend): transfer-table modal on the waiter table view"
```

---

## Task 11: Report + PROGRESS.md + full verification

**Files:**
- Create: `reports/NNN-feat-waiter-table-detail-actions.md` (next free number)
- Modify: `PROGRESS.md`

- [ ] **Step 1: Full verification**

Run: `cd backend && ./mvnw test` → PASS (record count).
Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run` → PASS (record counts).

- [ ] **Step 2: Manual smoke (document results in the report)**

With the app running (`./mvnw spring-boot:run` + `pnpm run dev`), as a WAITER on `/waiter/tables/:id`:
- Add item → item appears in the order list, KDS receives it.
- Charge + settle the table → view stays on the page, banner shows, only Print bill is enabled.
- Print bill → toast (SENT or queued-no-agent depending on print-agent presence).
- Leave the view, navigate back to `/waiter/tables/:id` for that closed session → redirected to `/waiter/tables`.
- Open a second browser as another WAITER; Transfer the table → it disappears from your floor list and appears on theirs.

- [ ] **Step 3: Write the report** per `CLAUDE.md §4` (Identification with predecessor = plan-B's report, Objective, Modified Files, What Changed, Why, Verification).

- [ ] **Step 4: Update `PROGRESS.md`**

- Flip `- [ ] C: table-detail action buttons ...` → `- [x] C: ... (report NNN)`.
- Add a "Last Completed Task (report NNN, ...)" bullet at the top of Current Execution State.
- Keep the file within budget.

- [ ] **Step 5: Commit**

```bash
git add reports/NNN-feat-waiter-table-detail-actions.md PROGRESS.md
git commit -m "docs: report and progress for waiter table-detail actions"
```

---

## Self-Review Notes (author)

- **Spec coverage:** §3.1 add-item backend → Tasks 1–2. §3.2 add-item frontend → Task 3. §4.1 stay-state → Task 8. §4.2 on-demand receipt → Tasks 4–5. §4.3 print frontend → Task 9. §5.1 transfer backend (`GET /identity/waiters`, `POST /sessions/{id}/transfer`, `TableTransferred`, listeners) → Tasks 6–7. §5.2 transfer frontend + WS → Task 10. §6 file list → matches. §7 testing → each task carries its tests. §8 locked decisions (409 on existing bill; client loops qty; minimal modifier picker; own `WaiterDirectoryController`) → Tasks 1, 3, 3, 7 respectively.
- **Type consistency:** `addItemAsWaiter(sessionId, menuItemId, selectedOptionIds, participantName)` identical in Tasks 1/2. `SessionTableService.addWaiterItem(sessionId, { menuItemId, selectedOptionIds, participantName })` identical in Tasks 2(shape)/3. `transferTable(sessionId, callerEmail, targetWaiterId)` service vs `transferTable(sessionId, targetWaiterId)` frontend — deliberately different (caller comes from `Authentication` server-side). `TableTransferred` ctor identical in Tasks 6/6-listeners. `printBillReceipt(billId): {jobId,status}` identical in Tasks 5/9.
- **Deviation from spec:** `waiterId` stores the waiter **email** (verified from `SessionController.createSession`), so `transferTable` sets `session.waiterId = targetUser.getEmail()` and the ownership check compares against `callerEmail`. The endpoint still accepts the target's **id** (`targetWaiterId`) from the picker.
- **Placeholder scan:** Task 8 Step 2 and Task 9 Step 3 give test intent as comments rather than full RTL bodies because the exact provider/wrapper for `TableInformation` must be copied from its sibling tests in the repo; the assertions (banner text, `navigate` spy, button enabled/disabled, mutation args, toast keys) are fully specified. All backend steps carry complete code.
</content>
