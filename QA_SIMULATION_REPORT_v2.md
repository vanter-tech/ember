# QA_SIMULATION_REPORT_v2.md

**Ember — 2ª simulación funcional (verificación en vivo de `AUDIT_BLUEPRINT.md` post-`FIX-QA`)**

| Campo | Valor |
|---|---|
| Fecha | 2026-09-04 |
| Método | **Ejecución real** contra `backend` (Spring Boot, perfil por defecto) + `frontend` (Vite dev), Postgres/MinIO en Docker, sobre el mismo tenant semilla "Demo" (+ tenant "Embers" para las pruebas cross-tenant) usado en la sesión original. Login real como ADMIN, WAITER, KITCHEN y CUSTOMER; tráfico HTTP real con `curl`, un cliente STOMP real en Node (`@stomp/stompjs`, tomado de `frontend/node_modules`) para la suscripción cruzada. **La extensión de Chrome (`claude-in-chrome`) no estaba conectada en esta sesión** — los hallazgos puramente de UI (E-03, E-11, E-12, E-13, E-17, E-18, E-19, E-20, E-21) se re-verificaron **leyendo el código fuente actual** en los archivos/líneas que `reports/361-fix-qa-branch-remediation.md` dice haber tocado, no clic-a-clic en un navegador real; se marcan explícitamente como `CONFIRMADO POR CÓDIGO` más abajo, no `[EN VIVO]`. |
| Rama | `main` (HEAD `672fed04`, tras el merge de `FIX-QA` vía PR #73) |
| Insumo | `AUDIT_BLUEPRINT.md` (F-01..F-24) + `QA_SIMULATION_REPORT.md` (E-01..E-23, la simulación en vivo original) + `reports/361-fix-qa-branch-remediation.md` (qué se arregló y qué se dejó fuera de alcance a propósito) |
| Cuentas usadas | Las mismas 5 de la sesión original, reseteadas a `Testing123!` sólo en la BD de desarrollo local: `admin@demo.com`, `mesero@demo.com`, `mesero2@demo.com`, `cocinero@demo.com` (tenant "Demo") y `fe3@ember.com` (WAITER del tenant "Embers", víctima cross-tenant). + 1 cuenta CUSTOMER nueva (`qa.v2.customer@example.com`, registrada esta sesión). |
| Estado del entorno tras la sesión | `billing.taxRate` del tenant "Demo" restaurado a `0` (valor original). `space.totalTables` sin tocar (seguía en `10`, aunque hay 15-17 filas de `DiningTables` visibles en el dashboard — residuo de mesas creadas por sesiones de prueba anteriores/actuales, no de esta sesión modificando el conteo). **Turno de caja #1 (tenant Demo)** — ya venía abierto y vencido desde la sesión original (con 2 pagos duplicados de prueba, sin tocar) — fue **prolongado dos veces** en esta sesión para poder ejercitar el flujo de pago físico; ahora tiene además **1 pago físico real de \$11.00 y 1 pago digital confirmado de \$11.00** de las pruebas F-03/F-04 de esta sesión. **Ninguno de estos artefactos se limpió** — mismo criterio que la sesión original (no tocar sin revisión humana, ver Backlog). 4 mesas nuevas quedaron con sesión `OPEN`/`PAID`-no-cerrada en "Demo" (mesas 3, 12→cancelada, 13, 14→cerrada al pagar, 15→cerrada al pagar) y 2 en "Embers" (mesas 4, 5) por las pruebas de este informe. Backend y frontend dev que se levantaron para esta sesión se **detuvieron** al finalizar (no estaban corriendo antes de empezar, tampoco quedaron corriendo después). |

> **Nota de rigor.** Cada hallazgo marcado **[EN VIVO]** fue disparado contra el sistema real corriendo
> en esta sesión — respuesta HTTP real, frame STOMP real, o stack trace real de log, capturados en el
> momento. Los marcados **CONFIRMADO POR CÓDIGO** se verificaron leyendo el archivo/línea actual en el
> árbol de trabajo (no ejecución de UI, por la falta de extensión de Chrome esta sesión — ver Método).
> Los marcados **ABIERTO POR DISEÑO** son decisiones de producto documentadas en `reports/361-*.md §6/§8`,
> no regresiones ni descuidos.

---

## 1. Veredicto de Estabilidad

# 🟢 MEJORA SUSTANCIAL — las 5 fallas Bloqueantes originales están cerradas

De los 5 hallazgos **Bloqueante** de la simulación original (fuga de datos cross-tenant por STOMP,
toma de configuración por un comensal, choque total de la pantalla de pedido, cobro físico
duplicado, e impuesto configurado nunca aplicado), **los 5 se re-probaron en vivo y los 5 están
cerrados**, con evidencia nueva (frames STOMP reales, respuestas HTTP reales, totales de factura
reales). De los 8 hallazgos **Alta**, los 6 verificables por HTTP están cerrados; los 2 restantes
(E-11 blank screens, y el resto de UX) se confirmaron por código, no en vivo, por falta de
navegador esta sesión.

**Una brecha nueva, no reportada antes, aparece en esta sesión:** presentar un **token QR** de
sesión (el que un comensal recibe al escanear el código de mesa) como `Authorization: Bearer` en
cualquier ruta que espere un token de usuario produce un **500 Internal Server Error** sin manejar
(`UsernameNotFoundException` sin capturar), no el 401 limpio que `AUDIT_BLUEPRINT.md` F-13 exige
como comportamiento esperado. No es una brecha de datos (el token sigue sin dar acceso a nada), pero
es un defecto de manejo de errores real y en vivo, confirmado en dos rutas distintas. Ver **F-13**
más abajo — **STILL BROKEN**, no estaba en el alcance de `FIX-QA` (reports 361 no lo lista), así que
no es una regresión de esa rama, pero tampoco está resuelto y `AUDIT_BLUEPRINT.md` lo esperaba
cerrado ("no bypass demostrable" ≠ "sin excepción no manejada").

El resto de la superficie **ABIERTA POR DISEÑO** (E-23/F-10 oráculo de PIN, F-14/F-15 RBAC de
plataforma y hash de contraseña en activación Hub, F-17 almacenamiento de JWT, F-20/F-21/F-22/F-24
higiene de config/secretos) permanece exactamente como estaba — decisiones de producto documentadas,
no descuidos de esta ronda.

---

## 2. Matriz de re-verificación — `AUDIT_BLUEPRINT.md` F-01..F-24

| ID | Hallazgo original | Estado ahora | Evidencia de esta sesión |
|---|---|---|---|
| **F-01** | STOMP `SUBSCRIBE` no autoriza destino — fuga cross-tenant | ✅ **RESUELTO** [EN VIVO] | Cliente STOMP real (`@stomp/stompjs`) autenticado como CUSTOMER del tenant "Demo" hace `CONNECT` + `SUBSCRIBE /topic/waiter/{tenantId-Embers}` → el broker responde **`STOMP ERROR: Not authorized to subscribe to this destination`**, cero frames entregados. Control positivo: el mismo cliente suscrito a `/topic/waiter/{propio-tenant}` como WAITER legítimo **sí** recibe el frame (`{"tenantId":"2978...","sessionId":"f1581884...","tableNumber":12}`) — el caso legítimo no se rompió. |
| **F-02** | `PUT /settings` sin `@PreAuthorize` — cualquier autenticado reescribe config | ✅ **RESUELTO** [EN VIVO] | Token de CUSTOMER con `rid` (post-join) → `PUT /v1/settings` → **`403 Forbidden {"detail":"Access denied"}`**. `GET /v1/settings` con el mismo token → **`200`** (abierto a CUSTOMER a propósito, ver report 361 E-05 — el terminal de sala necesita leer la tasa real de impuesto). |
| **F-03** | `registerPhysicalPayment` no valida estado del split — doble cobro | ✅ **RESUELTO** [EN VIVO] | Split "Mesa" \$11.00 UNPAID → 1ª llamada `POST /billing/payments/physical` → **201**, `Payment` `CONFIRMED`. 2ª llamada idéntica → **409** `"Split for participant 'Mesa' is already PAID"`. `GET /billing/bills/6/payments` confirma **exactamente 1 fila**. |
| **F-04** | `initiateDigitalPayment` no bloquea ni evita intents concurrentes | ✅ **RESUELTO** [EN VIVO] | Split "Mesa" \$11.00 UNPAID → 1ª llamada `POST /billing/payments/digital` → **201** `PENDING`. 2ª llamada idéntica → **409** `"A digital payment is already pending for participant 'Mesa'"`. Confirmado 1 sola fila `PENDING`, luego confirmada sin problema (`POST /payments/{id}/confirm` → bill `PAID`). |
| **F-05** | NPE en `confirmDraftsForUser` cuando hay ítem "Mesa" sin `participantId` | ✅ **RESUELTO** [EN VIVO] | Mesa con 1 ítem "Mesa" (`participantId:null`) + 1 DRAFT de un comensal real → `POST /sessions/{s}/participants/{u}/confirm` → **200**, sin 500. (Antes: `NullPointerException` en `SessionService.java:559`.) |
| **F-06** | NPE en `removeItem` por evaluación temprana de `isOwner` | ✅ **RESUELTO** [EN VIVO] | El mismo ítem "Mesa" (`participantId:null`, `PENDING`) removido por el camarero asignado → **200**, ítem eliminado del array, `ITEM_DELETED` en el `activityLog`. Sin 500. |
| **F-07** | `closeEmptySession` ignora la identidad del llamante | ✅ **RESUELTO** [EN VIVO] | Mesa vacía abierta por `mesero@demo.com` → `mesero2@demo.com` (no asignado) llama `DELETE /sessions/{id}/cancel` → **403** `Access denied`; la sesión sigue existiendo. Control: el camarero correcto la cancela → **204**. |
| **F-08** | `GET /dashboard/status` sin rol — CUSTOMER ve toda la sala | ✅ **RESUELTO** [EN VIVO] | Token CUSTOMER con `rid` → **403**. Token KITCHEN → **403** también (endurecido más de lo mínimo pedido — bien). |
| **F-09** | `/actuator/health` expone datasource/disco a anónimos | ✅ **RESUELTO** [EN VIVO] | `GET /v1/actuator/health` sin token → **`{"status":"UP"}`** exactamente, sin ningún detalle de componentes. |
| **F-10** | Oráculo de enumeración en `/auth/login/pin` (401 vs 409 vs 423) | ⚪ **ABIERTO POR DISEÑO** [EN VIVO] — sin cambio, esperado | Email inexistente → **401** `Invalid credentials`. Email real sin PIN (`admin@demo.com`) → **409** `PIN_NOT_SET`. Sigue siendo distinguible — decisión de producto documentada en report 361 §6 (UX de `QuickLoginModal` depende de la distinción), no un descuido. |
| **F-11** | `/sessions/join` sin rate limit | ✅ **RESUELTO** [EN VIVO] | 25 llamadas rápidas con códigos inválidos → las primeras ~10 devuelven `404`, desde la 11ª en adelante **`429 Too Many Requests`** de forma consistente. |
| **F-12** | `/printing/agents/token` sin rate limit, O(N·bcrypt) por request anónimo | ✅ **RESUELTO** [EN VIVO] (rate limit) — arquitectura O(N) sin cambio, fuera de alcance | 20 llamadas con API key inválida → **`429`** en todas. La ruta ya no es explotable para agotamiento de CPU vía volumen sin control; el escaneo `findAll()+BCrypt` en sí no fue tocado (report 361 §8 lo marca fuera de alcance explícitamente). |
| **F-13** | Confusión de tipo de token QR vs usuario — "sin bypass demostrable" pero diseño fragile | 🔴 **STILL BROKEN — hallazgo nuevo/no resuelto** [EN VIVO] | Un **token QR** real (`GET /sessions/{id}/qr`, subject=`sessionId`) presentado como `Authorization: Bearer` en `GET /v1/sessions/{id}` **y** en `GET /v1/printing/agents/me/printers` → ambos devuelven **`500 Internal Server Error`** (Spring Boot genérico, sin cuerpo `problem+json`), no el `401` limpio que el blueprint pide como resultado esperado (S7-01/S7-02). Log del backend confirma la causa exacta: `org.springframework.security.core.userdetails.UsernameNotFoundException: User not found: 378e7212-...` (el subject del JWT QR es un `sessionId`, no un email, y `loadUserByUsername` no captura ese caso). **No es una brecha de acceso** — el token QR no otorga ningún permiso real — pero es una excepción no manejada, en producción sería un 500 genuino para cualquier cliente que reintente un token QR expirado/mal copiado en el campo equivocado. No estaba en el alcance de `FIX-QA` (report 361 no lo lista entre los 22 arreglados), así que no es una regresión de esa rama — pero tampoco está cerrado. **Recomendación:** capturar `UsernameNotFoundException` en el filtro JWT (o añadir el claim `typ` a los tokens QR, como F-13 ya sugería) y mapear a 401. |
| **F-14** | `PlatformOperator` sin campo de rol, sin `@PreAuthorize` en `/platform/**` | ⚪ **ABIERTO POR DISEÑO** — confirmado por código, sin cambio | `PlatformOperator.java` sigue sin campo `role`; `grep -rn PreAuthorize platform/` → 0 resultados. Documentado como decisión de producto pendiente en `AUDIT_BLUEPRINT.md` §9 orden 11, no tocado por `FIX-QA` (fuera de alcance). |
| **F-15** | `HubActivationService.activate` devuelve `adminPasswordHash` | ⚪ **ABIERTO POR DISEÑO** — confirmado por código, sin cambio | `HubActivationService.java:88` sigue llamando `.adminPasswordHash(admin.getPasswordHash())`. Documentado como intencional (el Hub siembra su BD local), fuera de alcance de `FIX-QA`. |
| **F-16** | `POST /sessions/{id}/waiter-items` sin verificar camarero asignado | ✅ **RESUELTO** [EN VIVO] | `mesero2@demo.com` (no asignado a la mesa de `mesero@demo.com`) llama `POST /sessions/{id}/waiter-items` → **403**. Control: el camarero correcto → **200**, ítem "Mesa" agregado. |
| **F-17** | JWT 24h en `localStorage` sin revocación; `ember-quick-access` persiste datos de personal | ⚪ **ABIERTO POR DISEÑO** — confirmado por código, sin cambio | `authStore.ts` sigue usando `zustand/persist` sobre `localStorage`; `quickAccessStore.ts` sigue guardando `{name,email,role}` por perfil. Ninguno tocado por `FIX-QA` (E-21 sólo dejó de *mostrar* el email en el chip antes de autenticar — el dato sigue en `localStorage`, ver E-21 abajo). |
| **F-18** | `SecurityAuditTest` con ~60 filas de prefijo `/api/**` obsoleto (aserciones vacías) | 🟡 **ABIERTO — sin cambio, ya rastreado** | `grep -n '"/api/'` sobre `SecurityAuditTest.java` sigue devolviendo filas con el prefijo obsoleto (ej. línea 25 `"GET,  /api/catalog/categories"`). Confirmado no tocado — coincide con report 361 §8 (explícitamente fuera de alcance de esa rama). No es una regresión; sigue siendo deuda de test conocida. |
| **F-19** | `/sessions/{id}/status` filtra detalle interno (`"No tenant bound..."`) en vez de 404 limpio | ✅ **RESUELTO** [EN VIVO] | `GET /v1/sessions/{uuid-inexistente}/status` sin token → **`401 Unauthorized`** (ruta removida de `permitAll`), nunca el `409` con el mensaje interno de antes. Coincide con el criterio de aceptación de S4-06 (401 **o** 200, nunca el 409 filtrando detalle). |
| **F-20** | `ddl-auto=update` por defecto (dev/hub compiten con Flyway) | 🟡 **ABIERTO — no aplicable a simulación en vivo, confirmado sin cambio** | `application.yml:40` sigue `ddl-auto: ${DDL_AUTO:update}`, con el propio comentario del archivo (línea 34) ya documentándolo como seguimiento conocido. Sin cambio, como se esperaba (no es parte de `FIX-QA`). |
| **F-21** | Perfil `hub` con credenciales en claro (`password: ember`, `secret-key: ember-hub-local`) | ⚪ **ABIERTO POR DISEÑO — no aplicable a simulación en vivo, confirmado sin cambio** | `application-hub.yml` sigue con ambos valores en claro. Mitigado por binding a `localhost`, sin cambio esperado. |
| **F-22** | `.env` estuvo versionado; secreto viejo permanece en historial git | ⚪ **NO APLICABLE A SIMULACIÓN EN VIVO** | Hallazgo histórico de git, no ejecutable; no re-verificado esta sesión (ya documentado como rotado, el valor viejo sigue en el historial por diseño de git). |
| **F-23** | `ProtectedRoute` renderiza `<Outlet/>` cuando `role` es `undefined` en vez de redirigir | 🟡 **ABIERTO — confirmado por código, sin cambio** (no probado en vivo — sin navegador esta sesión) | `ProtectedRoute.tsx:15` sigue con `if (role && !allowedRoles.includes(role))` — con `role` falsy, la condición nunca se cumple y cae a `<Outlet/>`. No tocado por `FIX-QA`. Recomendado como S8-01 en el blueprint, sigue pendiente. |
| **F-24** | API key del print-agent en claro en `agent.properties` en disco | ⚪ **ABIERTO POR DISEÑO — no aplicable a simulación en vivo** | Inherente al diseño (documentado en el propio blueprint); no se levantó un print-agent esta sesión para re-confirmar, sin cambio esperado. |

---

## 3. Re-verificación de los hallazgos exclusivos de `QA_SIMULATION_REPORT.md` (no numerados en el blueprint)

| ID | Hallazgo original | Estado ahora | Evidencia |
|---|---|---|---|
| **E-03** | `ItemsFloatingIsland` — selector `state.items \|\| []` causaba loop infinito de render, pantalla de menú en blanco | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO (sin navegador esta sesión) | `ItemsFloatingIsland.tsx:14,18` — constante módulo-level `EMPTY_ITEMS` + `state.items ?? EMPTY_ITEMS`, ya no fabrica un array nuevo por render. |
| **E-05** | Impuesto configurado nunca se aplicaba a la factura real; panel del camarero mostraba 10% fijo | ✅ **RESUELTO** [EN VIVO] — la verificación más contundente de esta sesión | Con `taxRate=0` (valor original del tenant): ítem de \$10 → bill total **\$10.00** exacto. Con `taxRate=10` (10%, semántica confirmada en el propio código — `BillingService.java:106-116`, es un valor 0-100 no una fracción): mismo ítem de \$10 → bill total **\$11.00** exacto. El impuesto configurado por el ADMIN ahora sí llega al total real, con la aritmética correcta. (No se verificó en vivo si `TableInformation.tsx` del camarero refleja el mismo número — requiere navegador.) |
| **E-11** | Pantalla en blanco tras login (ADMIN/WAITER) — sin ruta `index` | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO (sin navegador esta sesión) | `App.tsx` tiene ahora `<Route index element={<Navigate to="..." replace/>}/>` bajo los bloques `/admin`, `/waiter`, `/kitchen` y `/customer` (líneas 113, 126, 128, 142, 151 y más). |
| **E-12** | Icono "⋯" con semántica engañosa (en realidad sólo "desactivar") | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO | `StaffCard.tsx` — icono `MoreHorizontal` reemplazado por `UserX`, prop `onDeactivate` (antes `onOpenActions`), etiqueta honesta. |
| **E-13** | Badge de PIN desactualizado tras guardar | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO | `EditStaffModal.tsx` recibe `hasPin` y lo usa directamente en el render del badge (línea 116/119); la mutación invalida `['staff']` (líneas 78/88) antes de que el modal pueda reabrirse con datos viejos. |
| **E-17** | Dos overlays simultáneos (alerta de caja vencida + tour) | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO (no reproducido en vivo — el turno de esta sesión ya no está vencido tras prolongarlo para las pruebas F-03/F-04) | `uiStore.ts` tiene `cashShiftAlertOpen`; `WaiterTour.tsx:16,29` gatea `ready` en `!cashShiftAlertOpen`. |
| **E-18** | Diálogos Radix sin `Description`/`aria-describedby` (warning de accesibilidad) | 🟡 **PARCIALMENTE RESUELTO** — CONFIRMADO POR CÓDIGO | `CloseShiftDialog.tsx` (el caso explícitamente citado, "Shift reconciliation") ya tiene `<DialogDescription>` (línea 96) — **el que reporta 361 dice haber arreglado**. Pero **`QuickLoginModal.tsx` ("PIN") y `EditStaffModal.tsx` ("Edit employee") — los otros dos diálogos citados textualmente en el hallazgo original — siguen sin `DialogDescription` (0 ocurrencias en ambos)**. El hallazgo no está cerrado en general, sólo en el único diálogo que la rama tocó. |
| **E-19** | Lista de categorías sin `key` estable | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO | `Category.tsx:62` — `key={Category.id}` en el `<Link>` mapeado (antes el `key` faltaba o estaba en el nodo equivocado). |
| **E-20** | Input de horario con `value` y `defaultValue` simultáneos | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO | `BrandingSettings.tsx` — los campos de horario (líneas 166, 176) sólo usan `value={...}`, cero ocurrencias de `defaultValue=`. |
| **E-21** | Login de acceso rápido exponía email de personal sin autenticar | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO | `QuickLoginModal.tsx:99` — el chip muestra `{profile.role}`, no el email; comentario en el propio código (líneas 95-96) documenta la razón del cambio citando el hallazgo. **Nota:** el email sigue viviendo en `localStorage` vía `quickAccessStore` (ver F-17) — sólo se dejó de *mostrar* en pantalla antes de autenticar, el dato en sí no se eliminó del dispositivo. |

---

## 4. Backlog restante (post-`FIX-QA`, tras esta 2ª verificación)

Orden por urgencia técnica:

1. **F-13 (nuevo/no resuelto) — 500 sin manejar al presentar un token QR como token de usuario.**
   Fix acotado: capturar `UsernameNotFoundException` (o cualquier excepción del filtro JWT al
   resolver el subject) y mapear a 401 vía `GlobalExceptionHandler`; considerar además añadir el
   claim `typ=qr-session` a `QrTokenService` (la sugerencia original de F-13) para que la validación
   sea explícita y no dependa de que `loadUserByUsername` falle por accidente.
2. **E-18 (parcial) — extender `DialogDescription`/`aria-describedby` a `QuickLoginModal` y
   `EditStaffModal`**, los otros dos diálogos que el hallazgo original citó por nombre y que
   `FIX-QA` no tocó.
3. **F-23 — `ProtectedRoute` no redirige cuando `role` es `undefined`.** No confirmado en vivo esta
   sesión (sin navegador); el código no cambió, sigue siendo el mismo defecto de UX-only (el backend
   sigue aplicando `@PreAuthorize` real) documentado en el blueprint original.
4. **F-18 — reescribir `SecurityAuditTest`** con las rutas reales (`/v1/**`, no `/api/**`) y añadir
   la matriz rol×ruta que S2-11 pide. Deuda de test conocida, no de producto.
5. **E-23/F-10 — decisión pendiente del maintainer** sobre el oráculo de enumeración de PIN
   (colapsar a un 401 genérico rompe el mensaje de ayuda de `QuickLoginModal`; mantenerlo dejar el
   oráculo abierto). Sigue exactamente donde report 361 §6 lo dejó.
6. **F-14, F-15, F-17, F-20, F-21, F-24 — decisiones de producto/deuda de infraestructura ya
   documentadas**, sin cambio de estado, no requieren acción de esta ronda.
7. **Pendiente de esta sesión, no de código:** repetir la verificación de E-11, E-12, E-13, E-17,
   E-19, E-20, F-23 **con navegador real** (`claude-in-chrome` no estaba conectado) para confirmar
   que lo que el código dice también se ve/comporta así en la UI renderizada — la confirmación por
   código es sólida pero no reemplaza un clic real.

---

## Anexo — Evidencia técnica citada

Todas las respuestas HTTP y el frame STOMP citados en este informe se capturaron en vivo contra
`main` `672fed04` durante esta sesión (backend Spring Boot local puerto 8080, frontend Vite puerto
5173, ambos detenidos al finalizar). El frame STOMP de F-01 se generó con un script Node ad-hoc
(`@stomp/stompjs` de `frontend/node_modules`, WebSocket nativo de Node 24) conectado a
`ws://localhost:8080/v1/ws/websocket` con un JWT legítimo del tenant "Demo", suscrito primero al
canal de "Embers" (rechazado) y luego, como control, al propio canal de "Demo" (aceptado y con
frame real recibido). El stack trace de F-13 se tomó del log de arranque del backend de esta
sesión (`UsernameNotFoundException: User not found: <sessionId>`, dos ocurrencias, una por cada
ruta probada). Las líneas de código citadas para los hallazgos "CONFIRMADO POR CÓDIGO" se leyeron
directamente del árbol de trabajo en el momento de esta sesión, no de memoria de `reports/361-*.md`.
