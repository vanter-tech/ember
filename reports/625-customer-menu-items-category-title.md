# Report 625 — CUSTOMER-MENU-ITEMS-CATEGORY-TITLE

## 1. Identification
- Report: 625
- Task ID: CUSTOMER-MENU-ITEMS-CATEGORY-TITLE
- Predecessor: CUSTOMER-MENU-CATEGORY-ITEM-COUNT (report 624)

## 2. Objective
Report 615 removed the generic "Carta Digital" title from the `items` step without replacing it with anything, leaving the customer with no on-screen indication of which category they're browsing (only the back button implies it).

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- Extracted `activeCategoryData` (was inlined as an anonymous `.find()` just for `itemsCategory`) so its `name` is available to render.
- `items` step header restructured back into the title-left / actions-right row it had before report 615, but the title is now the **selected category's name** (`activeCategoryData?.name`) instead of the old generic "Carta Digital" — and it's visible at every breakpoint (previously the table-code badge/"Ver cuenta" row was desktop-only via `hidden sm:flex`; that part still is, only the title is new and unconditional).

## 5. Why It Changed?
Flagged as the more important of two gaps noticed while reviewing the populated categories view: losing the category-name context when browsing items was a real regression from the earlier title removal, not just a data artifact.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`**: entering "Pollo" now shows "Pollo" as the items-step title, in the same position the old generic title occupied, with the desktop table-code/"Ver cuenta" row unchanged on the right.

## Open item (not done this round)
Empty categories (0 platillos) still show a blank grid with no message when opened — flagged alongside this one but deferred since the user asked to prioritize this fix first.
