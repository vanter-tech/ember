# Report 357 — EMB-FEAT-22: Plan C wrap-up, report + full cross-stack verification

## 1. Identification
- **Report number:** 357
- **Task ID:** EMB-FEAT-22 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 11; docs + verification only, no app code)
- **Predecessor Task:** EMB-FEAT-21 (report 356 — transfer-table modal on the waiter table view)
- **Branch:** `feat/waiter-quick-login-table-actions`

## 2. Objective
Close out Plan C (waiter table-detail action buttons, EMB-FEAT-12..22): run the full backend + frontend verification gate one final time, record the counts, document the 2-device manual smoke checklist, update `PROGRESS.md`, and mark Plan C complete. No production or test code is changed by this task.

## 3. Modified Files
- `reports/357-emb-feat-22-plan-c-verification.md` — **new** (this report)
- `PROGRESS.md` — flip `EMB-FEAT-22` checkbox; new "Last Completed Task" bullet; Plan C marked COMPLETE

## 4. What Changed?
Nothing in `backend/` or `frontend/`. Plan C shipped end-to-end across the ten preceding tasks:

| Task | Report | Layer | Summary |
|------|--------|-------|---------|
| EMB-FEAT-12 | 347 | backend | `SessionService.addItemAsWaiter` — item at `PENDING`, attributed to a participant or `"Mesa"`, fires `ItemAdded` + `KitchenItemsConfirmed`; `409` if a non-voided bill exists |
| EMB-FEAT-13 | 348 | backend | `POST /sessions/{id}/waiter-items` (`@PreAuthorize hasRole('WAITER')`) → `SessionDetailResponseDto` |
| EMB-FEAT-14 | 349 | frontend | `AddItemModal.tsx` + wire "Agregar platillo"; menu search, minimal modifier picker, participant select, qty loop |
| EMB-FEAT-15 | 350 | backend | Extract `ReceiptRenderer` from `PrintingEventListener` (pure refactor, zero behavior change) |
| EMB-FEAT-16 | 351 | backend | `BillReceiptPrintService` + `POST /printing/bills/{billId}/receipt`, gated on `Bill.status == PAID` → `409 BILL_NOT_PAID` |
| EMB-FEAT-17 | 352 | backend | `TableTransferred` event + `SessionActivity.TABLE_TRANSFERRED` + `SessionService.transferTable`; broadcast on `/topic/session/{id}` + `/topic/waiter/{tenantId}` |
| EMB-FEAT-18 | 353 | backend | `GET /identity/waiters` (`WaiterSummary` — no `passwordHash`/`pinHash`) + `POST /sessions/{id}/transfer` |
| EMB-FEAT-19 | 354 | frontend | `TableInformation.tsx` — remove the CLOSED auto-redirect; "mesa pagada y cerrada" stay-state banner; `actionsDisabled` when `status !== 'OPEN'`; still redirect on a fresh mount of an already-closed session |
| EMB-FEAT-20 | 355 | frontend | Wire "Imprimir cuenta" (`printingService.printBillReceipt`), enabled only when `status === 'CLOSED'`; SENT / queued-no-agent / failed toasts |
| EMB-FEAT-21 | 356 | frontend | `TransferTableModal.tsx` (excludes self, navigates to `/waiter/tables` on success) + `websocket.ts` `TABLE_TRANSFERRED` handler; wire "Transferir" |

All three previously-dead header buttons in `TableInformation.tsx` — **Agregar platillo**, **Imprimir cuenta**, **Transferir** — are now functional.

## 5. Why It Changed?
`CLAUDE.md §7` requires every plan to end with a single wrap-up task: one final green run of the full verification gate (not just the per-task scoped subsets), a report, and a `PROGRESS.md` sync so the next session starts from an accurate state. Plan C is now complete and ready to merge.

## 6. Verification (this task)
Full gate, run fresh on the tip of `feat/waiter-quick-login-table-actions` (commit `33ef8f3`):

- **Backend** — `./mvnw test` → exit 0; surefire aggregate **992 tests, 0 failures, 0 errors, 0 skipped**.
- **Frontend build** — `pnpm run build` (`tsc -b && vite build`) → PASS, **0 TypeScript errors** (built in ~2s; the >500 kB chunk notice is the long-standing bundle-size warning, not an error).
- **Frontend lint** — `pnpm run lint` → **0 errors**, 16 pre-existing warnings (none in any file Plan C touched).
- **Frontend tests** — `pnpm run test:run` → **66/66 passed** (21 files).

## 7. Manual 2-device smoke (checklist — run by the maintainer)
With `./mvnw spring-boot:run` + `pnpm run dev`, as a WAITER on `/waiter/tables/:id`:

1. **Add item** — open "Agregar platillo", search a dish, pick modifiers if any, choose a participant or "Mesa", set qty, submit → the item appears in the order list at `PENDING` and the KDS queue receives it.
2. **Charge + settle** — request the bill, mark every split paid, "Cobrar y cerrar mesa" → the view **stays on the page**, the amber "mesa pagada y cerrada" banner shows, and only "Imprimir cuenta" is enabled (Transferir / Agregar / per-item trash / split actions all disabled).
3. **Print bill** — click "Imprimir cuenta" → toast: "enviado a la impresora" with a print agent connected, or "en cola (sin agente)" without one. A second click reprints (no `409`, bill is `PAID`).
4. **Re-entry block** — navigate away, then back to `/waiter/tables/:id` for that closed session → redirected to `/waiter/tables` (no toast).
5. **Transfer** — on a fresh OPEN table, open a second browser signed in as another WAITER; from the first, "Transferir" → pick the second waiter → the table leaves the first waiter's floor list and appears on the second's; the first browser navigates to `/waiter/tables`.

## 8. Next
Plan C (EMB-FEAT-12..22) is **COMPLETE**. The whole EMB-FEAT effort (Plan B quick-login EMB-FEAT-00..11 + Plan C table-detail actions EMB-FEAT-12..22) is done on `feat/waiter-quick-login-table-actions`. Next: maintainer runs the manual smoke above, then merges the branch to `main` (short-lived-branch workflow, `CLAUDE.md` / PROGRESS.md BRANCH EVENT 6).
