# Report 124 — task-EMB-PAY-01

**Predecessor Task:** bugfix-waiter-tables-realtime-orders (report 123)

## Objective
Give the payment cycle a real-time backbone: let a waiter trigger "calculate + split the bill" in one call, and broadcast the resulting bill/splits and every subsequent payment event to everyone on the session's WebSocket topic — the prerequisite for a customer-facing bill/pay screen (there was previously no `GET` endpoint for `Bill`/`BillSplit`, so push-over-WebSocket is the only channel).

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/dto/RequestBillingRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/dto/SplitPaidMessage.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/dto/DigitalPaymentInitiatedMessage.java` (new)
- `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## What Changed?
- `POST /billing/sessions/{sessionId}/request` (WAITER, 202 Accepted, no body): publishes the existing `BillingRequested` event. That event/listener/`BillReadyMessage` broadcast (`billing/event/BillingRequested.java`, `billing/listener/BillingEventListener.java`) already existed, fully unit-tested, but nothing in the app ever published it — this endpoint is the missing wire. Spring's event bus is synchronous here (per project convention), so `calculateBill`+split+broadcast all complete before the HTTP response returns, and any `IllegalStateException` (already billed, session not open, no billable items) still surfaces as the existing 409 mapping.
- `PaymentService.registerPhysicalPayment` and `confirmDigitalPayment` now broadcast `SplitPaidMessage` (`SPLIT_PAID`) to `/topic/session/{sessionId}` right after marking a `BillSplit` paid, so every viewer (customer and waiter) sees per-participant payment status live instead of only learning about the *final* payment via the existing `SESSION_CLOSED` broadcast.
- `PaymentService.initiateDigitalPayment` now broadcasts `DigitalPaymentInitiatedMessage` (`DIGITAL_PAYMENT_INITIATED`) — needed because `confirmDigitalPayment` is intentionally WAITER-only (manual confirm on the stub gateway) and there was otherwise no channel telling the waiter a digital payment even exists to confirm.
- `PaymentService` gained a `SimpMessagingTemplate` dependency for the above.
- Test updates: `BillingControllerTest` covers the new endpoint's role gating (202/403/403) — a direct `@MockBean ApplicationEventPublisher` interaction check was attempted first but dropped after confirming Spring resolves `ApplicationEventPublisher` via `registerResolvableDependency` to the real `ApplicationContext`, bypassing `@MockBean` in a `@WebMvcTest` slice (a documented Spring Test limitation, not a bug in the endpoint). `PaymentServiceTest` gained a `SimpMessagingTemplate` mock plus three new broadcast-content tests. `SecurityAuditTest` gained the new route to its 401 matrix.

## Why It Changed?
The user asked to complete the customer-facing payment cycle on the frontend, but investigation showed two hard blockers that no frontend code could work around: no read endpoint for `Bill`/`BillSplit`, and the one broadcast path built for this was orphaned dead code. The user chose (via explicit prompt) the "full cycle, small backend touch" option over "waiter-only, zero backend changes." The digital-payment-initiated broadcast was a same-shaped follow-on gap discovered while designing the waiter's "confirm digital payment" UI in task-EMB-PAY-03.

## Verification
`cd backend && ./mvnw test` — full suite green (exit code 0), including the 3 new/updated test files.
