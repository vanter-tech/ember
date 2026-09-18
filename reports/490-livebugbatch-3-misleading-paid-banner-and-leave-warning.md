# Report 490

## 1. Identification
- **Report number:** 490
- **Task ID:** LIVE-BUG-BATCH 3/7 — misleading "paid and closed" banner + smarter leave-confirmation
- **Predecessor task:** report 489 (bug 2 — activity log missing "X left the table")

## 2. Objective
Live user bug report: if a table has one participant and they leave (possibly by mistake) before
ordering anything, the waiter's table view shows "Mesa pagada y cerrada" even though no bill was
ever created. User-confirmed scope: fix the misleading banner text, and additionally warn the
customer before they leave that doing so will free the table.

## 3. Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/pages/waiter/TableInformation.closedstate.test.tsx`
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/components/FloatingNav.test.tsx` (new)
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`
- `frontend/src/locales/es/common.ts`
- `frontend/src/locales/en/common.ts`

## 4. What Changed?
**Banner fix.** Verified all 3 backend code paths that set a session to `CLOSED`
(`SessionService.leaveSession`, `removeSeat`, `closeSession`): the first two close an empty table
with nothing billable and never create a `Bill`; only `closeSession` (called from
`PaymentCompletedListener`, i.e. an actual completed payment) leaves a real bill behind.
`TableInformation.tsx`'s closed-stay-state banner now checks `billData` (already fetched via
`billingService.getBillState`, which returns `null` when no bill exists) to pick the right message:
`tablePaidClosedBanner` ("Mesa pagada y cerrada...") when a real bill exists, or the new
`tableClosedNoOrderBanner` ("Mesa liberada: no se realizó ningún pedido.") when it doesn't.

`TableInformation.closedstate.test.tsx`'s existing test asserted the *old*, always-"paid" banner
text for a fixture that never seeds a bill (`getBillState` mocked to resolve `null`) — that test was
itself exercising the exact bug. Renamed/repurposed it to assert the new "no order" text, and added
a new test that seeds a real bill and confirms the "paid and closed" banner still shows in that case.

**Leave-confirmation warning.** `FloatingNav.tsx`'s existing "Abandonar mesa?" confirmation
(`AlertDialog`) already existed but never mentioned the table-freeing consequence. Added
`isLastParticipant`/`hasBillableItems` checks (mirroring `SessionService.leaveSession`'s own close
condition) and an extra warning sentence — `leaveTableWillFreeTableWarning` — appended to the dialog
body only when leaving would actually free the table (sole participant, nothing ordered yet). New
`FloatingNav.test.tsx` covers all three cases: warns when solo+no-order, doesn't warn with other
participants present, doesn't warn once something billable has been ordered.

## 5. Why It Changed?
The banner text asserted something false (a payment that never happened) whenever a table was freed
by an empty leave rather than a real checkout — confusing for a waiter reviewing what happened. The
leave-confirmation warning directly addresses the user's "puede ser por error" (could be by mistake)
concern: the dialog already required a click-through, but now the one case that has a real,
irreversible-from-the-diner's-side consequence (freeing the table) says so explicitly.

## Verification
- `cd frontend && pnpm vitest run src/pages/waiter/TableInformation.closedstate.test.tsx` → 3/3 pass.
- `cd frontend && pnpm vitest run src/components/FloatingNav.test.tsx` → 3/3 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **148/148**.
- No backend changes — `./mvnw test` not re-run for this fix.
