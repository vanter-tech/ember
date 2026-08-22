# Report 176 — feature-ticket-settings-live-preview

## Identification
- **Report number:** 176
- **Task ID:** feature-ticket-settings-live-preview
- **Predecessor Task:** bugfix-settings-sidebar-accordion-loyalty-subtabs (report 175)

## Objective
Add a "Ticket" subtab under Settings → Facturación where the admin configures the printed ticket's content (header/footer text, paper width, tax/tip visibility) and sees a live visual preview of both the customer receipt and the kitchen ticket (comanda).

## Modified Files
- `backend/src/main/java/com/vanter/ember/settings/model/SettingsPayload.java`
- `frontend/src/store/uiStore.ts`
- `frontend/src/components/SettingsBar.tsx`
- `frontend/src/pages/admin/Settings.tsx`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx` (new)
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`
- `frontend/src/lib/backend-types.ts` (regenerated)

## What Changed?
- `SettingsPayload` gained a `TicketSettings` nested class (`headerMessage`, `footerMessage`, `paperWidth: PaperWidth` enum `MM_58`/`MM_80` default `MM_80`, `showTaxBreakdown` default `true`, `showTip` default `true`) and a `ticket` field, embedded in the existing `restaurant_settings.payload` JSON column — no Flyway migration needed.
- `SettingsType` gained `'TICKET'`; `SettingsBar.tsx`'s `GROUP_MEMBERS.BILLING` accordion now has 3 members (`BILLING`, `PAYMENT_GATEWAY`, `TICKET`), same collapsible pattern as the existing Facturación/Pasarela split.
- New `TicketSettings.tsx` follows the draft/save/undo mutation pattern shared by every other settings tab (`BillingSettings.tsx`/`HardwareSettings.tsx`): a single form Card (header/footer text inputs, paper-width `Select`, two `Switch`es). A "Vista previa" row holds 2 outline buttons ("Recibo del cliente" / "Comanda de cocina", `Receipt`/`ChefHat` icons) that open a shared `Dialog` (previously unused in this codebase) showing the corresponding preview — no permanent second card on the page.
- The preview renders a monospace, thermal-receipt-styled block whose max-width reacts to the selected paper width. The customer-receipt view reads real `BrandingSettings` (business name/RUC/address/phone) and `BillingSettings` (currency symbol, `taxRules`, first `suggestedTipPercentage`) combined with 3 static sample line items to compute subtotal/tax/total/tip; the kitchen-ticket view shows the same sample items with quantities and one sample note, no prices. Both honor the draft header/footer/paper-width/toggle values as they're typed, before saving. (First pass used an always-visible second card with tabs; changed to the button+modal pattern per user follow-up feedback in the same session, before the branch was pushed.)
- Added ~29 new i18n keys per locale (`ticketLabel`, form labels/placeholders, preview labels, and the 3 sample item names) following the existing ES-source/EN-`satisfies` convention.
- `backend-types.ts` regenerated via `pnpm run openapi` against a **clean** backend build. The first regen attempt (against a `target/classes` that hadn't been `mvn clean`ed since the EMB-ACC accounting-module revert) leaked stale accounting-module endpoints into the generated file; caught before committing, fixed by `./mvnw clean` + rebuild + re-regenerating. Final diff on `backend-types.ts` is only the new `TicketSettings` schema, the `ticket` field on `SettingsPayload`, and one incidental reordering of 2 pre-existing fields on an unrelated `Pageable` schema (harmless, generator nondeterminism).

## Why It Changed?
User request: add a ticket-preview subtab under Facturación so admins can see how their printers' output will look before printing, without introducing any real printer/ESC-POS integration (none exists in this codebase — `HardwareSettings.tsx`'s `autoPrintTickets`/`printCustomerReceipt` are the only pre-existing print-related settings, both plain booleans). Scope was explicitly narrowed via brainstorming to preview + content settings only, both ticket types (customer receipt and kitchen comanda) sharing one subtab with a preview switcher, no `window.print()`/ESC-POS hook.

## Verification
- `cd frontend && pnpm run build` → `tsc -b && vite build` clean, no TS errors.
- `cd backend && ./mvnw test` → 722/722 green.
- No browser click-through this session (no `claude-in-chrome` tool available) — disclosed gap, consistent with prior recent sessions' notes in `PROGRESS.md`.
