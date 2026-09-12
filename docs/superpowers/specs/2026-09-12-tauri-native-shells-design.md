# Ember Hub v2 y Printer Agent v2 — shell nativo Tauri — Diseño

> Estado: **spec para revisión**. Retoma el ítem diferido explícitamente en
> `docs/superpowers/specs/ember_hub.md` §2.3 ("v1.1: Tauri como shell de
> UI/bandeja") y en `docs/superpowers/specs/2026-09-08-print-agent-installer-design.md`
> ("Convergencia a Tauri, diferida... el trabajo Tauri es su propio spec y toca
> las dos apps a la vez"). Cubre **ambas** apps con una sola decisión
> arquitectónica; los **planes de implementación son documentos separados**,
> uno por app. **Orden de ejecución: printer-agent v2 primero, Ember Hub v2
> después.** Trabajo en la rama `spec/tauri-native-shells`.

## 1. Motivación

Ni Ember Hub ni printer-agent tienen hoy ninguna pantalla propia "bonita":

- **Ember Hub** (`backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`):
  ventana Swing plana con 4 `JLabel` de estado (Postgres/MinIO/Servidor/Licencia)
  y 5 botones (`Iniciar`, `Detener`, `Seleccionar license.key…`, `Abrir en
  navegador`, `Salir`). No es la app de trabajo — es el **launcher/panel de
  control** que arranca los servicios y luego abre el SPA real del SaaS en el
  navegador del sistema.
- **printer-agent** (`printing-agent/src/main/java/com/vanter/emberagent/ui/`):
  `AgentDashboard`/`AgentTrayIcon`/`PairDialog`, Swing puro, sin servidor web
  propio — consume `StatusHub` directo en el mismo proceso.

Ambas son "feas y rústicas" (Swing por defecto) frente al resto del producto
(React 19 + Tailwind 4 + shadcn/ui). El objetivo de este spec es **solo
visual/de shell**: reemplazar esas dos ventanas de control por una ventana
nativa (Tauri + WebView2) con la calidad del resto del SaaS — **no** se toca
la app de trabajo real (el SPA del SaaS sigue abriéndose en el navegador
externo desde Hub, sin cambios) ni la lógica de negocio de ninguna de las dos
apps (bootstrap de Postgres/MinIO, licencia, pairing, impresión — todo eso
sigue funcionando exactamente igual).

**Por qué printer-agent primero:** es el caso más simple de los dos (menos
estados, sin bootstrap de base de datos portátil) y sirve como validación del
patrón arquitectónico antes de aplicarlo al Hub, que tiene más superficie
(Postgres/MinIO portátiles, licenciamiento, heartbeat).

## 2. Decisiones tomadas

### 2.1 Tecnología de shell: Tauri + WebView2

Se evaluaron 3 opciones (ver discusión de brainstorming): Tauri+WebView2,
JavaFX `WebView` embebido, y re-skin puro en Swing (FlatLaf). Se eligió
**Tauri + WebView2** porque:

- Renderiza HTML/CSS/JS real — permite igualar el mockup de referencia
  (`ember_printer_agent_v2.png`, cards/badges/tabla con el rojo de marca) sin
  pelear contra las limitaciones de Swing o del motor WebKit de JavaFX.
- WebView2 ya viene instalado en Windows 10/11 — no hay que empaquetar un
  Chromium propio como haría Electron (descartado explícitamente por ese
  costo de instalador, igual que ya se descartó Docker Desktop como vía de
  distribución en `ember_hub.md` §3).
- El riesgo de "el equipo no tiene experiencia en Rust" (la razón por la que
  `ember_hub.md` §2.3 excluyó Tauri de v1) se mitiga porque el shell que se
  necesita es casi todo configuración (`tauri.conf.json` + definir un
  sidecar) — muy poco Rust escrito a mano.

### 2.2 Modelo de proceso: Tauri es el proceso principal, Java es un sidecar sin ventana propia

Dos procesos por app, en vez de uno:

```
Instalador final (generado por el bundler de Tauri)
  │
  └─ App instalada = binario Tauri (Rust + WebView2)
       ├─ Ventana nativa → build estático (Astro) cargado desde disco local
       ├─ Icono de bandeja (API nativa de Tauri)
       └─ Al abrir: spawnea como proceso hijo (sidecar)
             java-runtime/ (jlink, SIN CAMBIOS)
             app/*.jar (SIN CAMBIOS en lógica de negocio)
                  └─ núcleo headless existente (AgentRunner / bootstrap Hub)
                  └─ NUEVO: mini servidor HTTP de control, loopback-only
```

Se descartó el modelo inverso (Java sigue siendo el proceso principal y abre
Tauri como ventana embebida) por ser un uso no estándar del framework, sin
documentación/ejemplos de referencia, y porque obligaría a mantener dos
sistemas de empaquetado a la vez (jpackage/Inno Setup + Tauri) en vez de uno.

Justificación adicional de por qué dos procesos y no una sola app Tauri con
toda la lógica reescrita en Rust: la lógica de negocio de ambas apps (JDBC,
Hibernate, JNA/DPAPI, cliente STOMP, bootstrap de Postgres portátil) es Java
existente, probada y con cobertura de tests real — reescribirla en Rust no
está en alcance ni se justifica solo para mejorar una ventana de estado.

### 2.3 Canal de comunicación Tauri ↔ Java: HTTP local, loopback-only, sin auth

Los dos procesos no pueden llamarse funciones entre sí directamente, así que
necesitan un canal. Se eligió un servidor HTTP mínimo (`com.sun.net.httpserver.HttpServer`
del JDK — **sin nuevo framework/dependencia**, mismo criterio que ya usó
`CsvWriter` en EMB-EXPORT antes del cambio a POI) bindeado únicamente a
`127.0.0.1` en un puerto efímero:

- El proceso Java elige el puerto al arrancar y lo comunica a Tauri
  imprimiendo una única línea a stdout (`PORT=<n>`) al terminar de levantar
  el servidor de control — Tauri lee esa línea del proceso hijo antes de
  considerar el sidecar "listo".
- Sin autenticación: el canal nunca sale de la PC (no hay listener en
  `0.0.0.0` ni en la IP de LAN), el mismo modelo de confianza que ya usa
  cualquier proceso local hablándose a sí mismo.
- **Actualizaciones en vivo por polling** (1–2s desde la ventana), no
  WebSocket/SSE — decisión consistente con `ember_hub.md` §2.7, que ya
  rechazó conexión persistente para el canal de sync Hub↔nube por la
  complejidad de reconexión que eso implica (ver "WebSocket endpoint
  isolation gotcha" en `PROGRESS.md`). Una ventana de estado local no
  justifica esa complejidad.

### 2.4 Frontend de la ventana: proyecto Astro + React + Tailwind, separado

Un proyecto nuevo e independiente por app (`printing-agent/ui/`, y más
adelante `ember-hub/ui/`), con su propio `package.json`, **no** un módulo
compartido con `frontend/` ni con `landing/`. Copia únicamente los tokens de
diseño de `frontend/src/index.css` (Tailwind 4 `@theme inline`: `--primary:
oklch(0.395 0.175 28.5)` = rojo de marca, `--radius` y su escala) para verse
consistente con el resto del producto sin acoplarse a su código. Build
estático (`astro build`) — sin servidor Node en producción, Tauri carga los
archivos directo del disco.

### 2.5 Alcance funcional de la v2 (fase 1): paridad, no features nuevas

La ventana Tauri de printer-agent iguala lo que `AgentDashboard`/`PairDialog`
ya hacen hoy — **no** agrega los botones de acción nuevos que aparecen en el
mockup de referencia (`Reiniciar Servicio`, `Imprimir Página de Prueba`,
`Configurar RAW`), que quedan explícitamente diferidos a una fase 2 futura
fuera de este spec, porque requieren lógica nueva del lado del agente (no
solo la ventana). Pantallas de la fase 1:

- **Estado de conexión:** fase (`UNPAIRED`/`CONNECTING`/`CONNECTED`/
  `RETRYING`), último heartbeat, id del agente — espejo de `StatusHub.Snapshot`.
- **Emparejamiento:** input de código → `PairingClient.redeem` vía el server
  local.
- **Impresoras detectadas:** lista de solo lectura de `WindowsPrinterEnumerator`.
- **Trabajos recientes:** tabla de solo lectura de los últimos 20 `JobRecord`
  de `StatusHub`.
- Bandeja: clic abre/enfoca la ventana, menú "Abrir"/"Salir" — reemplaza
  `AgentTrayIcon` usando la API de bandeja de Tauri.

Para Ember Hub v2 (implementado después, en su propio plan) el mismo patrón
aplica sustituyendo qué expone el sidecar: estado de Postgres/MinIO/Servidor,
selección de `license.key`, estado de licencia/heartbeat, y el botón "Abrir
en navegador" — sin tocar el SPA que ese botón abre.

## 3. Contrato de API local (printer-agent, fase 1)

Todo bajo `http://127.0.0.1:<puerto>/`, JSON:

| Método/ruta | Uso |
|---|---|
| `GET /api/status` | Snapshot completo: `{phase, detail, lastSeen, agentId, printerCount, recentJobs[]}` — espejo directo de `StatusHub.Snapshot`. |
| `POST /api/pair` `{code}` | Envuelve `PairingClient.redeem`. `200` + estado actualizado, o el mismo `4xx`/mensaje que ya produce `PairingClient` (429 → "Demasiados intentos…", no-200 → "Código inválido…"). |
| `GET /api/printers` | Última lista reportada por `WindowsPrinterEnumerator` (cacheada en memoria, no re-escanea en cada llamada). |

## 4. Manejo de errores

- **El sidecar no arranca** (puerto ocupado, JAR corrupto, runtime jlink
  roto): si Tauri no recibe la línea `PORT=` dentro de un timeout corto
  (ej. 5s), la ventana muestra un estado de error explícito ("El agente no
  pudo iniciar") con un botón que abre la carpeta de logs existente — no se
  queda cargando indefinidamente.
- **El sidecar muere en caliente:** Tauri detecta el proceso hijo terminado →
  la ventana pasa a estado de error con botón "Reintentar" que vuelve a
  spawnear el sidecar, sin reinstalar nada.
- **Un ciclo de polling falla:** no es fatal — reintento silencioso en el
  siguiente ciclo, mismo criterio que ya asumen los ciclos de sync de Hub
  (`ember_hub.md` §2.7).
- **Emparejamiento falla:** los mensajes ya existen en `PairingClient`; la
  API local los pasa tal cual, la ventana solo los muestra.

## 5. Empaquetado

- `jlink`/`jpackage` de printing-agent **no cambian** — solo se le quita a
  `Main` la superficie Swing. Los modos `--tray`/`--headless` colapsan a un
  único modo "sidecar": arranca el núcleo headless existente + el nuevo
  servidor de control, imprime `PORT=<n>` a stdout y queda corriendo.
- El bundler de Tauri arma el instalador final (`.msi` o NSIS `.exe`),
  empaquetando el app-image de jlink/jpackage como recurso interno y
  spawneándolo como sidecar al abrir. **Reemplaza**
  `printing-agent/installer/EmberAgent.iss` (Inno Setup).
- Autoarranque al iniciar sesión vía el plugin de autostart de Tauri —
  reemplaza el atajo en `{commonstartup}` que usa el `.iss` actual.
- CI (`lint.yml`, job `build-print-agent`): se actualiza para compilar
  también `printing-agent/ui/` (Astro) y el proyecto Tauri, no solo el
  jar+jpackage.

Ember Hub v2 sigue el mismo patrón de empaquetado (jlink/jpackage del JAR sin
Swing + bundler de Tauri), detallado en su propio plan de implementación.

## 6. Plan de pruebas

- **Java (printer-agent):** `AgentRunner`/`StatusHub`/`PairingClient`/etc. no
  cambian de lógica — su suite (**41/41** a la fecha de este spec) sigue
  intacta. Se agregan tests unitarios para el nuevo servidor de control
  (`/api/status`, `/api/pair`, `/api/printers`, casos de error), con el mismo
  estilo hermético que ya usan `PairingClientTest`/`WindowsPrinterEnumeratorTest`.
- **Astro/React:** tests de componente para las 4 pantallas, mockeando el
  `fetch` a `/api/*` — análogo a `test:run` del frontend del SaaS.
- **Verificación manual** (extiende `printing-agent/VERIFY.md`): instalar en
  una PC limpia, confirmar que el sidecar arranca, la ventana muestra estado
  real, el pairing funciona, la bandeja funciona, y que un crash del sidecar
  se refleja como error en la ventana en vez de quedarse colgada.

## 7. Riesgos identificados (documentados, no bloqueantes)

- **Rust real, aunque acotado:** aun con la mayoría del trabajo en
  configuración, cualquier ajuste fino del sidecar (parseo de stdout,
  manejo de señales al cerrar la ventana para no dejar el proceso Java
  huérfano) requiere algo de Rust. Se mitiga arrancando por printer-agent
  (el caso más simple) antes de Hub.
- **`astro build` como fuente de la ventana:** hay que confirmar en la
  implementación que el build estático de Astro funciona bien servido desde
  `tauri://` (protocolo interno de Tauri) sin URLs absolutas rotas — riesgo
  bajo pero no verificado en este spec.
- **Cierre de proceso huérfano:** si Tauri crashea o se lo mata a la fuerza
  (Task Manager) sin pasar por su ciclo normal de cierre, el sidecar Java
  podría quedar corriendo en segundo plano. Se resuelve en la
  implementación (ej. Tauri vigila su propio proceso padre, o el sidecar
  hace polling de "¿sigue vivo mi padre?" y se autotermina) — el mecanismo
  exacto se define en el plan, no en este spec.

## 8. Fuera de alcance de este spec (explícito)

- Botones de acción nuevos del mockup (reiniciar servicio, imprimir página
  de prueba, configurar RAW) — fase 2 futura, no incluida aquí.
- Cualquier cambio a la app de trabajo real (el SPA del SaaS que Hub abre en
  el navegador) — sigue exactamente igual.
- Cualquier cambio a la lógica de negocio de printer-agent o Hub (bootstrap,
  licenciamiento, impresión, sync) — solo cambia la presentación/shell.
- El plan de implementación de Ember Hub v2 en sí — es un documento
  separado, ejecutado después de que printer-agent v2 esté validado.
