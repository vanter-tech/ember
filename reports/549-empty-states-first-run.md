# Report 549 — EMPTY-STATES-FIRST-RUN

## 1. Identification
- **Report:** 549
- **Task ID:** EMPTY-STATES-FIRST-RUN (workflow-review follow-up, 2026-09-23) — not from `AUDIT_BLUEPRINT.md`
- **Predecessor Task:** report 548 — ADMIN-PASSWORD-RESET (F-25)
- **Branch:** none yet — pending commit on `main`

## 2. Objective
A brand-new tenant's first login shows several genuinely blank screens instead of a "nothing here yet" message — `admin/inventory` and its subtabs, `kitchen/orders`. Flagged live by the user as reading like a broken build on exactly the screen that matters most (first impression / onboarding). Audited the rest of the app for the same pattern and found one more real gap (`waiter/Tables`); everything else already had its own empty-state handling.

## 3. Modified Files
- `frontend/src/components/EmptyState.tsx` (new)
- `frontend/src/components/EmptyState.test.tsx` (new)
- `frontend/src/pages/admin/Category.tsx`
- `frontend/src/pages/admin/Category.test.tsx` (new)
- `frontend/src/pages/admin/Inventory.tsx`
- `frontend/src/pages/admin/Inventory.test.tsx` (new)
- `frontend/src/pages/admin/ListMenuItem.tsx`
- `frontend/src/pages/admin/ListMenuItem.test.tsx` (new)
- `frontend/src/pages/admin/ModifierGroups.tsx`
- `frontend/src/pages/admin/ModifierGroups.test.tsx` (new)
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- `frontend/src/pages/kitchen/OrdersDisplay.test.tsx` (new)
- `frontend/src/pages/waiter/Tables.tsx`
- `frontend/src/pages/waiter/Tables.test.tsx` (new)
- `frontend/src/locales/{es,en}/admin.ts`
- `frontend/src/locales/{es,en}/kitchen.ts`
- `frontend/src/locales/{es,en}/waiter.ts`

## 4. What Changed?
- New shared `EmptyState` component (icon + title + optional description + optional action), namespace-agnostic so admin/kitchen/waiter pages each supply their own translated copy.
- Wired it into the 6 confirmed blank-screen gaps: `Category`, `Inventory`, `ListMenuItem`, `ModifierGroups` (all `admin/inventory/*` subtabs), `OrdersDisplay` (`kitchen/orders`), and `Tables`' main grid (`waiter/Tables`, only shown when the cash register is open — when it's closed, the existing "open the register" overlay already covers that case).
- Left `admin/staff` (`StaffGrid`), the 3 `admin/analytics` charts, and `admin/cashRegister` (`ShiftHistoryTable`) untouched — each already had its own empty-state handling, confirmed during the audit.
- No CTA button added to the "create X" empty states (originally planned): `TopNav.tsx` already renders a contextual "+ Crear categoría/producto/grupo" button on every one of these routes (`CREATE_CATEGORY`/`CREATE_INVENTORY_ITEM`/`CREATE_MODIFIER_GROUP`), so a second button inside the empty state would duplicate it. Kept the component's `action` prop for future reuse where no such button already exists.
- New tests per touched page (empty-array render + a "still renders real data" guard) plus a small isolated test for `EmptyState` itself — none of the 6 touched pages had any prior test coverage at all, so these are net-new, narrowly scoped to the empty-state branch rather than full page coverage.

## 5. Why It Changed?
A restaurant's first login sequence necessarily starts with zero categories/products/modifiers/inventory and zero kitchen orders — that's not an edge case, it's the very first thing every new tenant (including the pilot restaurant) sees. A blank white page there reads as "this is broken," directly undermining trust at the worst possible moment. This is pure frontend polish — no backend change, no migration.

## 6. Verification
- `pnpm run build` (`tsc -b && vite build`) — clean.
- `pnpm run lint` — 0 errors (15 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 213/213 (61 files, +8 new test files/16 new tests).
