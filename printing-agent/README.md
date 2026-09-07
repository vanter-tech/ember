# Ember print agent

A small Java process that runs on the PC physically connected to your kitchen / receipt
printer. It connects out to the Ember backend over WebSocket, receives print jobs for the
printers registered to it, and sends them to the printer.

You need one agent per PC that has printers attached. Each agent has its own API key.

## 1. Prerequisites

- **Java 17 (JRE or JDK).** Check with `java -version` — it must report `17.x`.
  Get it from <https://adoptium.net/temurin/releases/?version=17>.
- The printer installed and working in Windows (it prints a Windows test page).

## 2. Create the agent and get its key (in the Ember admin)

1. Admin → **Configuración → Impresión**.
2. **Generar agente**, give it a name (e.g. `Caja 1`).
3. Copy the **API key** — it is shown **once**.

## 3. Configure the agent

Put two files in the same folder on the printer's PC, e.g. `C:\ember-agent\`:

- `printing-agent-0.1.0-SNAPSHOT.jar` (the built agent — ask the Ember team for the current
  build, or run `mvn -DskipTests package` in this directory)
- `agent.properties`:

```properties
backend.base-url=https://api.ember.vanter.net/v1
agent.api-key=PASTE_THE_KEY_SHOWN_ONCE
```

For a self-hosted Ember Hub, set `backend.base-url` to `http://<hub-ip>:8080/v1` instead.

## 4. Register the printer (in the Ember admin)

Under the agent, **Agregar impresora**:

| Field | Value |
| --- | --- |
| Rol | `Cocina` or `Recibo` |
| Tipo de conexión | how the printer is reached — see below |
| Etiqueta | any name for your reference |

**Connection type:**

- **Red (IP)** — a network/Ethernet printer. Enter its IP and port (usually `9100`).
- **USB (puerto serial)** — a printer exposed as a COM port. Enter the port (`COM3`).
- **Cola de impresión de Windows** — anything installed as a normal Windows printer. Enter the
  **exact** queue name from PowerShell:

  ```powershell
  Get-Printer | Select-Object Name
  ```

  Copy it verbatim (spaces and capitalisation matter), then pick a **Modo de impresión**:
  - **Directo (ESC/POS)** — real thermal receipt printers.
  - **Por driver de Windows** — printers with **no** ESC/POS support, e.g. EPSON EcoTank
    inkjets (`EPSON L3210 Series`, `EPSON L1250 Series`, …). Inkjets **only** work in this mode.

## 5. Run the agent

On the printer's PC:

```powershell
cd C:\ember-agent
java -jar printing-agent-0.1.0-SNAPSHOT.jar
```

(Optionally pass the config path: `java -jar printing-agent-0.1.0-SNAPSHOT.jar C:\ruta\agent.properties`.)

Success looks like:

```
[print-agent] conectado, agentId=..., impresoras=1
```

Leave the window open — the agent only prints while it is running. `impresoras=0` means the
printer is inactive or wasn't saved; check the admin UI.

## 6. Keep it running automatically

Windows **Task Scheduler** → **Create Task** → trigger **At log on** → action:
`java -jar C:\ember-agent\printing-agent-0.1.0-SNAPSHOT.jar`, "Start in" `C:\ember-agent`.

## Troubleshooting

- **`Windows print queue not found: <name>`** — the queue name doesn't match `Get-Printer`
  output exactly. Fix it in **Agregar impresora**.
- **`This port appears to have been shutdown or disconnected`** — a USB/COM printer config
  points at a port that isn't there; deactivate or delete that printer.
- **Nothing prints, no error** — an inkjet configured as **Directo (ESC/POS)**. Switch it to
  **Por driver de Windows**.
- **`conexión perdida, reintentando`** — the PC can't reach `backend.base-url`; check the URL
  and network.
