# Report 491

## 1. Identification
- **Report number:** 491
- **Task ID:** LIVE-BUG-BATCH 4/7 — Settings tab cards should use a grid layout
- **Predecessor task:** report 490 (bug 3 — misleading "paid and closed" banner + leave warning)

## 2. Objective
Live user bug report: most Settings tabs render their fields in a single narrow (`max-w-md`)
column, wasting the right half of the card on wide screens, with the footer's `border-t` looking
detached from content that doesn't fill the card. User-confirmed reference: `BrandingSettings.tsx`
already uses the correct pattern (`grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6`, no `max-w-*`
cap) — every other tab should match it.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/MenuSettings.tsx`
- `frontend/src/pages/admin/components/settings/PaymentGatewaySettings.tsx`
- `frontend/src/pages/admin/components/settings/HardwareSettings.tsx`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx`
- `frontend/src/pages/admin/components/settings/LoyaltySettings.tsx`
- `frontend/src/pages/admin/components/settings/BillingSettings.tsx`
- `frontend/src/pages/admin/components/settings/BusinessHoursSettings.tsx`

## 4. What Changed?
Converted each `CardContent`'s field layout from a `max-w-md space-y-*` (or `max-w-2xl space-y-4`
for Business Hours) single column to `grid grid-cols-1 md:grid-cols-2 gap-x-12 gap-y-6 p-6`, split
into two `flex flex-col space-y-6` column wrappers, exactly mirroring `BrandingSettings.tsx`'s
existing structure — for the 6 tabs whose content is genuinely a form (fields/toggles/selects):
MenuSettings (2 toggles → 1 per column), PaymentGatewaySettings (4 fields → 2/2), HardwareSettings
(2 toggles → 1 per column), TicketSettings (6 fields → 3/3), LoyaltySettings (4 field groups →
2/2), BusinessHoursSettings (7 day-rows now flow 2-per-row instead of one long list).

`BillingSettings.tsx` is mixed: currency/tax-rate/tax-included/suggested-tips are compact fields
(now 2-column), but "Reglas de impuestos" is a genuinely dynamic list of already-wide rows (name +
rate + toggle + remove button per row) — kept that section full-width below the grid rather than
forcing it into a half-width column, where its rows would wrap awkwardly.

**Deliberately left unchanged** (not form-shaped, forcing a grid would add visual noise without
reducing whitespace): `SpaceSettings.tsx` (single field — a 2nd empty column doesn't help),
`ExportSettings.tsx` (already compact, single small self-contained form), `InfoSettings.tsx`
(static informational content), `PrintingSettings.tsx` and `LoyaltyRewardsSettings.tsx` (agent/job
lists and a data table, both correctly full-width by design already).

## 5. Why It Changed?
Matches the exact reference pattern the user pointed at (Branding's already-correct layout) and
addresses both symptoms described: wasted right-side space on a wide card, and a footer divider
that looked disconnected from a narrow content column above it.

## Verification
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **148/148** (no test coverage existed for any of these 7
  components before or after — pure layout change, nothing behavioral to assert).
- No backend changes.
