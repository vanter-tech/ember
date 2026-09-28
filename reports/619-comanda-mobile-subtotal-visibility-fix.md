# Report 619 — COMANDA-MOBILE-SUBTOTAL-VISIBILITY-FIX

## 1. Identification
- Report: 619
- Task ID: COMANDA-MOBILE-SUBTOTAL-VISIBILITY-FIX
- Predecessor: COMANDA-VIEW-HEADER-AND-CARD-FIXES (report 618)

## 2. Objective
Report 618's fix for the subtotal overflowing the participant card wasn't enough on mobile — verified live with `claude-in-chrome` against the running dev server (`localhost:5173`, real table session): at mobile width the "Subtotal $X" block was almost entirely clipped by the card's `overflow-hidden`, showing only a stray "S" and "$" at the edge, because the name/avatar block (`flex-1`) and the price block (`shrink-0`) still couldn't both fit in one row on a narrow card.

## 3. Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`

## 4. What Changed?
Participant card's `CardTitle` now stacks vertically on mobile and returns to the previous row layout at `sm:` and up:
- `CardTitle`: `flex justify-between items-center` → `flex flex-col sm:flex-row sm:items-center sm:justify-between`.
- Name/avatar block: `flex-1` → `sm:flex-1` (only grows to fill remaining row width once it's actually in a row); padding `p-5` → `p-3 sm:p-5` (less wasted horizontal space on mobile).
- Subtotal block: was always a column; now `flex-row justify-between items-center px-3` on mobile (label left, price right, full width) and `sm:flex-col sm:items-start sm:gap-2` to match the original desktop look.

## 5. Why It Changed?
Direct instruction to verify with `claude-in-chrome` rather than assume the previous fix was sufficient — doing so caught that "prevent shrinking" alone doesn't help when there simply isn't enough row width for both blocks on a phone; stacking is the correct fix at that size.

## Verification
- **Live-verified with `claude-in-chrome`** against the running dev server + backend (real table session, `Fernand-O-bando` / "Anfitrión" / `POLLO ROSTIZADO`): confirmed the bug at ~393px width (subtotal clipped to "S…$"), applied the fix, confirmed it renders correctly after Vite HMR ("Subtotal $1000.00" fully visible, name and badge intact). Desktop width couldn't be re-confirmed in this session (the browser window was stuck at a fixed 393×852 viewport, resize calls didn't take effect), but the `sm:` classes reuse the exact same values already confirmed working at 1568px before this change, unconditionally instead of behind the breakpoint.
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
