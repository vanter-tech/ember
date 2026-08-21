# Report 170 — EMB-i18N-05: Kitchen views

**Task ID:** EMB-i18N-05
**Predecessor Task:** EMB-i18N-04 (report 169)

## Objective

Extract the remaining hardcoded Spanish/mixed-language UI strings in the Kitchen Display System (`pages/kitchen/**`) into a new `kitchen` i18n namespace, so the existing `LanguageSwitcher` (inserted in `OrdersDisplay.tsx` during EMB-i18N-01) actually translates the screen's content, per `docs/superpowers/plans/2026-08-18-emb-i18n.md` Task 5.

## Modified Files

- `frontend/src/locales/es/kitchen.ts` (new)
- `frontend/src/locales/en/kitchen.ts` (new)
- `frontend/src/locales/index.ts`
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`

## What Changed?

- New `kitchen` namespace (12 keys, ES source / EN `satisfies`): 7 seeded by the plan doc (`loadingOrders`, `loadingOrdersError`, `connected`, `disconnected`, `kdsSubtitle`, `ticketLabel`, `viewDetails`) plus 5 discovered while reading `FocusedCard.tsx` — a file the plan only said to "read and apply the same procedure" for, without enumerating its literals: `orderDetailsHeading` ("Detalles de Orden - M{{tableNumber}}"), `clientPlaceholder` ("Cliente: #-Por-iterar"), `entryTimeLabel` ("Ingreso: {{time}}"), `printButton` ("Imprimir"), `voidButton` ("Anular").
- `ticketLabel` and `viewDetails` are shared/reused between `QueueCard.tsx` and `FocusedCard.tsx` (`ticketLabel` in both; `viewDetails` only appears in `QueueCard.tsx`) rather than duplicated, same dedup convention as EMB-i18N-02..04.
- Registered `kitchen` in `locales/index.ts`'s `dictionaries` map, following the exact `esX`/`enX` import + object-extension pattern used for `auth`/`customer`/`waiter`.
- `OrdersDisplay.tsx`: added `useTranslation('kitchen')`, replaced its 4 remaining literals (loading, error, connected/disconnected badge text, KDS subtitle) — the wordmark + `LanguageSwitcher` insertion itself was already done in EMB-i18N-01.
- `QueueCard.tsx`: added `useTranslation('kitchen')`, replaced the ticket-number label and "Ver detalles" button text.
- `FocusedCard.tsx`: added `useTranslation('kitchen')`, replaced the order-details heading, ticket label, client placeholder, entry-time label, and Imprimir/Anular buttons.

Left untouched (per plan, disclosed gaps):
- `NEXT_ACTION_LABEL`/`NEXT_STATUS`/`STATUS_LABEL` (`pages/kitchen/lib/itemStatus.ts`) — shared status-label lookup tables, not page-local strings; folding them into the `kitchen` namespace is explicitly deferred to a future task.
- Both files' `toast.error('No se pudo actualizar el estado del plato')` calls — deferred to EMB-i18N-08 (validation/toast copy pass, all roles).
- `QueueCard.tsx`'s `order.tableNumber || "?"` fallback — a symbol, not translatable text.

## Why It Changed?

Continues the EMB-i18N backlog (spec `docs/superpowers/specs/2026-08-18-emb-i18n-design.md`, plan `docs/superpowers/plans/2026-08-18-emb-i18n.md`) task-by-task, in plan order, after EMB-i18N-01..04 shipped the infrastructure, auth/nav, customer, and waiter namespaces. `pages/kitchen/**` was next in the queue (`docs/superpowers/plans/2026-08-18-emb-i18n.md` Task 5), and its 3 files were the only remaining tenant-frontend screens still fully hardcoded on the KDS role.

## Verification

- `cd frontend && pnpm run build` — `tsc -b` + `vite build` succeeded, no errors (the `satisfies typeof esKitchen` check in `en/kitchen.ts` confirms key parity).
- `cd frontend && pnpm test:run` — 7/7 passed (3 files).
- No `claude-in-chrome` tool available this session — manual click-through toggling the switcher on a live KDS screen was not performed. Disclosed gap, same as EMB-i18N-02..04 (reports 167–169); still owed alongside the other pending manual-verification items already tracked in `PROGRESS.md`.
