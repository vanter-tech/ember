# Report 570 — MODIFIER-COLORS-BRAND-ACCENT

## 1. Identification
- **Report number:** 570
- **Task ID:** MODIFIER-COLORS-BRAND-ACCENT
- **Predecessor:** report 569 (MODIFIER-GROUPS-TAB-COLORS)

## 2. Objective
Make the modifier-group colors match the app's design (white/zinc surfaces + brand red) instead of bright Tailwind pastel fills.

## 3. Modified Files
- `frontend/src/lib/modifierGroupColors.ts`
- `frontend/src/pages/admin/components/ModifierGroupAssignmentField.tsx` (+ its test)
- `frontend/src/pages/admin/ModifierGroups.tsx`
- `frontend/src/pages/admin/ListMenuItem.tsx`

## 4. What Changed?
- Palette replaced by 8 muted warm accents (brick, terracotta, ochre, olive, sage, slate, plum, stone) exposing only `dot` and `accent` (left-border) classes; the color identifies a group but never fills a surface.
- Picker badges: neutral white/zinc; selected state uses the brand red `#8c1717` (border + light tint + check); a colored dot precedes the name; the numbered order pills are neutral with the dot.
- Sub-tab cards: neutral card with a 4px colored left edge; the type badge is neutral; option chips are neutral with the group's dot.
- Dish-card badges: neutral chips with the group's dot.
- Test "different colors" now compares the dot class.

## 5. Why It Changed?
The saturated per-group fills clashed with the rest of the SaaS UI; color-as-accent keeps group identity while staying on-brand.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 224/225; the 1 failure is the pre-existing `MenuJoin.test.tsx` (see r550). Not checked visually in a browser.
