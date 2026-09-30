# Report 650 — ADMIN-MENU-ITEMS-CARD-POLISH

## 1. Identification
- Report: 650
- Task ID: ADMIN-MENU-ITEMS-CARD-POLISH
- Predecessor: 649 (ADMIN-MENU-ITEMS-GRID-LAYOUT)

## 2. Objective
Polish the dish cards: circular backgrounds on edit/delete buttons, bigger price and toggle, both inside a CardFooter.

## 3. Modified Files
- `frontend/src/pages/admin/ListMenuItem.tsx`

## 4. What Changed?
Edit/delete buttons got `rounded-full` backgrounds (grey for edit, light red tint for delete). Footer `div` replaced by `CardFooter` (`bg-transparent` to override its muted default). Price `text-2xl` → `text-4xl`. Switch scaled to `h-8 w-14` for this instance only, overriding the thumb via `[&>span]` selectors (thumb `h-7 w-7`, checked translate `x-6`).

## 5. Why It Changed?
Visual hierarchy: actions read as buttons, price and availability toggle are prominent, and the footer uses the shared card primitive. The shared `Switch` component was left untouched.

Verification: `pnpm run build` clean, lint 0 errors, `ListMenuItem.test.tsx` 4/4.
