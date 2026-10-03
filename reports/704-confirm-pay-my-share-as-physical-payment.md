# Report 704 — Waiter confirmation of "pay my share" is recorded as a physical payment

## 1. Identification
- **Report number:** 704
- **Current Task:** FIX-CONFIRM-DIGITAL-AS-PHYSICAL
- **Predecessor Task:** Report 703 — LANDING-PIXEL-ALWAYS-ON

## 2. Objective
On the web version, a customer paid from the phone ("pay my share", used to earn loyalty points) while the money was received physically by the waiter. When the waiter confirmed it, the accountant's cash-receipts view (the one that opens the drawer) never showed it. In the Hub it did, because customers cannot use the phone there and the waiter registers a physical payment.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `PROGRESS.md`
- `reports/704-confirm-pay-my-share-as-physical-payment.md` — new

## 4. What Changed?
- `PaymentService.confirmDigitalPayment(paymentId, confirmedByEmail)` now behaves like `registerPhysicalPayment`: it requires an open, non-overdue cash shift (same errors), sets the payment to `PHYSICAL`, stores `cashShiftId` and the confirming waiter in `processedBy`, and publishes `PhysicalPaymentRegistered`, which creates the pending receipt the accountant sees. The intent is still created as `DIGITAL`/`PENDING` so the customer and waiter UI are unchanged.
- The stale-amount and voided-bill guards run before the shift check, as before.
- `BillingController` passes the authenticated waiter to the service.
- Loyalty points are not affected: they accrue from `PaymentCompleted`, which does not depend on the payment method.

## 5. Why It Changed?
- No payment gateway exists (`gatewayRef = "STUB-..."`); the "digital" tap is a registered intent and the waiter receives the cash/card at the table (product intent recorded in memory). Keeping the confirmed payment as `DIGITAL` also meant the shift close counted it as a digital sale, not as cash, so the expected-cash figure ignored money that really went through the drawer, and a later refund created no cash-out movement.
- Decision by the user: payments already confirmed as `DIGITAL` stay as they are (history, only restaurants live today); only confirmations from now on are physical.

## Verification
- New tests: confirm records a `PHYSICAL` payment with the shift and waiter and publishes `PhysicalPaymentRegistered`; rejects with no open shift; rejects with an overdue shift. The all-paid test now expects both events.
- `./mvnw test`: 1806/1806 (a first full run had one error in `PortableMinioBootstrapCredentialsIntegrationTest`, which passes alone and on rerun — unrelated and intermittent, not investigated). `./mvnw checkstyle:check`: clean.
- Not done: manual check in the web app; after deploy, repeat the flow (phone pay, waiter confirms) and confirm the pending receipt appears for the accountant.
- Behavior change to know: a waiter can no longer confirm a pay-my-share intent when there is no open shift or it is overdue (same rule as registering a physical payment).
