# Report 165

## 1. Identification
- **Report Number:** 165
- **Task ID:** bugfix-customer-floating-islands-mobile-overlap
- **Predecessor Task:** bugfix-customer-home-mobile-responsive (report 164)

## 2. Objective
Fix `ParticipantsPopUp`/`ItemsFloatingIsland` colliding with the always-present `FloatingNav` at narrow (`sm`–`md`) viewport widths on the customer `Menu` page.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
The two `fixed`-positioned wrapper `div`s (lines 192/195) hosting `ParticipantsPopUp` and `ItemsFloatingIsland` had a hardcoded `bottom-10` offset. Changed to `bottom-24 md:bottom-10`, so the raised offset only applies between the `sm` and `md` breakpoints; at `md` and above the original `bottom-10` is restored (desktop layout unchanged).

## 5. Why It Changed?
`FloatingNav.tsx` sits at `sm:bottom-8`, only 8px below the islands' original `bottom-10`. Both elements are simultaneously visible from the `sm` breakpoint up (`hidden sm:block`), and `FloatingNav`'s own rendered height at `sm` widths made its top edge reach into the islands, causing a visual overlap on narrow screens. Raising the offset (matching `MobileActionsIsland`'s `bottom-24` pattern already used for the `sm:hidden` mobile case) gives clearance in the same range without affecting wider layouts where the two never touch.

## Verification
`cd frontend && pnpm run build` — green (`tsc -b && vite build`, pre-existing chunk-size warning only, unrelated).
