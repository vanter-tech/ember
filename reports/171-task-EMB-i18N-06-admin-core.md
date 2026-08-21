# Report 171: EMB-i18N-06 — Admin core translation

## 1. Identification
- **Report number:** 171
- **Task ID:** EMB-i18N-06
- **Predecessor Task:** EMB-i18N-05 (report 170)

## 2. Objective
Extract hardcoded Spanish UI strings from the admin core screens (`pages/admin/**` excluding analytics/settings) plus the shared `GlobalDeleteModal`/`PaginationControls`/`SettingsBar` components into a new `admin` i18n namespace, so the existing `LanguageSwitcher` can toggle these screens between Spanish and English.

## 3. Modified Files
- `frontend/src/locales/es/admin.ts` (new, 98 keys)
- `frontend/src/locales/en/admin.ts` (new, `satisfies typeof esAdmin`)
- `frontend/src/locales/index.ts` (registered `admin` namespace)
- `frontend/src/pages/admin/Category.tsx`
- `frontend/src/pages/admin/ListMenuItem.tsx`
- `frontend/src/pages/admin/components/EditMenuModal.tsx`
- `frontend/src/pages/admin/components/NewCategoryModal.tsx`
- `frontend/src/pages/admin/components/NewMenuModal.tsx`
- `frontend/src/pages/admin/cashRegister/CashRegister.tsx`
- `frontend/src/pages/admin/cashRegister/components/DailyZReportPanel.tsx`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- `frontend/src/pages/admin/staff/Staff.tsx`
- `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx`
- `frontend/src/pages/admin/staff/components/EditStaffModal.tsx`
- `frontend/src/pages/admin/staff/components/StaffCard.tsx`
- `frontend/src/pages/admin/staff/components/StaffGrid.tsx`
- `frontend/src/pages/admin/staff/components/StaffHeader.tsx`
- `frontend/src/pages/admin/staff/components/StaffKpis.tsx`
- `frontend/src/components/GlobalDeleteModal.tsx`
- `frontend/src/components/PaginationControls.tsx`
- `frontend/src/components/SettingsBar.tsx`

## 4. What Changed?
Created the `admin` namespace (`locales/es/admin.ts` ES source, `locales/en/admin.ts` EN `satisfies`) and registered it in `locales/index.ts`, following the same pattern as `kitchen`/`waiter`/`customer`. All 19 target files now call `useTranslation('admin')` and render JSX text/`placeholder`/`title`/`aria-label` literals through `t('key')`, with `{{var}}` interpolation for `loadingCategories`/`productsCountLabel`/`refundedAmountSuffix`.

Identical repeated literals were deduped to a single key across files: `cancelButton` ("Cancelar", 6 files), `nameLabel`/`descriptionLabel`/`coverImageLabel` (3 menu/category modals), `savingButton`/`saveButton` vs. the visually-similar-but-textually-distinct `savingEllipsisLabel` ("Guardando" vs "Guardando..." — kept separate per the established no-copy-editing precedent), `activeStatus` ("Activo", reused across `Category.tsx`'s badge, `EditStaffModal.tsx`'s toggle label, and `StaffCard.tsx`'s status dot title), and `shiftLabel`/`contractTypeLabel`/`locationLabel` (shared between `EditStaffModal.tsx`'s form and `StaffCard.tsx`'s metadata chips). `EditMenuModal.tsx`'s pre-existing bug — its "available" toggle's `FormLabel` literally reads "Precio" instead of "Activar" — was preserved as-is (reused `priceLabel`), not fixed, per the plan's string-extraction-only constraint.

A type error surfaced during build: `Category.totalItems` is `number | undefined`, but the new `productsCountLabel` interpolation expects `string | number`; fixed with `Category.totalItems ?? 0`.

## 5. Why It Changed?
Continues the EMB-i18N backlog (plan `docs/superpowers/plans/2026-08-18-emb-i18n.md`, Task 6) toward full Spanish/English coverage of the tenant frontend. `admin/staff/types.ts`'s `ROLE_LABELS`/`STAFF_FILTERS`/`ROLE_BADGE_CLASSNAMES` lookup tables were deliberately left untouched — same precedent as `kitchen`'s `itemStatus.ts` and `customer`'s `TIER_LABELS` — so `StaffFilters.tsx` required no code change at all, since every string it renders comes from that table. All `toast.*`/`z.object(...)` calls in the touched files (`ListMenuItem.tsx`, `EditMenuModal.tsx`, `NewCategoryModal.tsx`, `NewMenuModal.tsx`, `CreateStaffModal.tsx`, `EditStaffModal.tsx`, `GlobalDeleteModal.tsx`) were left hardcoded, deferred to EMB-i18N-08 per the plan's global constraint. No backend changes; no `pages/console/**` files touched (`PaginationControls.tsx` is a shared `components/` file also used by 2 console pages, but only its own file needed editing — no console page was modified).

**Verification:** `pnpm run build` (tsc -b + vite build) — pass. `pnpm test:run` — 7/7 pass. No `claude-in-chrome` browser tool available this session, so no manual click-through was performed — disclosed gap, consistent with prior EMB-i18N task reports.
