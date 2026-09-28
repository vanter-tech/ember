# Report 630 — CUSTOMER-MENU-MOBILE-CATEGORY-CARD-RESTACK

## 1. Identification
- Report: 630
- Task ID: CUSTOMER-MENU-MOBILE-CATEGORY-CARD-RESTACK
- Predecessor: CUSTOMER-MENU-DESKTOP-GRID-LAST-ROW-FILL (report 629)

## 2. Objective
Undo the `#8c1717` background applied to the mobile category description (report 628) and restack the row's content: name, then the item-count badge below it, then the description below the badge.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
Reverted the row back to a single `flex items-center` layout (thumbnail + text column + chevron, same shape as before report 628), but the text column is now `flex-col gap-1` holding three stacked lines in order: name (`truncate`), the `categoryItemCountLabel` badge (`w-fit`, same outline style as before), then the description (`text-sm text-gray-500 line-clamp-2`, no colored background). The `#8c1717` block from report 628 is gone entirely.

## 5. Why It Changed?
Direct follow-up: the colored description block wasn't what was wanted after seeing it live — the requested layout is a plain vertical stack (title → badge → description) within the same row as the thumbnail.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`** at ~390px width: every row now shows name, then the platillos-count badge, then the description, stacked in that order with no colored background.
