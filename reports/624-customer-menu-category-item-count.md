# Report 624 — CUSTOMER-MENU-CATEGORY-ITEM-COUNT

## 1. Identification
- Report: 624
- Task ID: CUSTOMER-MENU-CATEGORY-ITEM-COUNT
- Predecessor: ADMIN-CATEGORIES-PAGINATION-TRANSITION (report 623)

## 2. Objective
Address a gap flagged after reviewing the now-populated categories view: the customer couldn't see how many dishes each category holds before opening it, unlike the admin categories page which already shows a "X Productos" count.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- Desktop grid: each category `Card` gets a white pill `Badge` at the top-right (`absolute top-4 right-4`), mirroring the item-card price badge's styling but on the opposite corner, showing `t('categoryItemCountLabel', { count: category.items?.length ?? 0 })`.
- Mobile list: each row gets the same badge (outline variant, muted text) between the name/description block and the chevron.
- Both read `category.items?.length` directly — no new query, the `/menu` response already nests each category's items.
- Added `categoryItemCountLabel` i18n key: `{{count}} platillos` (ES) / `{{count}} dishes` (EN).

## 5. Why It Changed?
Direct follow-up after being asked for an honest take on the categories view now that it has real data — flagged the missing item-count signal (present in the admin equivalent) as the concrete gap, and was asked to add it.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`** (desktop width, running dev server + real tenant data): every category card shows its correct count ("1 platillos" for Pollo, "0 platillos" for the empty ones). Mobile-width re-check blocked again by `resize_window` not taking effect in this browser session — not re-verified visually, but it reuses the same `Badge` component and row layout already used elsewhere in this exact file.
