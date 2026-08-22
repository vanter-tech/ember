# Report 173

## Identification
- **Report Number:** 173
- **Task ID:** bugfix-cash-register-sidebar-nav-icon
- **Predecessor Task:** EMB-i18N-07 (report 172) — followed by a git revert (feature/kitchen-view hard-reset to `9a4b23a`) that removed the accounting engine work (reports 174-205 from the prior history), on explicit user request.

## Objective
After reverting the accounting-engine work, bring back a collapsible sidebar shell for the admin Cash Register page (previously a plain top-tab layout), containing only the Caja entry and its Historial de Turnos / Corte Diario sub-tabs — and update `FloatingNav`'s admin Cash Register icon to `BookOpen` (what the now-reverted "Contabilidad" nav entry used).

## Modified Files
- `frontend/src/pages/admin/cashRegister/components/CashRegisterBar.tsx` (new)
- `frontend/src/pages/admin/cashRegister/CashRegister.tsx`
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## What Changed?
- Added `CashRegisterBar.tsx`: a collapsible sidebar (`collapsed` state, `w-64`/`w-fit`) with a single "Caja" entry (`Banknote` icon) and its two sub-tabs (Historial de Turnos / Corte Diario, `destructive` variant when active) always shown beneath it, plus a floating collapse toggle button (bottom-left, icon-only collapsed / icon+text expanded) — same pattern as the reverted `AccountingBar.tsx`, reduced to Caja-only since there are no other sections anymore.
- `CashRegister.tsx`: replaced the `Tabs`/`TabsList` top-tab layout with the same `flex md:flex-row` sidebar shell the old `Accounting.tsx` used, rendering `ShiftHistoryTable`/`DailyZReportPanel` based on the sidebar's selected section.
- `FloatingNav.tsx`: admin Cash Register `Link`'s icon changed `Banknote`→`BookOpen` (route `/admin/cash-register` and label `navCash` unchanged; `Banknote` import kept, still used by the waiter Cash Register link).
- Re-added `collapseSidebarLabel`/`expandSidebarLabel`/`cashRegisterTab` keys to `locales/{es,en}/admin.ts` (lost when the accounting-engine commits were reverted).

## Why It Changed?
User request, after the accounting-engine revert: wanted the sidebar navigation UX back (instead of the plain top tabs Cash Register had before accounting existed), scoped to just Caja, with the nav icon carried over from the old "Contabilidad" entry rather than reintroducing the whole accounting module.
