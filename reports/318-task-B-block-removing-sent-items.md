# Report 318 — Task B: block removing items already sent to the kitchen

## 1. Identification
- **Report number:** 318
- **Current Task:** Task B (payment-flow bug cluster) — a diner/waiter must not be
  able to delete an order item once it has left the DRAFT/PENDING stage
- **Predecessor Task:** report 317 (Task A — current-bill fetch endpoint)
- **Branch:** `feat/hpd-14-monitoring`

## 2. Objective
`SessionService.removeItem` only rejected items in `PREPARING`; `READY` and
`DELIVERED` items could still be deleted, which silently dropped billable food that
the kitchen had already made or handed over — one of the reported causes of a waiter
being unable to close a partially-paid table without "losing" delivered items. This
task hardens the backend guard and disables the matching trash button in the waiter
UI. No settle/close logic (Task C).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` — widen `removeItem` guard
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java` — parameterize the guard test over `PREPARING`/`READY`/`DELIVERED`
- `frontend/src/pages/waiter/TableInformation.tsx` — disable + tooltip the per-item trash button when sent to kitchen
- `frontend/src/locales/es/waiter.ts` — `cannotRemoveSentItem`
- `frontend/src/locales/en/waiter.ts` — `cannotRemoveSentItem`

## 4. What Changed?
- **`removeItem` guard**: the `item.getStatus() == PREPARING` check now also matches
  `READY` and `DELIVERED`; the `IllegalStateException` message changed from
  "already being prepared" to "already been sent to the kitchen" (covers all three
  states). `DRAFT` and `PENDING` items remain freely removable by owner or waiter.
- **`SessionServiceTest`**: `removeItem_throwsWhenItemIsPreparing` became
  `removeItem_throwsWhenItemAlreadySentToKitchen`, a `@ParameterizedTest`
  `@EnumSource(names = {"PREPARING","READY","DELIVERED"})` asserting `IllegalStateException`
  with message containing "sent to the kitchen". Added the two
  `org.junit.jupiter.params` imports.
- **`TableInformation.tsx`**: the item `.map()` now computes
  `isSentToKitchen = status in {PREPARING, READY, DELIVERED}`; the destructive
  `Button` gets `disabled={isSentToKitchen}` and `title={t('cannotRemoveSentItem')}`
  when locked, so the waiter can no longer open the delete modal for those rows.
- **i18n**: `cannotRemoveSentItem` — ES "No se puede quitar: el artículo ya fue
  enviado a cocina." / EN "Cannot remove: this item has already been sent to the
  kitchen."

## 5. Why It Changed?
Deleting a `READY`/`DELIVERED` item removed real food from the session and its bill
after the kitchen had already acted on it, so the totals no longer matched what was
served. The waiter then had no clean way to close a partially-paid table — the only
apparent option was to delete outstanding delivered items, which is exactly the data
loss this guard now prevents. Enforcing it server-side is authoritative; disabling
the button is the matching affordance so the waiter isn't offered an action the API
will reject.

## Verification
- `cd backend && ./mvnw test` — **exit 0**, aggregated surefire: **911 tests, 0
  failures, 0 errors, 0 skipped** (`SessionServiceTest` 45).
- `cd frontend && pnpm run build` — green (`tsc -b` + `vite build`, ~3.6s).
- `cd frontend && pnpm run lint` — **0 errors**, 17 warnings, all pre-existing (none
  in the changed lines).
- Not exercised in a live browser this session (no `claude-in-chrome`).
