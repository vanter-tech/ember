# Report 620 — COMANDA-SUBTOTAL-CARD-FOOTER

## 1. Identification
- Report: 620
- Task ID: COMANDA-SUBTOTAL-CARD-FOOTER
- Predecessor: COMANDA-MOBILE-SUBTOTAL-VISIBILITY-FIX (report 619)

## 2. Objective
Move the participant card's subtotal out of the header row (next to the name) to the last line of the card, below the ordered dishes.

## 3. Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`

## 4. What Changed?
- `CardHeader`/`CardTitle` now only holds the avatar + name + host/participant badge (dropped the subtotal block and the `flex-col sm:flex-row` split from report 619, no longer needed here).
- Added a `CardFooter` (shadcn's existing component, not previously used in this file) after `CardContent`'s dish list, showing "Subtotal $X" as the card's last line with a top border separating it from the items.

## 5. Why It Changed?
Direct layout request — the subtotal reads more naturally as a running total under the products it's summing, not next to the person's name.

## Verification
- **Live-verified with `claude-in-chrome`** against the running dev server + the same real table session used in report 619: subtotal now renders as the last line of the card, below "POLLO ROSTIZADO $500.00", with a clear separator; header shows just the avatar/name/badge.
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
