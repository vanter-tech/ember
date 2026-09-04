# Report 355 — EMB-FEAT-20: wire the "Imprimir cuenta" button

## Identification
- **Report number:** 355
- **Current Task ID:** EMB-FEAT-20 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 9)
- **Predecessor Task:** EMB-FEAT-19 (report 354 — TableInformation closed stay-state + remove auto-redirect)

## Objective
Wire the dead "Imprimir cuenta" header button on the waiter table-detail view to the
on-demand receipt endpoint `POST /printing/bills/{billId}/receipt` (EMB-FEAT-16), enabled
only once the table is paid & closed (`status === 'CLOSED'` with a fetched bill). Frontend-only.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`
- `frontend/src/pages/waiter/TableInformation.printbill.test.tsx` (new)

## What Changed?
- **`api.ts`** — `printingService` gained `printBillReceipt(billId: number): Promise<{ jobId: string; status: string }>`
  → `POST /printing/bills/${billId}/receipt` (no body), returns the parsed `{ jobId, status }`.
- **`locales/{es,en}/waiter.ts`** — 3 new parity keys:
  `printSentToast` (ES `'Cuenta enviada a la impresora'` / EN `'Bill sent to the printer'`),
  `printQueuedNoAgentToast` (ES `'Cuenta en cola (sin impresora conectada)'` / EN `'Bill queued (no printer connected)'`),
  `printFailedToast` (ES `'No se pudo imprimir la cuenta'` / EN `'Could not print the bill'`).
- **`TableInformation.tsx`**
  - `import { … printingService … }` added to the existing `@/lib/api` import.
  - New `printBillMutation` (`useMutation`): `mutationFn: (billId: number) => printingService.printBillReceipt(billId)`;
    `onSuccess` → `toast.success(res.status === 'PENDING' ? t('printQueuedNoAgentToast') : t('printSentToast'))`
    (a `PENDING` job means it was persisted but no print agent picked it up — everything else,
    e.g. `SENT`, means it reached a printer); `onError` → `toast.error(t('printFailedToast'))`.
  - The "Imprimir cuenta" `Button` gained
    `disabled={!billData || sessionData?.status !== 'CLOSED' || printBillMutation.isPending}`
    and `onClick={() => billData && printBillMutation.mutate(billData.id)}`.
- **`TableInformation.printbill.test.tsx`** (new) — 3 tests, wrapper adapted from
  `TableInformation.closedstate.test.tsx` (`QueryClientProvider` + `MemoryRouter`/`Routes` for
  `:id`). Mocks `@/lib/api` (`SessionTableService.sessionInformation`, `billingService.getBillState`,
  `printingService.printBillReceipt`), `react-hot-toast` (spy `success`/`error`), stubs
  `useNavigate` and `SectionTour`. Test 1: `OPEN` → button disabled. Test 2: `OPEN` then
  `qc.setQueryData(['sessionDetails','s1'], …CLOSED)` → click → `printBillReceipt(5)` called,
  `toast.success('Cuenta enviada a la impresora')`. Test 3: same transition, job resolves
  `{ status: 'PENDING' }` → `toast.success('Cuenta en cola (sin impresora conectada)')`.

## Why It Changed?
The button has been a visual placeholder since the view was built. EMB-FEAT-16 added the
backend endpoint and EMB-FEAT-19 replaced the CLOSED auto-redirect with a stay-state so the
waiter is still on the page when the bill is payable — this task connects the two, giving the
waiter a way to print (or reprint) the customer's receipt from a paid-and-closed table. The
`CLOSED`-only gate matches the backend's `Bill.status == PAID` precondition (a `409
BILL_NOT_PAID` otherwise); the `!billData` guard covers the brief window before `['bill', id]`
resolves.

## Plan Deviations
- Test wrapper copied from `TableInformation.closedstate.test.tsx` (sibling, same mock shape)
  rather than the plan's sketch; the plan left the `react-hot-toast` mock unspecified — added a
  standard `vi.fn()` spy mock. Assertions use real ES copy (repo does not mock i18n).
- Button-name matcher is `/Imprimir cuenta/i` (`printBillLabel`), the real rendered ES text,
  not a raw key.

## Verification
- `pnpm run test:run TableInformation.printbill` → **3/3 PASS** (RED confirmed first: all 3
  failed before the wiring — button enabled while OPEN, no mutation call, no toast)
- `pnpm run test:run TableInformation` → **5/5 PASS** (2 files: closedstate + printbill)
- `pnpm run test:run` (full) → **64/64 PASS** (20 files; was 61/61 + 1 new file / 3 new tests)
- `pnpm run build` → **PASS**, 0 TypeScript errors
- `pnpm run lint` → **0 errors** (16 pre-existing warnings, none in the touched files)
