# Report 421 — EMB-PRINT-AGENT T5 (frontend admin)

## 1. Identification
- **Report:** 421
- **Task ID:** EMB-PRINT-AGENT T5 (frontend admin — pairing code, printer `<select>`, `.exe` download link)
- **Predecessor:** report 420 — EMB-PRINT-AGENT T4 (agent — `AgentRunner` + Swing dashboard/tray)
- **Plan:** `docs/superpowers/plans/2026-09-08-print-agent-installer.md` Task 5

## 2. Objective
Expose the T1 pairing/discovery backend in the admin UI: create-agent flow leads with a short pairing code (API key demoted to an advanced `<details>`), per-agent "Nuevo código" action + Emparejado/Sin emparejar badge + installer `.exe` download link, and the Windows-queue field becomes a dropdown of the agent's discovered printers with inkjet auto-selecting `DRIVER` render mode.

## 3. Modified Files
- `frontend/src/lib/backend-types.ts`
- `frontend/src/lib/api.ts`
- `frontend/src/vite-env.d.ts`
- `frontend/src/pages/admin/components/settings/printing/CreateAgentModal.tsx`
- `frontend/src/pages/admin/components/settings/PrintingSettings.tsx`
- `frontend/src/pages/admin/components/settings/printing/AddPrinterModal.tsx`
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`
- `frontend/src/pages/admin/components/settings/PrintingSettings.test.tsx`

## 4. What Changed?
- **`backend-types.ts` (surgical, no full `openapi` regen):** added `DiscoveredPrinter` (`name`/`driverName`/`portName`/`inkjetGuess`) and `PairingCodeResponse` (`code`/`expiresAt`) schemas; `PrintAgentResponse` gained `paired?: boolean` and `discoveredPrinters?: DiscoveredPrinter[]`. Full regen was skipped deliberately — the live openapi diff is ~1230 lines of pre-existing drift unrelated to this task (PROGRESS.md T1 note).
- **`api.ts`:** `PairingCodeResponse` type export + `printingService.createPairingCode(id)` → `POST /printing/admin/agents/${id}/pairing-code`.
- **`vite-env.d.ts`:** declared optional `VITE_AGENT_DOWNLOAD_URL`.
- **`CreateAgentModal.tsx`:** `onSuccess` now also calls `createPairingCode(created.id)` (best-effort, swallows failure) and stores `pairCode`. The post-create screen leads with the pairing code (large, monospace, `tracking-widest`) + a Copy button + `printingPairCodeHint`; the raw API key moved into a `<details>` labelled `printingAdvancedInstallLabel`. Added a small `copy()` helper (clipboard + toast).
- **`PrintingSettings.tsx`:** new `NewPairingCodeButton` component (sibling of `RegenerateKeyButton`) — mutates `createPairingCode`, shows `code` + localized `expiresAt` in a `Dialog` with Copy. Card header now carries a `VITE_AGENT_DOWNLOAD_URL`-driven download anchor (default `https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe`). Per-agent status line appends an emerald/amber `Emparejado`/`Sin emparejar` span from `agent.paired`. `ADD_PRINTER` modal now opens with `{ agentId, discoveredPrinters }` instead of a bare id string.
- **`AddPrinterModal.tsx`:** reads `{ agentId, discoveredPrinters }` from `modalPayload`. When the agent reported ≥1 named queue, `windowsQueueName` renders as a shadcn `<Select>` of those names plus an `__other__` → `printingQueueOtherOption` item that reveals the free-text `Input`; with no discovered queues it stays a plain `Input` (unchanged behaviour). Picking a queue whose `inkjetGuess` is true calls `form.setValue('renderMode', 'DRIVER')`. `queueFreeText` state reset on close.
- **i18n:** 10 new keys added to both `es/admin.ts` and `en/admin.ts` (`printingPairCodeTitle`, `printingPairCodeHint`, `printingAdvancedInstallLabel`, `printingNewPairCodeButton`, `printingPairCodeExpiresLabel`, `printingPairedBadge`, `printingUnpairedBadge`, `printingDownloadAgentLink`, `printingQueueOtherOption`, `printingCopyButton`).
- **`PrintingSettings.test.tsx`:** mocks `createPairingCode`; +2 tests — badge (`Sin emparejar`/`Emparejado`) + download link present; "Nuevo código" click calls `createPairingCode('a-1')` and renders the returned code.

## 5. Why It Changed?
T1 shipped the code-based pairing handshake and printer-discovery sink but nothing surfaced them. Operators need to (a) hand an installer + a phone-readable code to whoever sets up the printer PC instead of pasting a 40-char key into `agent.properties`, (b) see at a glance whether an agent has completed pairing, and (c) pick the exact Windows queue name from what the agent actually enumerated rather than typing it and hoping it matches `Get-Printer`. Inkjet queues (EcoTank etc.) have no ESC/POS path, so pre-selecting `DRIVER` when the agent flags `inkjetGuess` removes a common misconfiguration.

## Verification
- `cd frontend && pnpm run build` → PASS (`tsc -b` clean, vite built in 3.88s).
- `cd frontend && pnpm run lint` → 0 errors, 16 warnings (all pre-existing).
- `cd frontend && pnpm run test:run` → **120/120** (was 118; +2 new `PrintingSettings` cases).
