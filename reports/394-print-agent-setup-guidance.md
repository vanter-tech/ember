# Report 394 — in-app print-agent setup guidance

## 1. Identification
- **Report:** 394
- **Task ID:** Q5 — print-agent has no in-app setup guidance
- **Predecessor:** report 393 — feat(customer): in-app camera QR scanner (Q4, PR #93)

## 2. Objective
Tell the admin what to do with the print-agent API key and how to fill in a printer, right in
the UI, plus a standalone setup guide for the agent process.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/printing/CreateAgentModal.tsx`
- `frontend/src/pages/admin/components/settings/printing/AddPrinterModal.tsx`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`
- `printing-agent/README.md` (new)

## 4. What Changed?
- **`CreateAgentModal`** — on the "key shown once" screen, an amber hint under the key:
  *"Pégala en el archivo agent.properties (campo agent.api-key), junto a backend.base-url, en la
  PC conectada a la impresora, y ejecuta el agente. Instrucciones completas:
  printing-agent/README.md."* (`printingApiKeySetupHint`).
- **`AddPrinterModal`** — under the Windows-queue name field, a hint:
  *"Debe coincidir EXACTO con el nombre que muestra Get-Printer en esa PC. Las impresoras de
  inyección de tinta (EcoTank) necesitan el modo 'Por driver de Windows'."* (`printingQueueNameHint`).
- **`printing-agent/README.md`** — Java 17 prereq, create-agent steps, the two-file layout
  (`agent.properties` with `backend.base-url=https://api.ember.vanter.net/v1` + `agent.api-key`;
  Hub uses `http://<hub-ip>:8080/v1`), the connection-type table (NETWORK / USB / WINDOWS_QUEUE
  with `Get-Printer` and the RAW-vs-DRIVER rule for inkjets), `java -jar` run + the
  `[print-agent] conectado` success line, Task Scheduler for auto-start, and a troubleshooting
  list keyed off the agent's actual log messages.
- No behaviour change. `pnpm run build` clean, `lint` 0 errors, `test:run` **91/91**.

## 5. Why It Changed?
The key is displayed once with only "copy this now" and no next step, and every real customer
setup so far has needed someone to explain out-of-band where `agent.api-key` goes, that the
Windows queue name has to match `Get-Printer` exactly, and that EcoTank inkjets only print in
DRIVER mode (this exact confusion recurred during the L3210 bring-up — see report 254). The
hints and README put that knowledge where the admin and the on-site tech will actually see it.
