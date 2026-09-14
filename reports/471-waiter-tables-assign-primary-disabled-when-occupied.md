# Report 471

## 1. Identification
- **Report Number:** 471
- **Task ID:** `/waiter/tables`: "Asignar mesa" as primary (red) + disabled when occupied (ad-hoc)
- **Predecessor Task:** report 470 (removed dead charge/print buttons)

## 2. Objective
User asked to make "Asignar mesa" the primary (red) button and disable it when the table is already occupied, for both the waiter and admin views.

## 3. Modified Files
- Modify: `frontend/src/pages/waiter/Tables.tsx`

## 4. What Changed?
Dropped `variant={'outline'}` from the "Asignar mesa" `Button` — the default variant is `bg-primary` (the app's red), so this alone makes it primary/red now that it's the only action button left in that state. `disabled={!isCajaOpen}` became `disabled={!isCajaOpen || tableDetails.isOccupied}`. No admin-specific file exists or needed changes: `App.tsx` routes `/waiter/tables` through `ProtectedRoute allowedRoles={['WAITER', 'ADMIN']}` to this exact same `Tables.tsx` component, so the fix already covers both roles.

## 5. Why It Changed?
Direct user request.

## 6. Verification
- `pnpm run test:run` (full suite) — **135/135**, unchanged.
- `pnpm run build` / `pnpm run build:hub` — both clean.
