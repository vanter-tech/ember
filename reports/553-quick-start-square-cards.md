# Report 553 — QUICK-START-SQUARE-CARDS

## 1. Identification
- **Report number:** 553
- **Task ID:** QUICK-START-SQUARE-CARDS
- **Predecessor:** report 552 (COLORED-AVATAR-INITIALS)

## 2. Objective
Redesign the login "Inicio rápido": square rounded cards (avatar in the middle, name and role below) instead of rectangular rows, on the landing's light background (dot grid + blurred red glows).

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?
- Page background: `bg-slate-50` → white base + masked dot grid + two blurred `#920703` blobs (12 % top-right, 9 % bottom-left, `blur(90px)`), mirroring `landing/src/components/Hero.astro`. Card raised with `z-10`.
- Quick-start profiles: 2/3-column grid of `aspect-square rounded-3xl` tiles, 64px colored initials circle centered, name + role stacked below; the edit "×" moved inside the tile's top-right corner.
- No logic, store or modal changes.

## 5. Why It Changed?
Row chips read as a list; square tiles match the requested visual identity and the landing's look. The "×" was moved inside because outside-corner placement would be clipped by the grid gap.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` tests 16/16. Not checked visually in a browser.
