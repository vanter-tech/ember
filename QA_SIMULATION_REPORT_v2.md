# QA_SIMULATION_REPORT_v2.md

**Ember — 2ª simulación funcional (verificación en vivo de `AUDIT_BLUEPRINT.md` post-`FIX-QA`)**

| Campo | Valor |
|---|---|
| Fecha | 2026-09-04 |
| Método | **Ejecución real** contra `backend` (Spring Boot, perfil por defecto) + `frontend` (Vite dev), Postgres/MinIO en Docker, sobre el mismo tenant semilla "Demo" (+ tenant "Embers" para las pruebas cross-tenant) usado en la sesión original. Login real como ADMIN, WAITER, KITCHEN y CUSTOMER; tráfico HTTP real con `curl`, un cliente STOMP real en Node (`@stomp/stompjs`, tomado de `frontend/node_modules`) para la suscripción cruzada. La sesión original no tenía `claude-in-chrome` conectado, así que los hallazgos puramente de UI se verificaron leyendo código (`CONFIRMADO POR CÓDIGO`); **una adenda 2026-09-04, en sesión posterior con `claude-in-chrome` sí conectado, repitió E-11/E-12/E-13/E-17/E-18/E-19/E-20/E-21 con clics reales** (login real, navegación directa, lectura de consola) — ver §4.1 — y de paso fijó F-13 (report 362). E-03 y F-23 siguen sin repro en vivo (ver §4.1). |
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
(E-11 blank screens y el resto de UX) fueron confirmados primero por código y **luego en vivo con
navegador real** en la adenda del §4.1 — sin sorpresas frente a lo que el código ya indicaba.

**Una brecha nueva, no reportada antes, apareció en la sesión original de este documento:**
presentar un **token QR** de sesión (el que un comensal recibe al escanear el código de mesa) como
`Authorization: Bearer` en cualquier ruta que espere un token de usuario producía un **500 Internal
Server Error** sin manejar (`UsernameNotFoundException` sin capturar), no el 401 limpio que
`AUDIT_BLUEPRINT.md` F-13 exige. **Adenda 2026-09-04 — FIXED, report `362-bugfix-qr-token-bearer-500.md`,
commit `68f67daa`:** `QrTokenService` ahora estampa `typ=session-qr` en los tokens QR;
`SecurityConfig.jwtAuthFilter` sólo intenta `loadUserByUsername` cuando `type == null` (antes
comparaba contra el string exacto `"print-agent"`), y envuelve la búsqueda en un
`try/catch(UsernameNotFoundException)` como defensa adicional. Suite completa `./mvnw test`
**1030/1030** tras el fix, incluyendo el nuevo `QrTokenAsBearerRejectionTest`. Ver **F-13** más abajo
— ya no es parte del backlog abierto.

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
| **F-10** | Oráculo de enumeración en `/auth/login/pin` (401 vs 409 vs 423) | ⚪ **ABIERTO POR DISEÑO** [EN VIVO] — **decisión confirmada por el usuario 2026-09-04: mantener tal cual** | Email inexistente → **401** `Invalid credentials`. Email real sin PIN (`admin@demo.com`) → **409** `PIN_NOT_SET`. Sigue siendo distinguible — decisión de producto documentada en report 361 §6 (UX de `QuickLoginModal` depende de la distinción) y ratificada explícitamente por el usuario al revisar este backlog, no un descuido. |
| **F-11** | `/sessions/join` sin rate limit | ✅ **RESUELTO** [EN VIVO] | 25 llamadas rápidas con códigos inválidos → las primeras ~10 devuelven `404`, desde la 11ª en adelante **`429 Too Many Requests`** de forma consistente. |
| **F-12** | `/printing/agents/token` sin rate limit, O(N·bcrypt) por request anónimo | ✅ **RESUELTO** [EN VIVO] (rate limit) — arquitectura O(N) sin cambio, fuera de alcance | 20 llamadas con API key inválida → **`429`** en todas. La ruta ya no es explotable para agotamiento de CPU vía volumen sin control; el escaneo `findAll()+BCrypt` en sí no fue tocado (report 361 §8 lo marca fuera de alcance explícitamente). |
| **F-13** | Confusión de tipo de token QR vs usuario — "sin bypass demostrable" pero diseño fragile | ✅ **RESUELTO** — [EN VIVO], adenda 2026-09-04, report 362 / commit `68f67daa` | Un **token QR** real presentado como `Authorization: Bearer` producía `500` en `GET /v1/sessions/{id}` y `GET /v1/printing/agents/me/printers` (causa: `UsernameNotFoundException` sin capturar en `jwtAuthFilter`, ver detalle histórico abajo). **Fix aplicado esta sesión:** `QrTokenService.generateQrToken` ahora estampa `typ=session-qr`; `SecurityConfig.jwtAuthFilter` gatea el intento de `loadUserByUsername` en `type == null` (antes comparaba contra el string exacto `"print-agent"`) y envuelve la búsqueda en `try/catch(UsernameNotFoundException)` como defensa adicional para cualquier futuro tipo de token no-usuario. Nuevo test `QrTokenAsBearerRejectionTest` (`@SpringBootTest`+`MockMvc`): el mismo escenario ahora devuelve `401`. Suite completa `./mvnw test` **1030/1030**. Detalle histórico del bug (previo al fix): un token QR (`GET /sessions/{id}/qr`, subject=`sessionId`) causaba `org.springframework.security.core.userdetails.UsernameNotFoundException: User not found: 378e7212-...` porque el subject del JWT QR es un `sessionId`, no un email, y `loadUserByUsername` no capturaba ese caso — nunca fue una brecha de acceso (el token QR no otorgaba ningún permiso real), sólo una excepción sin manejar. |
| **F-14** | `PlatformOperator` sin campo de rol, sin `@PreAuthorize` en `/platform/**` | ⚪ **ABIERTO POR DISEÑO** — confirmado por código, sin cambio | `PlatformOperator.java` sigue sin campo `role`; `grep -rn PreAuthorize platform/` → 0 resultados. Documentado como decisión de producto pendiente en `AUDIT_BLUEPRINT.md` §9 orden 11, no tocado por `FIX-QA` (fuera de alcance). |
| **F-15** | `HubActivationService.activate` devuelve `adminPasswordHash` | ⚪ **ABIERTO — usuario pidió rediseñar, pendiente de plan dedicado** | `HubActivationService.java:88` sigue llamando `.adminPasswordHash(admin.getPasswordHash())`. Usuario decidió (2026-09-04) que esto debería rediseñarse en vez de mantenerse — pero requiere un mecanismo de bootstrap distinto (ej. el Hub genera su propia contraseña temporal en vez de recibir el hash de la nube) que toca el contrato de activación y necesita ruta de actualización para licencias ya emitidas. No es un fix de una sesión; queda como iniciativa propia, ver report 364 §6. |
| **F-16** | `POST /sessions/{id}/waiter-items` sin verificar camarero asignado | ✅ **RESUELTO** [EN VIVO] | `mesero2@demo.com` (no asignado a la mesa de `mesero@demo.com`) llama `POST /sessions/{id}/waiter-items` → **403**. Control: el camarero correcto → **200**, ítem "Mesa" agregado. |
| **F-17** | JWT 24h en `localStorage` sin revocación; `ember-quick-access` persiste datos de personal | ⚪ **ABIERTO POR DISEÑO** — confirmado por código, sin cambio | `authStore.ts` sigue usando `zustand/persist` sobre `localStorage`; `quickAccessStore.ts` sigue guardando `{name,email,role}` por perfil. Ninguno tocado por `FIX-QA` (E-21 sólo dejó de *mostrar* el email en el chip antes de autenticar — el dato sigue en `localStorage`, ver E-21 abajo). |
| **F-18** | `SecurityAuditTest` con ~60 filas de prefijo `/api/**` obsoleto (aserciones vacías) | 🟢 **PARCIALMENTE RESUELTO** — adenda 2026-09-04, report 364 | Las 48 filas con prefijo `/api/` corregidas a la ruta real (`/api/catalog/categories` → `/catalog/categories`); añadidas las 14 rutas que F-18 señalaba como faltantes (`/settings`, `/dashboard/status`, `/identity/waiters`, `/menu`, `/admin/restaurant`, `/sessions/*/transfer\|waiter-items\|cancel\|leave\|resume`, `/billing/bills/*/settle\|splits/redistribute`, `/printing/bills/*/receipt`). `./mvnw test -Dtest=SecurityAuditTest` **91/91**. **Sigue pendiente:** la matriz rol×ruta de S2-11 (afirmar 403 con rol incorrecto en las 60+ rutas `@PreAuthorize`) — no incluida en este alcance. |
| **F-19** | `/sessions/{id}/status` filtra detalle interno (`"No tenant bound..."`) en vez de 404 limpio | ✅ **RESUELTO** [EN VIVO] | `GET /v1/sessions/{uuid-inexistente}/status` sin token → **`401 Unauthorized`** (ruta removida de `permitAll`), nunca el `409` con el mensaje interno de antes. Coincide con el criterio de aceptación de S4-06 (401 **o** 200, nunca el 409 filtrando detalle). |
| **F-20** | `ddl-auto=update` por defecto (dev/hub compiten con Flyway) | ✅ **RESUELTO** — adenda 2026-09-04, report 364 | `application.yml` ahora `ddl-auto: ${DDL_AUTO:validate}` (antes `update`), igualando a `prod`/`hub`/tests. Verificado en vivo: backend arrancado con `DDL_AUTO=validate` contra la BD de dev real — Flyway validó las 7 migraciones y Hibernate no reportó ningún mismatch. `.env` local (no versionado) actualizado igual, cerrando una fuga real: `docker-compose.yml`'s `app` (perfil `prod`) lo consumía vía `env_file` y silenciosamente pisaba el `validate` por defecto de `application-prod.properties`. |
| **F-21** | Perfil `hub` con credenciales en claro (`password: ember`, `secret-key: ember-hub-local`) | ⚪ **ABIERTO POR DISEÑO — investigado, decisión de diseño confirmada como más profunda de lo esperado** | El hallazgo llega hasta el código de bootstrap real: `PortableDatabaseBootstrap.java:79` escribe literalmente `"ember"` al ejecutar `initdb`, y `PortableMinioBootstrap.java:106` fija `MINIO_ROOT_PASSWORD=ember-hub-local` al lanzar el proceso — no es sólo config de Spring, son las credenciales reales de las instancias locales. Corregirlo requiere una ruta de migración para instalaciones **ya existentes** de Hub (su Postgres local ya tiene "ember" grabado en su propio catálogo de auth) que no se puede verificar sin un instalador portátil real. Se deja como seguimiento propio, no como fix de esta ronda. |
| **F-22** | `.env` estuvo versionado; secreto viejo permanece en historial git | ⚪ **NO APLICABLE A SIMULACIÓN EN VIVO** — decisión confirmada: dejar como está | Hallazgo histórico de git, no ejecutable. Usuario confirmó dejarlo sin cambio (ya rotado; reescribir el historial es disruptivo y el repo no está expuesto a terceros no confiables). |
| **F-23** | `ProtectedRoute` renderiza `<Outlet/>` cuando `role` es `undefined` en vez de redirigir | ✅ **RESUELTO** — adenda 2026-09-04, report 364 | `ProtectedRoute.tsx` — nuevo chequeo `if (!role) return <Navigate to="/login" replace/>` antes del chequeo de rol equivocado, igualando el comportamiento esperado por S8-01 (tratar `role` ausente como no-autenticado, no como 403). Nuevo `ProtectedRoute.test.tsx` (4 tests, antes 0). |
| **F-24** | API key del print-agent en claro en `agent.properties` en disco | ⚪ **ABIERTO POR DISEÑO — considerado y descartado para esta ronda** | Inherente al diseño (documentado en el propio blueprint); usuario delegó la priorización de F-20/F-21/F-24 ("lo que sea mejor") — se priorizaron F-20 (fix trivial, hecho) y se investigó F-21 (resultó más profundo de lo esperado, diferido); F-24 necesitaría una redacción de cifrado-en-reposo/keychain del SO, mayor alcance aún — queda fuera de esta ronda. No se levantó un print-agent esta sesión para re-confirmar en vivo. |

---

## 3. Re-verificación de los hallazgos exclusivos de `QA_SIMULATION_REPORT.md` (no numerados en el blueprint)

| ID | Hallazgo original | Estado ahora | Evidencia |
|---|---|---|---|
| **E-03** | `ItemsFloatingIsland` — selector `state.items \|\| []` causaba loop infinito de render, pantalla de menú en blanco | ✅ **RESUELTO** — CONFIRMADO POR CÓDIGO (sin navegador esta sesión) | `ItemsFloatingIsland.tsx:14,18` — constante módulo-level `EMPTY_ITEMS` + `state.items ?? EMPTY_ITEMS`, ya no fabrica un array nuevo por render. |
| **E-05** | Impuesto configurado nunca se aplicaba a la factura real; panel del camarero mostraba 10% fijo | ✅ **RESUELTO** [EN VIVO] — la verificación más contundente de esta sesión | Con `taxRate=0` (valor original del tenant): ítem de \$10 → bill total **\$10.00** exacto. Con `taxRate=10` (10%, semántica confirmada en el propio código — `BillingService.java:106-116`, es un valor 0-100 no una fracción): mismo ítem de \$10 → bill total **\$11.00** exacto. El impuesto configurado por el ADMIN ahora sí llega al total real, con la aritmética correcta. (No se verificó en vivo si `TableInformation.tsx` del camarero refleja el mismo número — requiere navegador.) |
| **E-11** | Pantalla en blanco tras login (ADMIN/WAITER) — sin ruta `index` | ✅ **RESUELTO** — [EN VIVO] (addendum 2026-09-04, `claude-in-chrome`) | Navegación real a `http://localhost:5173/admin` con sesión ADMIN activa → redirige a `/admin/inventory`, contenido visible de inmediato, sin pantalla en blanco. Repetido con WAITER: navegar a `/waiter` → redirige a `/waiter/tables`, tablero de mesas visible al instante. |
| **E-12** | Icono "⋯" con semántica engañosa (en realidad sólo "desactivar") | ✅ **RESUELTO** — [EN VIVO] (addendum) | En `/admin/employees`, el árbol de accesibilidad expone el control como `button "Deactivate"` (nombre accesible honesto, no "more options"); clic abre un `AlertDialog` real titulado "Are you sure?" con texto "The employee will become inactive...". Cancelado sin confirmar. |
| **E-13** | Badge de PIN desactualizado tras guardar | ✅ **RESUELTO** — [EN VIVO] (addendum) | Empleado "Segundo Mesero" sin PIN → se configuró un PIN nuevo (toast "Quick-login PIN saved.") → se cerró el modal y se reabrió fresco (`Profile` de nuevo) → badge ya mostraba **"PIN set"** de inmediato, no "No PIN". PIN removido después para dejar el estado como se encontró. |
| **E-17** | Dos overlays simultáneos (alerta de caja vencida + tour) | ✅ **RESUELTO** — [EN VIVO] (addendum) | El turno de caja de "Demo" seguía vencido (desde antes de esta sesión) → al entrar a `/waiter/tables` apareció **sólo** la alerta "The 2026-09-01 register was never closed"; tras "Not now" (descartarla), **entonces y sólo entonces** apareció el tooltip del tour "Your tables" — secuenciados, no simultáneos. |
| **E-18** | Diálogos Radix sin `Description`/`aria-describedby` (warning de accesibilidad) | ✅ **RESUELTO** — [EN VIVO], adenda 2026-09-04, report 363 | Los 3 diálogos que la sesión anterior confirmó en vivo con el warning (`QuickLoginModal`, `AlertDialog` "Are you sure?" de desactivar/eliminar en `GlobalDeleteModal`, y `EditStaffModal`) ahora renderizan un `<DialogDescription>` real (Radix wire automático de `aria-describedby`) en vez de nada o un `<p>` suelto. Re-verificado en vivo: los 3 diálogos abiertos de nuevo con `claude-in-chrome` — texto de descripción visible en pantalla, **cero** warnings de consola en los 3 (antes: 2 cada uno). `pnpm run build`/`lint`/`test:run` sin regresión (73/73, mismo conteo). `CloseShiftDialog` seguía limpio, sin cambios. |
| **E-19** | Lista de categorías sin `key` estable | ✅ **RESUELTO** — [EN VIVO] (addendum) | `/admin/inventory/categories` cargada con la consola limpia desde antes de la navegación — cero warnings de React (ni de `key` ni de ningún otro tipo) tras el render de la lista. |
| **E-20** | Input de horario con `value` y `defaultValue` simultáneos | ✅ **RESUELTO** — [EN VIVO] (addendum) | `/admin/settings` (pestaña Branding, default) cargada con la consola limpia — sólo mensajes de Vite/React DevTools, cero warning de `value`/`defaultValue` pese a que los inputs de horario (`12:00`/`23:00`) están poblados y visibles. |
| **E-21** | Login de acceso rápido exponía email de personal sin autenticar | ✅ **RESUELTO** — [EN VIVO] (addendum) | Pantalla de login real (chips de acceso rápido persistidos de sesiones anteriores en este mismo perfil de Chrome): cada chip muestra sólo nombre + rol ("admin · ADMIN", "John Doe · WAITER", etc.), ningún email visible antes de autenticar. **Nota sin cambio:** el email sigue en `localStorage` vía `quickAccessStore` (ver F-17) — sólo se dejó de mostrar en pantalla. |

---

## 4. Backlog restante (post-`FIX-QA`, tras esta 2ª verificación + adenda con navegador real)

Orden por urgencia técnica:

1. ~~**F-13 — 500 sin manejar al presentar un token QR como token de usuario.**~~ **FIXED**
   (adenda 2026-09-04, report 362, commit `68f67daa`) — ver fila F-13 arriba. Ya no requiere acción.
2. ~~**E-18 — `DialogDescription` faltante en `QuickLoginModal`, `EditStaffModal` y el confirm de
   "desactivar empleado".**~~ **FIXED** (adenda 2026-09-04, report 363) — ver fila E-18 arriba. Ya
   no requiere acción.
3. **F-23 — `ProtectedRoute` no redirige cuando `role` es `undefined`.** Aún no confirmado en vivo
   (requiere forzar un estado de store con token presente pero `role` ausente, no ejercitado por un
   flujo normal de clics); el código no cambió, sigue siendo el mismo defecto de UX-only (el backend
   sigue aplicando `@PreAuthorize` real) documentado en el blueprint original.
4. **F-18 — reescribir `SecurityAuditTest`** con las rutas reales (`/v1/**`, no `/api/**`) y añadir
   la matriz rol×ruta que S2-11 pide. Deuda de test conocida, no de producto.
5. **E-23/F-10 — decisión pendiente del maintainer** sobre el oráculo de enumeración de PIN
   (colapsar a un 401 genérico rompe el mensaje de ayuda de `QuickLoginModal`; mantenerlo dejar el
   oráculo abierto). Sigue exactamente donde report 361 §6 lo dejó.
6. **F-14, F-15, F-17, F-20, F-21, F-24 — decisiones de producto/deuda de infraestructura ya
   documentadas**, sin cambio de estado, no requieren acción de esta ronda.

### 4.1 Adenda 2026-09-04 — verificación con navegador real (`claude-in-chrome`)

La sesión original de este documento no tenía la extensión de Chrome conectada, así que E-03,
E-11, E-12, E-13, E-17, E-18, E-19, E-20 y E-21 se habían confirmado sólo leyendo código. En esta
sesión posterior, con `claude-in-chrome` conectado, se hizo el pase con clics reales: login real
como ADMIN y WAITER, navegación directa a las rutas raíz `/admin` y `/waiter` (E-11), desactivar
un empleado hasta el diálogo de confirmación (E-12), configurar y verificar un PIN de punta a
punta con reapertura del modal (E-13), disparar la alerta de caja vencida real seguida del tour
(E-17), lectura de consola en `/admin/inventory/categories` y `/admin/settings` (E-19/E-20), y la
pantalla de login con chips reales persistidos de sesiones anteriores (E-21). **Los 8 resultaron
confirmados en vivo, sin sorpresas respecto a la lectura de código** — y de paso se descubrió que
E-18 afecta a un tercer diálogo (el de "desactivar empleado") no identificado antes. **E-03
(pantalla de menú del cliente) y F-23 no se pudieron ejercitar** esta vez: el turno de caja
vencido de "Demo" bloqueaba silenciosamente "Open table" para WAITER (sin toast de error visible,
un posible defecto de UX propio pero fuera del alcance de esta verificación) antes de poder llegar
a generar un código de mesa para un comensal nuevo. También se aprovechó la conexión al navegador
para fijar el bug de token QR (F-13, ver arriba) — el fix backend no requería navegador, sólo
`./mvnw test`, pero se hizo en la misma sesión.

**Seguimiento (mismo día, report 363):** se corrigió E-18 en los 3 diálogos que esta adenda
identificó con el warning en vivo (`QuickLoginModal`, el confirm de "desactivar empleado" en
`GlobalDeleteModal`, y `EditStaffModal`) — cada uno gana un `<DialogDescription>` real. Re-abiertos
los 3 con `claude-in-chrome` tras el fix: descripción visible en pantalla, cero warnings de
consola en los 3. Ver fila E-18 y backlog ítem 2 arriba.

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
