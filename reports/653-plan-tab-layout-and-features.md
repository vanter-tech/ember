# Report 653 — PLAN-TAB-LAYOUT-AND-FEATURES

## 1. Identification
- Report: 653
- Task ID: PLAN-TAB-LAYOUT-AND-FEATURES
- Predecessor: 652 (ADMIN-MENU-ITEM-PRICE-BOLD); builds on uncommitted r647 (SETTINGS-PLAN-TAB-SUBSCRIPTION)

## 2. Objective
Settings > Plan: move the contact button and its note to the right, make the "Activo" badge #8c1717 and larger, and list what each plan truly includes.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## 4. What Changed?
- Footer is a right-aligned row (`sm:flex-row justify-end`): note left of the button; stacked on mobile.
- Active badge: `bg-[#8c1717] text-white px-4 py-1.5 text-base font-semibold`; inactive badge gets the same size, outlined.
- `PLAN_FEATURES` rewritten as cumulative lists: FREE (1 table, collaborative cart, KDS, daily analytics, community support); STARTER (everything in Free, 10 tables, analytics by period, cash close, roles, receipt branding, email support); PRO (everything in Starter, unlimited tables, Excel export, priority support); ENTERPRISE (everything in Pro, 24/7 support, SLA, custom integrations, account manager). 12 new i18n keys (es/en).

## 5. Why It Changed?
The old list only reflected what `PlanGateService` gates, so FREE and ENTERPRISE looked nearly empty. Gated items match the backend (tables, periodfilters, cashclose, roles, branding, export). Support/SLA/integrations/account-manager are commercial promises from the landing plan cards, not enforced in code.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 5/5. NOT committed: depends on uncommitted r647 files.
