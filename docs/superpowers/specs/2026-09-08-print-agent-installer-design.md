# Agente de impresión — App de escritorio + instalador Windows — Diseño

> Estado: **spec para revisión**. Continúa `docs/superpowers/specs/2026-08-22-emb-print-design.md`
> (el runtime del agente ya existe: `printing-agent/`, package `com.vanter.emberagent`,
> reports 180, 249–255, 355, 394–395). Cubre **solo** la superficie de instalación y
> operación en la PC del cliente — no cambia el protocolo de impresión (STOMP / ACK /
> render RAW vs DRIVER), que funciona y está verificado contra hardware real (report 254,
> EPSON L3210).

## 1. Contexto

Hoy el agente es un `java -jar` puro:

- El cliente debe **instalar Java 17** por su cuenta.
- Debe crear a mano `agent.properties` con `backend.base-url` + `agent.api-key`
  (`AgentConfig.load`), pegando la API key que el admin mostró una sola vez.
- Lo arranca con `java -jar printing-agent-…​.jar` en una consola que **no puede
  cerrar** (solo imprime mientras corre).
- El auto-arranque es configurar el Programador de tareas a mano (README §6).
- En "Agregar impresora" del admin hay que **tipear** el nombre exacto de la cola
  de Windows (`Get-Printer`) — causa #1 de fallos de bring-up.
- Si el agente se cae, no hay señal: te enterás porque no sale papel.

No hay **ninguna UI del lado de la PB del cliente**. Para un producto SaaS
(`app.ember.vanter.net`) eso es onboarding inaceptable.

La restricción que **no** se puede quitar: un SaaS en la nube no alcanza una
impresora en la LAN (menos USB) del restaurante. Algo tiene que correr
on-premise. Lo que se rediseña es *cómo se instala y se opera* ese proceso.

### Alcance confirmado en brainstorming (2026-09-08)

- **El agente es SOLO para la nube.** Es la pieza on-premise de los clientes de
  `app.ember.vanter.net`. El **Ember Hub es otra cosa** y no lleva agente — ver
  §4.1: al estar instalado en el mismo PC que las impresoras, el Hub las detecta
  y las usa directo.
- **UI nativa, no navegador.** El agente muestra una **ventana Swing** con el mismo
  espíritu que `com.vanter.ember.hub.dashboard.HubDashboard` (estado + errores en
  español + botón de acción), no una página servida en `localhost`.
- **Convergencia a Tauri, diferida.** Cuando el **Hub v2** pase a un shell Tauri que
  reusa el diseño de `frontend/`, **el Hub y el agente migran juntos** a esa misma
  UI. Este spec entrega la v1 Swing; el trabajo Tauri es su propio spec y toca las
  dos apps a la vez.
- **Auto-arranque: sí, obligatorio.** Mecanismo: **atajo en la carpeta *Startup***
  del usuario (patrón del Hub), no servicio Windows headless. Motivo: mantener el
  dashboard + tray como única superficie del proceso, sin socket de control
  dashboard↔servicio ("todo junto"). El PC donde corre el agente está encendido y
  con sesión iniciada durante el horario, igual que la PC-servidor del Hub.
- **Un solo módulo.** Swing + JNA + core headless conviven en `printing-agent/`
  (un pom). No se parte en `printing-agent-app/`.
- **Emparejamiento por código, y la key PERSISTE.** El admin genera un **código
  corto**; el agente lo canjea **una vez** por su API key y la guarda. `POST /pair`
  **devuelve la key existente del agente — no la rota**. Regenerar es una acción
  admin explícita y rara (`RegenerateKeyButton`). La credencial sobrevive
  reinicios y updates (vive en `%ProgramData%`), así el operador **no vuelve a
  emparejar nunca** salvo que borre datos o cambie de PC.
- **Impresora por desplegable.** El agente enumera `Get-Printer` y reporta la lista
  al backend; en "Agregar impresora" del admin se elige de un `<select>`, no se
  tipea. El agente marca cuáles son inkjet EcoTank → sugiere modo DRIVER.
- **Empaquetado como el Hub.** jlink (JRE derivado) + jpackage (app-image) + Inno
  Setup (`.exe`), reusando el patrón de `ember-hub/build-installer.ps1` y
  `ember-hub/installer/EmberHub.iss`. El cliente **no** instala Java.
- **Distribución del `.exe`:** link de descarga en el propio admin
  (`Configuración → Impresión`), ya que el cliente ya está en la web. Nada de
  entrega manual.
- **Versión propia.** El agente versiona por `printing-agent/pom.xml` (hoy
  `0.1.0-SNAPSHOT`), independiente de `backend/pom.xml`.
- **Credencial cifrada en disco.** La API key se guarda con **DPAPI**
  (`CryptProtectData`, ámbito máquina) — no en `agent.properties` en texto plano.
  Cierra la deuda **F-24**.

## 2. Arquitectura v1

Módulo Java nuevo dentro de `printing-agent/` (o submódulo `printing-agent-app/`
si conviene aislar Swing del core headless). El core actual
(`AgentConnection`, `PrintJobDispatcher`, `*Sender`, `AuthClient`,
`PrinterConfigClient`) se reusa **sin cambios de lógica**; se le añade una fachada
de estado observable.

### 2.1 `AgentDashboard` (Swing)

Una `JFrame` única, minimizable a la bandeja, con:

| Zona | Contenido |
|---|---|
| Encabezado | logo Ember, versión del agente, estado global (círculo verde/ámbar/rojo) |
| Conexión | `backend.base-url` (solo lectura salvo modo avanzado), estado STOMP (`Conectado` / `Reintentando…` / `Sin emparejar`), última vez visto |
| Impresora | `JComboBox` con las colas de `Get-Printer`; rol (Cocina/Recibo); modo (Directo/Driver, autopreseleccionado); botón **Guardar** y **Imprimir página de prueba** |
| Actividad | tabla de los últimos N trabajos (hora, rol, cola, estado, error) — de un buffer local, no del backend |
| Pie | **Abrir carpeta de logs**, **Copiar diagnóstico** (versión, SO, java, base-url, agentId, últimas líneas de log) |

Primer arranque sin credencial → un diálogo modal **"Emparejar este agente"**:
campo para el código (6–8 chars), o link "Tengo una API key" para el fallback.
Al canjear correctamente escribe la credencial cifrada y arranca la conexión.

Reusa de `hub/dashboard`: el patrón de errores reintentables en español y
`hub/tray/HubTrayIcon` como base de `AgentTrayIcon` (mostrar/ocultar ventana,
estado en el tooltip, "Salir").

### 2.2 Emparejamiento por código

**Backend** (`printing` module):

- `POST /print-agents/{id}/pairing-code` (ADMIN) → genera un código corto de un
  solo uso, TTL ~15 min, ligado al `PrintAgent` y al tenant. Devuelve `{ code, expiresAt }`.
- `POST /print-agents/pair` (sin auth, rate-limited) — body `{ code }` → valida
  (existe, no usado, no expirado) → devuelve `{ apiKey, backendBaseUrl, agentId, agentName }`
  y marca el código consumido. La `apiKey` es la misma que hoy (o se rota en este
  paso).
- El heartbeat/estado `connected` ya existe (`PrintAgentResponse.connected`); no
  cambia.

**Agente:**

- `PairingClient.redeem(code)` → guarda `AgentCredential` cifrada
  (`{ apiKey, backendBaseUrl }`), luego el flujo normal `AuthClient` → JWT → STOMP.
- `AgentConfig.load` pasa a leer primero la credencial cifrada; `agent.properties`
  queda como override para dev / soporte.

**Frontend admin** (`CreateAgentModal` + `PrintingSettings`):

- Tras "Generar agente", la pantalla "clave mostrada una vez" muestra el **código
  de emparejamiento** en grande (y, plegado, la API key cruda "para instalación
  avanzada"). Texto: "Abre Ember Agent en la PC de la impresora e ingresa este
  código."
- Botón **"Nuevo código"** en cada agente no emparejado / desconectado
  (equivalente a `RegenerateKeyButton`, endpoint nuevo).

### 2.3 Descubrimiento de impresoras

- Agente: al conectar y cada vez que refetchea configs (ya lo hace por job,
  report 251), enumera `Get-Printer` (PowerShell o WMI `Win32_Printer`) y hace
  `POST /print-agents/{id}/discovered-printers` con `[{ name, driverName, portName, isInkjetGuess }]`.
- Backend: guarda la última lista por agente (columna JSON en `print_agents` o
  tabla `agent_discovered_printers`), la expone en `PrintAgentResponse`.
- Frontend `AddPrinterModal`: si `connectionType === WINDOWS_QUEUE`, el campo
  `windowsQueueName` pasa a `<select>` poblado con esa lista; `renderMode` se
  autopreselecciona a `DRIVER` cuando `isInkjetGuess`. Tipeo libre sigue
  disponible como opción "otra…".

### 2.4 Almacenamiento de la credencial

- Windows: DPAPI vía JNA (`Crypt32.CryptProtectData` / `CryptUnprotectData`),
  ámbito **máquina** (`CRYPTPROTECT_LOCAL_MACHINE`) para que el servicio la lea
  sin sesión de usuario. Archivo `%ProgramData%\EmberAgent\credential.bin`.
- Fallback dev / no-Windows: `agent.properties` como hoy (con aviso en el log).
- Cierra **F-24** (key en texto plano en disco).

### 2.5 Empaquetado e instalador

Reusa `ember-hub/` como plantilla. Script `printing-agent/build-installer.ps1`:

1. `mvn -pl printing-agent -am -DskipTests package` → fat jar.
2. `jlink` → runtime mínimo (el jar de STOMP/tyrus/jSerialComm/escpos-coffee **no**
   es modular → runtime completo menos módulos claramente sobrantes, como el Hub).
3. `jpackage --type app-image` → `Ember Agent.exe` + `runtime/` + `app/…​.jar`.
4. **Inno Setup** → `EmberAgentSetup-<version>.exe`.

**Layout tras instalar:**

```
%ProgramFiles%\Ember Agent\          (se reemplaza en cada update)
  Ember Agent.exe                     launcher jpackage
  runtime\                            JRE embebido
  app\printing-agent.jar
  uninstall.exe

%ProgramData%\EmberAgent\            (sobrevive updates; el desinstalador pregunta antes de borrar)
  credential.bin                      API key cifrada (DPAPI, ámbito máquina)
  agent-state.json                    agentId, backendBaseUrl, última impresora elegida
  logs\                              rotados
```

**Auto-arranque:** atajo en la carpeta *Startup* común (`{commonstartup}`, como
`ember-hub/installer/EmberHub.iss` línea 46) que lanza `Ember Agent.exe`
minimizado a la bandeja. Un solo proceso (dashboard + tray + core), sin servicio
ni IPC. No imprime hasta que alguien inicia sesión en esa PC — aceptado: es el
mismo supuesto que el Hub (PC encendida y con sesión durante el horario).

El instalador agrega accesos directos en menú Inicio + escritorio que abren el
dashboard. No hace falta regla de firewall: el agente **sale** por WebSocket, no
escucha.

## 3. Cambios por módulo

**backend/** (`com.vanter.ember.printing`)

- `PairingCode` entity + `pairing_codes` migración (`V10`), o campo en `print_agents`.
- `PrintAgentController`: `+ pairing-code` (ADMIN), `+ /pair` (público, rate-limited),
  `+ /discovered-printers` (agente, auth por API-key/JWT).
- `PrintAgentResponse`: `+ discoveredPrinters`, `+ paired` (bool).
- Rate-limit + auditoría del endpoint `/pair` (reusar el patrón de `pin-login`).

**printing-agent/** (`com.vanter.emberagent`)

- Nuevo: `ui/AgentDashboard`, `ui/AgentTrayIcon`, `ui/PairDialog`.
- Nuevo: `PairingClient`, `WindowsPrinterEnumerator`, `dpapi/CredentialStore` (JNA).
- `AgentConfig` / `Main`: leer credencial cifrada primero; `Main` decide
  headless (servicio) vs con-ventana (dashboard) por arg/flag.
- `StatusHub` observable que el core alimenta y el dashboard/tray consumen.
- Socket local de control (si se va por servicio).

**frontend/** (`src/pages/admin/components/settings/printing/`)

- `CreateAgentModal`: mostrar código de emparejamiento; key cruda plegada.
- `PrintingSettings`: botón "Nuevo código"; badge "Emparejado / Sin emparejar".
- `AddPrinterModal`: `windowsQueueName` → `<select>` desde `discoveredPrinters`;
  `renderMode` autopreselección DRIVER para inkjet.
- i18n ES/EN de todas las cadenas nuevas.

**printing-agent/** (build)

- `build-installer.ps1`, `installer/EmberAgent.iss`, `installer/ember-agent.ico`,
  `jlink-modules.txt`, `build.env(.example)` — clonados de `ember-hub/`.
- CI: job que al menos compila el fat jar y corre `jpackage --type app-image`
  (el `.exe` de Inno queda como verificación manual, como el Hub).

## 4. Relación con el Ember Hub

### 4.1 El Hub detecta impresoras locales directo (workstream propio)

El Hub corre en el **mismo PC que las impresoras** (`SPRING_PROFILES_ACTIVE=hub`,
un monolito con acceso al hardware). No necesita agente externo ni que nadie
tipee el nombre de la cola. **Pieza nueva para el Hub** (spec/plan aparte, pero
comparte frontend con este):

- Un **enumerador local** en el proceso Hub (mismo `WindowsPrinterEnumerator` que
  usará el agente) expuesto por un endpoint solo-Hub, p. ej.
  `GET /hub/local-printers` → `[{ name, driverName, portName, isInkjetGuess }]`.
- En `AddPrinterModal`, cuando `isHubBuild()`, el `<select>` de
  `windowsQueueName` se llena de **ese** endpoint en vez de
  `discoveredPrinters` del agente. Misma UI, distinta fuente.
- En el Hub, los `PrinterConfig` los sirve el propio Hub y los jobs se despachan
  **in-process** (el sender ya existe: `WindowsPrintQueueSender` /
  `NetworkPrinterSender` / `UsbPrinterSender` viven en `printing-agent/` — o se
  mueven a un módulo compartido `printing-senders/` que consuman ambos). Sin
  STOMP hacia afuera.
- El concepto "agente" queda **oculto** en el build Hub (como ya se ocultan
  `/customer/*` y el UI de loyalty, report 398).

Esto es lo que pidió el owner: "el ember-hub al estar instalado debe reconocer
las impresoras directamente". Se ataca después del agente cloud, reusando el
enumerador y el `<select>`.

## 5. Fuera de alcance / diferido

- **Shell Tauri + UI estilo SaaS** — su propio spec, junto con Hub v2; migra Hub y
  agente a la vez.
- **Agente macOS / Linux** — solo Windows en v1 (jpackage por plataforma; DPAPI es
  Windows-only, en otros SO iría Keychain / libsecret).
- **Auto-provisión de impresoras** (el agente crea las `PrinterConfig` solo) — en
  v1 el admin sigue eligiendo rol + etiqueta; solo el nombre de cola se
  autocompleta.
- **Firma del `.exe`** (code-signing cert) — deseable para SmartScreen, decisión de
  compra aparte; no bloquea v1.

## 6. Decisiones cerradas (brainstorming 2026-09-08)

| # | Pregunta | Decisión |
|---|---|---|
| 1 | Auto-arranque: Servicio vs atajo Startup | **Atajo Startup** (patrón Hub); un solo proceso, sin IPC |
| 2 | ¿Módulo aparte para Swing/JNA? | **No** — todo en `printing-agent/`, un pom |
| 3 | ¿`/pair` rota la API key? | **No** — devuelve la existente; la key persiste y no se vuelve a emparejar |
| 4 | Distribución del `.exe` | **Link de descarga en el admin** (`Configuración → Impresión`) |
| 5 | Dónde vive `discoveredPrinters` | **Columna JSON en `print_agents`** (se pisa; sin histórico) |
| 6 | Versionado del agente | **Propio** (`printing-agent/pom.xml`), independiente del backend |

### Quedan por decidir en el plan

- Migración Flyway para `pairing_codes` (o campo en `print_agents`) — número `V10`.
- Si los `*Sender` se mueven a un módulo `printing-senders/` compartido con el Hub
  (§4.1) o se dejan en `printing-agent/` y el Hub depende de ese jar.
- Firma del `.exe` (code-signing) — compra aparte, no bloquea v1.

---

Próximo paso tras revisión: dos planes de implementación en
`docs/superpowers/plans/`:

1. `2026-09-08-print-agent-installer.md` — el agente cloud (fases: backend pairing
   + discovery → core observable + credential store DPAPI → dashboard/tray →
   frontend admin (código + `<select>` + link de descarga) → instalador
   jlink/jpackage/Inno → verificación manual en PC limpia sin Java).
2. `2026-09-08-hub-local-printer-detection.md` — §4.1: enumerador local en el Hub,
   endpoint `GET /hub/local-printers`, `<select>` alimentado desde ahí en build
   Hub, despacho in-process sin STOMP.
