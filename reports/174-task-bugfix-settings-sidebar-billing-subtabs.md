# Report 174

## Identification
- **Report #:** 174
- **Task:** bugfix-settings-sidebar-billing-subtabs
- **Predecessor Task:** bugfix-cash-register-sidebar-nav-icon (report 173)

## Objective
Give `/admin` Settings ("Configuración") the same collapsible sidebar pattern already used on `/admin/cash-register`, and split the combined Billing view into two proper subtabs: "Facturación" and "Pasarela de pago".

## Modified Files
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/Settings.tsx`

## What Changed?
- `SettingsType` gained a new `'PAYMENT_GATEWAY'` value alongside the existing `'BILLING'`.
- `SettingsBar` now accepts `collapsed`/`onToggleCollapsed` props (mirroring `CashRegisterBar`): all top-level items collapse to icon-only buttons, and a fixed bottom-left toggle button (`PanelLeftClose`/`PanelLeftOpen`) switches the state. The "Facturación" entry became an expandable group header with two indented subtab buttons — "Facturación" (`BILLING`) and "Pasarela de pago" (`PAYMENT_GATEWAY`, reusing the existing `paymentGatewayCardTitle` i18n key) — shown only when the sidebar is expanded; when collapsed, clicking the group icon jumps to `BILLING`.
- `Settings.tsx` now holds local `sidebarCollapsed` state, passes it down to `SettingsBar`, and widens/narrows the sidebar column accordingly. The `BILLING` switch case no longer stacks `<BillingSettings/>` + `<PaymentGatewaySettings/>` together; each now renders alone under its own case (`BILLING` / `PAYMENT_GATEWAY`).

## Why It Changed?
User request: implement the same collapsible-sidebar UX already shipped for Cash Register in the Settings section, and treat each settings card as its own subtab rather than stacking Billing + Payment Gateway on one screen.

## Verification
`cd frontend && pnpm run build` → PASS (`tsc -b && vite build`, no errors; pre-existing >500kB chunk-size warning unrelated).
