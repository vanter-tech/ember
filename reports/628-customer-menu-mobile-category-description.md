# Report 628 — CUSTOMER-MENU-MOBILE-CATEGORY-DESCRIPTION

## 1. Identification
- Report: 628
- Task ID: CUSTOMER-MENU-MOBILE-CATEGORY-DESCRIPTION
- Predecessor: CUSTOMER-MENU-DESKTOP-CATEGORY-HERO (report 627)

## 2. Objective
Mobile category rows squeezed name + description into one cramped horizontal slot alongside the thumbnail/badge/chevron, truncating both to a single line. Requested fix: move the description into its own full-width `#8c1717` block below the row.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
The mobile category `button` is now `flex-col` instead of `flex items-center`: the top row keeps thumbnail + name (still `truncate`) + item-count badge + chevron, and the description (when present) moved into its own full-width block below that row, styled `rounded-xl bg-[#8c1717]` with white `text-xs line-clamp-2` text — reusing the same red-tag visual language already used for modifier options elsewhere in the app (`ComandaView`/item modifiers), instead of truncating to one gray line.

## 5. Why It Changed?
Direct visual feedback: both name and description were getting cut off on mobile; the suggested fix (a colored card for the description) fit an existing pattern in the codebase rather than inventing a new one.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`** at ~390px width (a fresh tab was needed — `resize_window` was flaky again on the first tab, consistent with earlier reports this session): every category row now shows its full name and description in the red pill below, no truncation visible with the current dev data.
