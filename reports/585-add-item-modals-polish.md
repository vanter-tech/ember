# Report 585 — ADD-ITEM-MODALS-POLISH

## 1. Identification
- **Report number:** 585
- **Task ID:** ADD-ITEM-MODALS-POLISH
- **Predecessor:** report 584 (WAITER-MODIFIERS-SIDE-PANEL)

## 2. Objective
Polish the waiter add-dish modal: hide the modifier panel scrollbar, "Aceptar" instead of "Agregar", colored option badges, larger modal and cart panel.

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx` (+ `AddItemModal.test.tsx`)
- `frontend/src/components/ModifierOptionBadges.tsx`
- `frontend/src/lib/modifierGroupColors.ts`
- `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
Modifier panel uses `no-scrollbar`; confirm label is "Aceptar"/"Accept" (avoids implying the modifiers are mandatory). `GROUP_COLORS` gained `badge`/`badgeOn` tint classes; option badges are tinted with their group color (stronger when selected). Dialog widened to `min(95vw, 88rem)`, taller lists (78vh/68vh), taller product images (h-52), cart panel 26rem with `PANEL_SHIFT` 216, modifier panel w-96.

## 5. Why It Changed?
User feedback after trying the side panel. Build clean, lint 0 errors, waiter+admin tests 86/86. Not visually verified.
