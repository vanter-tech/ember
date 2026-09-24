# Report 558 — QUICK-START-ADMIN-BORDER

## 1. Identification
- **Report number:** 558
- **Task ID:** QUICK-START-ADMIN-BORDER
- **Predecessor:** report 557 (AUTH-CARD-PADDING)

## 2. Objective
Highlight the admin's tile in the login quick-start with a red `#8c1717` border.

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?
- The tile gets `border-2 border-[#8c1717]` when `p.role === 'ADMIN'` (via `cn`); other tiles keep the default border. Tile size is fixed (`h-44 w-44`), so the thicker border doesn't shift the layout.

## 5. Why It Changed?
Requested visual distinction for the admin profile.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually; relies on the stored profile role being the literal `ADMIN` (as saved from the login response).
