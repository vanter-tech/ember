# Report 317 — Task A: current-bill fetch endpoint (payment-flow bug cluster)

## 1. Identification
- **Report number:** 317
- **Current Task:** Task A — `GET /billing/sessions/{sessionId}/bill` so a page can rebuild
  its bill view without a live `BILL_READY` WebSocket frame
- **Predecessor Task:** report 316 (landing-video-modal)
- **Branch:** `feat/hpd-14-monitoring` (continuing the ad-hoc track reports 287–316 run on)

## 2. Objective
First of four tasks addressing a reported payment-flow bug cluster. Root cause of the
worst symptom (a diner who leaves during payment and rejoins no longer sees the bill;
the waiter's "Marcar pagado" / "Confirmar digital" buttons vanish after any reload):
the frontend had **no way to fetch an existing bill** — the waiter `['bill', id]` query
was a `Promise.resolve(undefined)` / `enabled:false` stub, and the customer `Bill.tsx`
read only the persisted zustand store. Both were populated solely by the ephemeral
`BILL_READY` STOMP broadcast. This task adds the missing read path. No settle/close or
leave-table logic (Tasks C / D).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/dto/WaiterBillStateResponse.java` — **new**
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java` — `getBillState(sessionId)`
- `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java` — `GET /billing/sessions/{sessionId}/bill`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` — `isParticipant(sessionId, userEmail)` helper
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java` — `@MockBean SessionService` + 5 endpoint tests
- `frontend/src/lib/api.ts` — `billingService.getBillState(sessionId)`
- `frontend/src/pages/waiter/TableInformation.tsx` — real `queryFn` for `['bill', id]`
- `frontend/src/pages/customer/Bill.tsx` — mount query → `setBillReady` rehydrate

## 4. What Changed?
- **`WaiterBillStateResponse`** record: `{ id, total, splits: List<BillSplit>,
  pendingDigitalPayments: List<PendingDigitalPayment> }` with a nested
  `PendingDigitalPayment(id, participantName, amount)`. Mirrors the client's existing
  `WaiterBillState` shape (previously assembled only from WS frames).
- **`PaymentService.getBillState(String sessionId)`**: loads the non-voided bill via
  `billRepository.findBySessionIdAndStatusNot(…, VOIDED)`, returns `null` when none
  exists; otherwise its splits (`billSplitRepository.findByBillId`) plus every
  `DIGITAL` / `PENDING` payment mapped to `PendingDigitalPayment`. Read-only, no
  `@Transactional` (consistent with `listPayments`).
- **`BillingController.getBillState`**: `GET /billing/sessions/{sessionId}/bill`,
  `@PreAuthorize("hasAnyRole('WAITER','CUSTOMER')")`. A CUSTOMER caller is gated by
  `sessionService.isParticipant(...)` → `AccessDeniedException` (403) if not a
  participant — same guard pattern as `SessionController.getSession`. Returns
  `ResponseEntity` 200 with body, or **204** when no bill exists yet.
- **`SessionService.isParticipant`**: resolves the email to a user id and checks the
  session's participant list. Extracted as a reusable helper (Task D will reuse it).
- **`billingService.getBillState`** (api.ts): `GET`s the endpoint, normalises a 204 /
  empty body to `null`, else returns `WaiterBillState`.
- **`TableInformation.tsx`**: the `['bill', id]` query now really fetches
  (`queryFn: () => billingService.getBillState(id!)`, `enabled: !!id`). The existing
  WebSocket `setQueryData` merge handlers write the same key and are unchanged, so a
  live bill still updates optimistically; a reopened table now also shows the bill.
- **`Bill.tsx`**: a `['billState', sessionId]` query (`enabled: !!sessionId && !bill`,
  `retry:false`) runs on mount; an effect feeds a returned bill into the session store
  via `setBillReady(...)`. Once the store holds the bill the query disables itself and
  live WS split updates take over — a rejoining diner now sees the bill and splits.
- **`BillingControllerTest`**: added `@MockBean SessionService` (the `@WebMvcTest`
  slice needs it now that the controller depends on it — this was the failing-context
  error the first full run surfaced) and 5 tests: waiter 200-with-bill, waiter 204,
  participant-customer 200, non-participant-customer 403, kitchen 403.

## 5. Why It Changed?
The bill, its splits and the pending-digital-payment list existed server-side but were
only ever pushed once, over STOMP, at the instant billing was requested. Any client
that reloaded, re-logged-in, or joined the session afterwards had no way to obtain
them, so:
- a diner who paid then had a tablemate leave-and-return saw an empty bill screen;
- the waiter, after any refresh, lost the split panel and with it the only UI path to
  register a physical payment or confirm a digital one — which is why physical
  payments and the caja payment record felt missing (they were unreachable, not
  broken).

A plain authenticated GET that returns the current persisted bill state closes that
gap without touching the payment-mutation or session-lifecycle logic, keeping the
change surgical and letting Tasks B–D build on a bill view that is now always
reachable.

## Verification
- `cd backend && ./mvnw test` — **BUILD SUCCESS, 905 tests, 0 failures, 0 errors**
  (900 prior + 5 new). First run failed on `BillingControllerTest` context load
  (missing `SessionService` bean in the slice); fixed with the `@MockBean` and re-run
  green.
- `cd frontend && pnpm run build` — green (`tsc -b` + `vite build`, built in ~11s).
- `cd frontend && pnpm run lint` — **0 errors**, 17 warnings, all pre-existing (none
  in the changed lines; the `TableInformation.tsx:72` `exhaustive-deps` warning is on
  the untouched CLOSED-status effect).
- Not exercised live in a browser this session (no `claude-in-chrome`); behaviour
  reasoning is above and covered by the new controller tests.
