# Report 136 — tweak-waiter-tables-caja-overlay-style

## 1. Identification
- **Report #:** 136
- **Task ID:** tweak-waiter-tables-caja-overlay-style
- **Predecessor Task:** bugfix-waiter-tables-require-open-caja (report 135)

## 2. Objective
Follow-up styling tweak to the report-135 "no open caja" gate on `/waiter/tables`: keep the disabled tables visibly present (blurred, not opacity-dimmed) behind the overlay, and make the blocking message text red.

## 3. Modified Files
- `frontend/src/pages/waiter/Tables.tsx`

## 4. What Changed?
- Disabled `Card` styling changed from `opacity-50` to `blur-sm` (still `pointer-events-none cursor-not-allowed`), so tables render visible-but-blurred instead of dimmed.
- Overlay `div` dropped its own `backdrop-blur-sm` (redundant now that the cards blur directly) and lightened its wash to `bg-white/40`.
- Overlay message text color changed from `text-zinc-600` to `text-[#8c1717]` (the app's brand red, already used for headers/prices elsewhere in this file).

## 5. Why It Changed?
User feedback on report 135's implementation: wanted the blocked tables visibly present in the background (blurred) rather than just faded, and the warning text in red for higher visual urgency.
