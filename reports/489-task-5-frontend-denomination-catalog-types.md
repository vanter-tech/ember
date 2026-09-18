# Report 489

## 1. Identification
- **Report number:** 489
- **Task ID:** CASH-SHIFT-DENOMINATION-COUNT Task 5 — frontend denomination constants + type patch
- **Predecessor task:** report 488 (Task 4 — `CashShiftController` wiring)

## 2. Objective
Add the frontend's 14-entry Nicaragua denomination catalog (mirroring the backend's
`NicaraguaDenominations.ALL`) plus a `sumBreakdown` helper, and patch `backend-types.ts` so the
frontend's generated API types match the backend's Task 3 DTO changes.

## 3. Modified Files
- `frontend/src/lib/denominations.ts` (new)
- `frontend/src/lib/denominations.test.ts` (new)
- `frontend/src/lib/backend-types.ts`

## 4. What Changed?
`denominations.ts` exports `Denomination`/`DenominationKind`/`DenominationCount` types,
`NICARAGUA_DENOMINATIONS` (14 entries: 7 `BILL` + 7 `COIN`, `bill_10`/`coin_10` as distinct C$10
entries), and `sumBreakdown()` (value × quantity across rows, ignores unknown ids, 0 for empty).

`backend-types.ts` was hand-patched, not regenerated — no local backend was running on :8080 to
regen against, and the plan's established fallback (reports 421/483) is to hand-patch when a real
regen isn't available:
- Added a `DenominationCount` schema (`denominationId?`, `quantity?`).
- `OpenShiftRequest` gains `breakdown?: DenominationCount[]`.
- `CloseShiftRequest` gains `breakdown?: DenominationCount[]` and `notes?: string`.
- `CashShiftResponse` gains `openingBreakdown?`, `closingBreakdown?`, `closeNotes?` (all
  `DenominationCount[]`/`string`), appended after the existing `prolongCount` field.

All 3 shapes were verified against the current file before patching (`countedCash`/`openingFloat`
were the only existing fields) to guarantee the patch lands exactly as the backend's actual DTOs.

## 5. Why It Changed?
The frontend needs its own denomination catalog to render the counting grid (Task 6) without a
shared package between frontend and backend — this mirrors how every other shared enum/constant in
this codebase is kept in sync by hand. The type patch is required before Tasks 6-9 can compile
against `breakdown`/`openingBreakdown`/`closingBreakdown`/`closeNotes` on the generated API types.

## Verification
- `cd frontend && pnpm vitest run src/lib/denominations.test.ts` → 5/5 pass.
- `cd frontend && pnpm run build` → clean (`tsc -b && vite build`).
