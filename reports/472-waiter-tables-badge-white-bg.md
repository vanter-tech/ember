# Report 472

## 1. Identification
- **Report Number:** 472
- **Task ID:** `/waiter/tables` grid: white background on the participant-count badge (ad-hoc)
- **Predecessor Task:** report 471 (Asignar mesa primary + disabled when occupied)

## 2. Objective
Two items from the user: (1) claimed the "Asignar mesa" disabled-when-occupied fix (report 471) was missing for the admin view, and (2) asked for a white background on the participant-count badge shown in each table card's top-right corner.

## 3. Investigation (item 1 — no code change)
Verified in code that no separate admin view exists: `App.tsx` routes `/waiter/tables` through `ProtectedRoute allowedRoles={['WAITER', 'ADMIN']}` to the exact same `Tables.tsx`, and the backend's `GET /cash-shifts/current` (which `isCajaOpen` reads) is `hasAnyRole('WAITER','ADMIN')` — tenant-scoped, not per-role. There is no code path where `disabled={!isCajaOpen || tableDetails.isOccupied}` behaves differently for ADMIN vs WAITER; grepped the whole `pages/admin/` tree for `assignTableLabel`/`isOccupied`/`tableNumber` and found nothing duplicating this button. Flagged this back to the user (stale build/deploy, or a different reproduction than assumed) rather than guessing at a second fix; no change made for this item.

## 4. Modified Files
- Modify: `frontend/src/pages/waiter/Tables.tsx`

## 5. What Changed?
The participant-count badge (`<Users/>` icon + count, top-right of each table `Card`) now always has a white background and black text/icon (`bg-white text-black`), keeping only a red border ring (`border-2 border-[#8b0000]`) when the table is occupied instead of the previous solid dark-red fill with white text. Also dropped a stray extra `}` left over in the old template literal's class string (harmless — an unused, dangling `}` token — but cleaned up while already touching this line).

## 6. Why It Changed?
Direct user request for item 2. Item 1 has no code change — see Investigation above.

## 7. Verification
- `pnpm run test:run` (full suite) — **135/135**, unchanged.
- `pnpm run build` / `pnpm run build:hub` — both clean.
