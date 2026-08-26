# Report 213: Use TopNav's shared "+" action button instead of a local one

**Predecessor:** report 212

## Objective
`ModifierGroups.tsx` and `Inventory.tsx` each had their own page-local "create" button in the page
header — every other admin list page (categories, menu items, staff) relies on `TopNav`'s single
route-aware action button instead. User-requested consistency fix.

## Modified Files
- `frontend/src/components/TopNav.tsx`
- `frontend/src/pages/admin/ModifierGroups.tsx`
- `frontend/src/pages/admin/Inventory.tsx`
- `frontend/src/locales/es/common.ts`
- `frontend/src/locales/en/common.ts`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## What Changed?
`TopNav.tsx`'s existing `isMenuItemRoute`/`isCategoryRoute`/`/admin/employees` else-if chain gained
two more branches: `/admin/modifier-groups` → `actionType: 'CREATE_MODIFIER_GROUP'`, and
`/admin/inventory` → `actionType: 'CREATE_INVENTORY_ITEM'`, each with a `buttonText` from two new
`common` i18n keys (`newModifierGroupButton`, `newInventoryItemButton`) — `TopNav` uses the
`common` namespace, not `admin`, so the page-local button's old `admin.newModifierGroupDialogTitle`/
`admin.newInventoryItemButton` labels couldn't be reused directly for this. `ModifierGroups.tsx`
and `Inventory.tsx` each lost their local `<Button onClick={() => openModal(...)}>` — the header now
only renders the `<h1>` title. Removed `admin.newInventoryItemButton` (ES/EN) since it became
unused dead code once the local button was deleted (its modal already has its own separate
`newInventoryItemDialogTitle` for the dialog's own header, untouched).

## Why It Changed?
Every other admin CRUD page already funnels "create" through `TopNav`'s single button so there's
exactly one entry point per page and consistent placement/styling — `ModifierGroups`/`Inventory`
had drifted from that convention when they were first built (reports 185/191), each adding its own
duplicate button instead of extending `TopNav`'s route switch. This aligns them with
`StaffHeader.tsx`'s pattern (title-only header, no local action button).

## Verification
`cd frontend && pnpm run test:run` — 25/25 PASS.
`cd frontend && pnpm run build` — PASS (confirms the `admin` `satisfies typeof esAdmin` check still
holds after removing the dead key on both sides).
