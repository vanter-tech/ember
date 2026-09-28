# Report 618 — COMANDA-VIEW-HEADER-AND-CARD-FIXES

## 1. Identification
- Report: 618
- Task ID: COMANDA-VIEW-HEADER-AND-CARD-FIXES
- Predecessor: CUSTOMER-FLOATING-ACTIONS-NAV-FIX-AND-HOME-SCOPE (report 617)

## 2. Objective
`ComandaView.tsx` never got the header sizing pass applied to `Menu.tsx`, and its per-participant card had two layout bugs: an oversized name and a subtotal that could get pushed outside the card.

## 3. Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`

## 4. What Changed?
- Back button: `w-15 h-15` → `h-10 w-10 sm:h-13 sm:w-13` (icon `w-5 h-5` → `w-4 h-4 sm:w-5 sm:h-5`), matching `Menu.tsx`'s header sizing.
- Participant card `CardTitle` was a bare `flex justify-between items-center` with two children, neither able to shrink — a long participant name pushed the subtotal past the card's edge. Fixed by giving the name block `min-w-0 flex-1` (+ `truncate` on the name itself) and the subtotal block `shrink-0`, so the price always stays inside the card and the name truncates instead of overflowing.
- Participant name size: `text-2xl` → `text-lg`.

## 5. Why It Changed?
Direct visual feedback: the comanda header wasn't updated alongside the menu header, the ordering participant's name read oversized, and the subtotal visibly spilled out of the card on longer names.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
