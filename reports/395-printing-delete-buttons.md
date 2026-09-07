# Report 395 — delete-agent / remove-printer buttons in the printing settings

## 1. Identification
- **Report:** 395
- **Task ID:** Q6 — printing UI missing deletes
- **Predecessor:** report 394 — docs(printing): in-app print-agent setup guidance (Q5, PR #94)

## 2. Objective
Give the admin a way to remove a print agent and to remove a printer from an agent, from the
Settings → Impresión screen.

## 3. Modified Files
- `frontend/src/store/uiStore.ts` — `DELETE_PRINT_AGENT`, `DELETE_PRINTER` modal types
- `frontend/src/components/GlobalDeleteModal.tsx` — handle the two new modal types
- `frontend/src/pages/admin/components/settings/PrintingSettings.tsx`
- `frontend/src/pages/admin/components/settings/PrintingSettings.test.tsx` (new)
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`

## 4. What Changed?
- **`GlobalDeleteModal`** now also handles:
  - `DELETE_PRINT_AGENT` → `printingService.revokeAgent(id)` (`DELETE /printing/admin/agents/{id}`,
    which sets the agent `REVOKED`), invalidates `['printAgents']`.
  - `DELETE_PRINTER` → `printingService.updatePrinter(printerId, { active: false })`
    (`PATCH /printing/admin/agents/printers/{printerId}`), invalidates `['printerConfigs']`.
  Both use the modal's existing confirm UI and the generic delete copy.
- **`PrintingSettings`** — a trash button per agent (`DELETE_PRINT_AGENT`) next to "Agregar
  impresora"; `AgentPrinterList` gets a trash button per printer (`DELETE_PRINTER`). Renders
  `<GlobalDeleteModal />` (it wasn't mounted on this screen before). The agent list now filters
  out `status === 'REVOKED'` and the printer list filters out `!active`, so a removed row
  disappears.
- New admin i18n keys: `printingDeleteAgentAria`, `printingRemovePrinterAria`,
  `printingAgentDeletedToast`, `printingPrinterRemovedToast`.
- Verified: `pnpm run build` clean, `lint` 0 errors, `test:run` **92/92**
  (`PrintingSettings.test.tsx`: revoked agents hidden + a delete button rendered).

## 5. Why It Changed?
The backend already had everything: `DELETE /printing/admin/agents/{id}` (`revoke`, a soft status
change) and `PATCH .../printers/{printerId}` with an `active` field. `printingService.revokeAgent`
and `printingService.updatePrinter` were already in the frontend API layer. Only the UI wiring
was missing — no button, and `GlobalDeleteModal` wasn't rendered on the printing screen — so an
admin could add agents and printers but never remove them.

Both removals are **soft** under the hood (agent → `REVOKED`, printer → `active = false`). The
list filters make them read as deletes; a revoked agent's key is dead and the print agent skips
inactive printers, so nothing keeps working after a "delete". A future "ver archivadas" toggle
could surface them for reactivation — not built, nobody is blocked on it. No backend change, no
migration.
