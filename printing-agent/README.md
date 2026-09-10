# Ember print agent

A small Windows app that runs on the PC physically connected to your kitchen / receipt
printer. It connects out to the Ember backend over WebSocket, receives print jobs for the
printers registered to it, and sends them to the printer.

You need one agent per PC that has printers attached. Each agent pairs once, with a short
code, and remembers its credential (encrypted with Windows DPAPI) across reboots and updates.

## 1. Install

1. In the Ember admin: **Configuración → Impresión → Descargar Ember Agent** — downloads
   `EmberAgentSetup-x.y.z.exe`.
2. Run it on the printer's PC and accept the UAC prompt. **No Java needed** — the installer
   bundles its own runtime.
3. Ember Agent starts automatically and also opens on every log-on (a shortcut in Startup,
   launched minimized to the system tray).

## 2. Pair the agent

1. In the admin, under **Configuración → Impresión**, create an agent (e.g. `Caja 1`) — the
   post-create screen shows a **pairing code** (valid ~15 minutes).
2. Open Ember Agent (tray icon → *Abrir*), paste the code, **Emparejar**.
3. The dashboard goes **Sin emparejar → Conectando… → Conectado**. The credential is written
   to `%ProgramData%\EmberAgent\credential.bin` and reused forever — you never pair again
   unless that folder is wiped or the PC changes.

If you already have a raw API key instead of a code, use *"Tengo una API key"* in the pair
dialog.

## 3. Register the printer (in the Ember admin)

Under the agent, **Agregar impresora**:

| Field | Value |
| --- | --- |
| Rol | `Cocina` or `Recibo` |
| Tipo de conexión | how the printer is reached — see below |
| Etiqueta | any name for your reference |

**Connection type:**

- **Red (IP)** — a network/Ethernet printer. Enter its IP and port (usually `9100`).
- **USB (puerto serial)** — a printer exposed as a COM port. Enter the port (`COM3`).
- **Cola de impresión de Windows** — anything installed as a normal Windows printer. Once the
  agent is connected it reports its installed queues, so this field becomes a **dropdown** of
  real queue names. Pick one, then choose a **Modo de impresión**:
  - **Directo (ESC/POS)** — real thermal receipt printers.
  - **Por driver de Windows** — printers with **no** ESC/POS support, e.g. EPSON EcoTank
    inkjets (`EPSON L3210 Series`, `EPSON L1250 Series`, …). Inkjets **only** work in this
    mode; the admin pre-selects it when the agent flags a queue as an inkjet.

## 4. Day to day

Nothing to run by hand. Ember Agent lives in the tray, reconnects on its own, and prints
jobs as they arrive. The dashboard shows the connection state, the reported printers, a
**Imprimir página de prueba** button, and a recent-activity table.

## Advanced

- On-disk layout: `%ProgramData%\EmberAgent\` (`credential.bin`, `agent-state.json`, `logs\`)
  survives app updates; the uninstaller asks before deleting it.
- Non-Windows / manual fallback: place an `agent.properties`
  (`backend.base-url=…`, `agent.api-key=…`) next to the jar and run
  `java -jar printing-agent-<version>.jar`. DPAPI is Windows-only, so the key is stored in
  clear text with a warning on other platforms.
- Build from source: `pwsh printing-agent/build-installer.ps1` (needs JDK 17 + Inno Setup 6).
- **Bump `printing-agent/pom.xml` `<version>` for every release** — it names the
  installer (`EmberAgentSetup-<version>.exe`) and jpackage's `--app-version`. Two
  releases sharing a version overwrite each other in `gs://ember-downloads-prod`
  and block clean in-place updates.

## Troubleshooting

- **`Windows print queue not found: <name>`** — the queue name doesn't match `Get-Printer`
  output exactly. Re-pick it in **Agregar impresora**.
- **`This port appears to have been shutdown or disconnected`** — a USB/COM printer config
  points at a port that isn't there; deactivate or delete that printer.
- **Nothing prints, no error** — an inkjet configured as **Directo (ESC/POS)**. Switch it to
  **Por driver de Windows**.
- **Dashboard stuck on `Reintentando`** — the PC can't reach the backend; check the network
  and that the pairing code hadn't already expired when redeemed.
