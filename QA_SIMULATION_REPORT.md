# QA_SIMULATION_REPORT.md

**Ember — Simulación funcional de los 4 perfiles (Ejecución real, no sólo análisis estático)**

| Campo | Valor |
|---|---|
| Fecha | 2026-09-04 |
| Método | **Ejecución real** contra `backend` (Spring Boot, perfil por defecto) + `frontend` (Vite dev) levantados localmente sobre el tenant semilla "Demo", con Postgres/MinIO ya corriendo en Docker. Login real como ADMIN, WAITER, KITCHEN y CUSTOMER (cuentas creadas/reseteadas para esta sesión); tráfico HTTP/STOMP real capturado con `curl`, un cliente STOMP en Node, y navegación real en Chrome (clics, formularios, consola, red). |
| Rama | `feat/waiter-quick-login-table-actions` (HEAD `b8fd7b0`) |
| Insumo | `AUDIT_BLUEPRINT.md` (auditoría estática previa) — cada hallazgo `[REPRO]` de ese documento se intentó reproducir en vivo aquí; los resultados abajo son **observados**, no proyectados. |
| Cuentas usadas | `admin@demo.com` / `mesero@demo.com` / `cocinero@demo.com` (contraseñas reseteadas a `Testing123!` sólo en la BD de desarrollo local) · `mesero2@demo.com` (creado vía API para probar aislamiento entre camareros) · `qa.customer.audit@example.com` (registrado como CUSTOMER) · `fe3@ember.com` (WAITER del tenant "Embers", usado como víctima cross-tenant) |
| Estado del entorno tras la sesión | Ajustes del tenant "Demo" restaurados a sus valores originales (taxRate 0.0, 10 mesas). El turno de caja #1 quedó abierto con 2 pagos duplicados de prueba — **artefacto intencional de la Suite S3, no limpiar sin revisar antes** (ver Backlog #6). |

> **Nota de rigor.** Todo hallazgo marcado **[EN VIVO]** fue disparado contra el sistema real y su
> "Comportamiento Obtenido" es una respuesta HTTP, un stack trace de log, o una captura de pantalla
> real, no una inferencia. Los pocos marcados **[DERIVADO]** provienen de lectura de código
> confirmada en la auditoría estática previa pero no se volvieron a ejecutar en esta sesión por
> presupuesto de tiempo; se listan igual porque son relevantes para el backlog, y se señalan como
> tales explícitamente.

---

## 1. Veredicto de Estabilidad

# 🔴 REQUIERE PARCHES CRÍTICOS

**No apto para producción inmediata.** La simulación disparó, con tráfico real y evidencia
reproducible, dos fallas de dinero (cobro duplicado sin bloqueo + impuesto que nunca se aplica en
el servidor), una fuga de datos en vivo entre restaurantes distintos a través de WebSocket, una
ruta de configuración que cualquier comensal autenticado puede escribir, y un choque total de la
pantalla de pedido del cliente ante un flujo de navegación perfectamente normal (abrir un enlace
compartido o refrescar el navegador). Ninguno de estos cinco requiere privilegios elevados ni
condiciones de laboratorio: los cinco se alcanzan desde el rol más bajo del sistema (CUSTOMER) o
desde una acción de un solo clic de un camarero.

El resto de la plataforma —aislamiento multi-tenant a nivel REST, bloqueo pesimista en caja,
reparto de céntimos en splits, rate limiting de login— se comportó exactamente como está
documentado y no mostró grietas bajo la simulación. El problema no es "el sistema está roto", es
que **un subconjunto pequeño y muy concreto de rutas de dinero, configuración y tiempo real
carece de la misma disciplina que el resto del código.**

---

## 2. Matriz de Errores

Severidad: **Bloqueante** (dinero, datos cruzados, o caída total de una pantalla crítica) ·
**Alta** (rompe una operación diaria sin rodeo razonable) · **Media** (defecto real, hay rodeo) ·
**Baja** (cosmético / higiene).

| ID | Severidad | Rol | Módulo | Pasos para Reproducir | Comportamiento Obtenido vs. Esperado | Fix Sugerido |
|---|---|---|---|---|---|---|
| **E-01** | **Bloqueante** | CUSTOMER | Tiempo real (STOMP) | 1) Cliente A se autentica en el Tenant "Demo". 2) Cliente A abre `/v1/ws` con su JWT válido y hace `SUBSCRIBE /topic/waiter/<UUID-tenant-B>` (el UUID de otro restaurante, "Embers"). 3) El Tenant B abre una mesa normalmente. | **Obtenido:** el Cliente A recibe en directo el frame `{"tenantId":"dcfeebbb...","sessionId":"...","tableNumber":3}` del Tenant B — verificado con un cliente STOMP real (`@stomp/stompjs`), no simulado. Repetido también contra `/topic/session/{id}` de una sesión ajena: el Cliente A recibió el nombre y precio del ítem que un camarero de Tenant B acababa de agregar, **sin ser participante de esa sesión.** **Esperado:** 403 / sin entrega — un usuario sólo debería recibir frames de su propio tenant (y en `/topic/session/*`, sólo si es participante o personal de esa mesa). | `JwtChannelInterceptor` (`config/JwtChannelInterceptor.java:564`) sólo autentica el frame `CONNECT`; ningún interceptor valida el destino de `SUBSCRIBE`. Agregar esa validación (tenant del destino == tenant de la sesión STOMP; para `/topic/session/{id}`, exigir `SessionService.isParticipant` o rol de sala) antes de dejar pasar el `SUBSCRIBE`. |
| **E-02** | **Bloqueante** | CUSTOMER | Configuración (`/settings`) | 1) Un comensal se une a una mesa con el código de 5 dígitos (obtiene `rid` en su JWT). 2) `PUT /v1/settings` con `billing.taxRate=0.99` y `space.totalTables=3`. | **Obtenido:** `200 OK`. El IVA del restaurante pasó de 0% a 99% y las mesas 4–10 quedaron desactivadas **en producción**, verificado con `GET /settings` y `GET /dashboard/status` inmediatamente después — ejecutado y revertido en esta sesión. **Esperado:** 403 — sólo ADMIN debería poder escribir la configuración del tenant. | Añadir `@PreAuthorize("hasRole('ADMIN')")` a `PUT /settings` (y evaluar si `GET` debe abrirse a WAITER en vez de a cualquier autenticado) en `settings/controller/SettingsController.java`. |
| **E-03** | **Bloqueante** | CUSTOMER | Pantalla de pedido (`/customer/menu`) | 1) Un cliente inicia sesión como CUSTOMER en un navegador/dispositivo donde nunca ha visitado antes esa pantalla (o con `localStorage` limpio — típico tras compartir un enlace, un modo privado, o simplemente el primer uso del celular de un comensal). 2) Se navega directo a `/customer/menu` (un enlace compartido, un refresh, o el propio flujo de reanudar sesión en ciertos casos). | **Obtenido:** pantalla en blanco con "Error — Something went wrong" (capturado por el `ErrorBoundary`). Consola: `Error: Maximum update depth exceeded` originado en `<ItemsFloatingIsland>`, precedido de `getSnapshot should be cached to avoid an infinite loop` — bucle de render infinito confirmado con traza completa. **Esperado:** la pantalla de menú carga normalmente aunque el carrito esté vacío. | Causa raíz confirmada: `pages/customer/components/ItemsFloatingIsland.tsx:10` — `useSessionStore((state) => state.items \|\| [])` crea un array nuevo en cada render cuando `items` es `undefined`, rompiendo la comparación por referencia de Zustand/`useSyncExternalStore`. Sustituir por un selector que no fabrique un literal nuevo cada vez (constante módulo-level `const EMPTY: Item[] = []`, o filtrar `items` ya normalizado en el store al persistir). |
| **E-04** | **Bloqueante** | WAITER | Cobros (`/billing/payments/physical`) | 1) Cuenta de $10.00, dividida en un solo split "Mesa". 2) `POST /billing/payments/physical` con `{billId, "Mesa", 10.00}`. 3) **Repetir exactamente la misma llamada.** | **Obtenido:** ambas devuelven `201 Created` — dos filas `Payment` `CONFIRMED` de $10.00 sobre una cuenta de $10.00 (verificado en `GET /billing/bills/{id}/payments` y reflejado como **$20.00 de "Total Revenue"** en el propio dashboard de Analytics del ADMIN, visto en pantalla). **Esperado:** la segunda llamada debe rechazarse (409) porque el split ya está `PAID`. | `PaymentService.registerPhysicalPayment` (`billing/service/PaymentService.java:83-107`) nunca comprueba `split.getStatus()` antes de marcarlo `PAID` de nuevo. Rechazar si el split ya no está `UNPAID`/`PARTIALLY_PAID`, o exigir una clave de idempotencia por intento de cobro. |
| **E-05** | **Bloqueante** | ADMIN / WAITER | Facturación — cálculo de impuestos | 1) Como ADMIN, configurar `Tax rate (%) = 0` (o cualquier valor) en Settings → Billing. 2) Como WAITER, abrir el panel de una mesa con un ítem de $10.00 en `Table Information`. 3) Comparar el "Total" mostrado ahí contra el total real que devuelve `POST /billing/sessions/{id}/bill`. | **Obtenido:** el panel del camarero siempre muestra **"Taxes (10%): $1.00"** sin importar la tasa real configurada (se verificó con `taxRate=0` en el backend). El total que efectivamente factura el backend es **$10.00 exactos, sin impuesto alguno**, sin importar qué tasa haya configurado el ADMIN — confirmado leyendo `BillingService.calculateBill`, que nunca referencia `RestaurantSettings`/`taxRate`. **Esperado:** el impuesto configurado por el restaurante debe aplicarse de verdad al total facturado, y la vista previa del camarero debe reflejar la tasa real, no un valor fijo. | Dos arreglos independientes: (1) `pages/waiter/TableInformation.tsx:171` — quitar el `subtotal * 0.1` hardcodeado y leer `settings.billing.taxRate` real vía `useQuery`. (2) `billing/service/BillingService.calculateBill` (`billing/service/BillingService.java:44-67`) — sumar el impuesto configurado al total antes de persistir el `Bill`. Este es un defecto de cumplimiento fiscal, no sólo de UI: el campo "Tax rate (%)" que el ADMIN configura **no tiene ningún efecto en la factura real** hoy. |
| **E-06** | **Alta** | CUSTOMER | Confirmar pedido (`/sessions/{id}/participants/{userId}/confirm`) | 1) Un camarero agrega un ítem genérico a la mesa vía "Add Item" sin asociarlo a un comensal (queda con `participantName: "Mesa"`, `participantId: null` — el caso normal de una ronda para toda la mesa). 2) Cualquier comensal de esa mesa intenta confirmar su propio pedido en borrador. | **Obtenido:** `500 Internal Server Error`. Log del backend: `NullPointerException` en `SessionService.java:559` (`item.getParticipantId().equals(...)` sobre un `participantId` nulo). **A partir de ese momento ningún comensal de esa mesa puede volver a confirmar pedido — la cocina deja de recibir comandas de esa sesión.** **Esperado:** 200, sólo se confirman los ítems del comensal que llama. | Invertir el orden de los filtros en `confirmDraftsForUser` (`session/service/SessionService.java:558-561`): filtrar primero por `status == DRAFT`, luego comparar `userId.equals(item.getParticipantId())` (no al revés) para que el `null` de los ítems "Mesa" nunca llegue al `.equals()`. |
| **E-07** | **Alta** | WAITER | Quitar ítem (`DELETE /sessions/{id}/items/{itemId}`) | Con el mismo ítem "Mesa" del caso anterior (en estado `PENDING`, no bloqueado por el guard de estados ya enviados a cocina): el camarero asignado intenta quitarlo. | **Obtenido:** `500 Internal Server Error`. Log: `NullPointerException` en `SessionService.java:521` (`item.getParticipantId().equals(user.getId())` evaluado antes de comprobar `isWaiter`). **Esperado:** 200, el camarero puede quitar cualquier ítem no enviado aún a preparación. | `SessionService.removeItem:519-522` — mover el cálculo de `isOwner` dentro del `if (!isWaiter)` (cortocircuito), o usar `user.getId().equals(item.getParticipantId())` con `Objects.equals` para tolerar `null`. |
| **E-08** | **Alta** | WAITER | Cancelar mesa vacía (`DELETE /sessions/{id}/cancel`) | Camarero B (sin ninguna relación con la mesa) llama a cancelar una mesa vacía abierta por el Camarero A. | **Obtenido:** `204 No Content` — la mesa de A se cancela sin que B esté autorizado. **Esperado:** 403, sólo el camarero asignado (o un ADMIN) debería poder cancelarla. | `SessionController.closeEmptySession` (`session/controller/SessionController.java:201`) recibe `Authentication` pero nunca la usa; `SessionService.closeEmptySession` tampoco compara contra `session.getWaiterId()`. Añadir la misma comprobación que ya existe en `transferTable`/`expandCapacity`. |
| **E-09** | **Alta** | WAITER | Agregar ítem por camarero (`POST /sessions/{id}/waiter-items`) | Camarero B (no asignado a la mesa) agrega un ítem a la mesa del Camarero A. | **Obtenido:** `200 OK` — el ítem se agrega igual, aparece en la comanda de cocina y en la cuenta de una mesa ajena. **Esperado:** 403. | `SessionController.addWaiterItem` (`session/controller/SessionController.java:163`) ni siquiera recibe `Authentication`. Añadir verificación de camarero asignado, igual que en E-08. |
| **E-10** | **Alta** | CUSTOMER | Panorama de sala (`GET /dashboard/status`) | Un comensal con `rid` (ya sentado en una mesa) llama directamente a `GET /dashboard/status`. | **Obtenido:** `200 OK` con el estado completo de **todas** las mesas del restaurante (ocupación, camarero asignado, hora de apertura de cada una) — no sólo la suya. **Esperado:** 403. | Añadir `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")` en `session/controller/DashboardController.java:24`. |
| **E-11** | **Alta** | Todos (staff) | Navegación post-login | Iniciar sesión como ADMIN → aterriza en `/admin`. Iniciar sesión como WAITER → aterriza en `/waiter`. | **Obtenido:** en ambos casos, pantalla **completamente en blanco** (sólo la barra superior y el dock flotante visibles; sin spinner, sin mensaje). Hay que hacer clic manualmente en un ícono del dock para ver contenido. Confirmado en vivo para ambos roles; el mismo patrón de enrutado (`AdminLayout`/`WaiterLayout` sin `<Route index>`) también aplica a `/customer` cuando se llega ahí vía `RoleRedirect` (p. ej. abriendo `/` con sesión activa) — no confirmado en vivo porque el formulario de login de CUSTOMER redirige explícitamente a `/customer/home`, evitando el caso. **Esperado:** redirigir automáticamente a una vista por defecto (dashboard, o la primera sección del dock). | Agregar `<Route index element={<Navigate to="analytics" replace/>} />` (o la sección que se decida) dentro de los bloques `/admin` y `/waiter` en `App.tsx:119-131`, y una ruta `index` equivalente bajo `/customer`. |
| **E-12** | **Media** | ADMIN | Staff → menú "..." | Clic en el ícono "⋯" ("more options") de la tarjeta de un empleado. | **Obtenido:** se abre directamente el diálogo destructivo "Are you sure? The employee will become inactive..." — no hay un menú intermedio con otras opciones (editar, ver perfil, etc.). El ícono universalmente asociado a "más opciones" es en realidad el botón único de **desactivar**. No se pierde nada sin confirmar ("Yes, deactivate" es un segundo paso), pero la semántica del ícono es engañosa. **Esperado:** un menú desplegable con acciones, "Desactivar" como una de ellas. | Revisar `pages/admin/staff/components` — reemplazar el ícono de kebab por un botón "Desactivar" explícito, o convertirlo en un menú real. |
| **E-13** | **Media** | ADMIN | Staff → Edit employee → Quick-login PIN | 1) Abrir "Profile" de un empleado sin PIN. 2) Configurar un PIN y guardarlo (toast "Quick-login PIN saved." confirmado). 3) Cerrar el modal y volver a abrir "Profile" del mismo empleado. | **Obtenido:** el badge sigue mostrando **"No PIN"**, incluso después de reabrir el modal fresco. Verificado contra el backend (`GET /admin/staff` → `hasPin: true`) que el PIN sí se guardó correctamente — es puramente una vista desactualizada. **Esperado:** el badge debe reflejar "PIN set" inmediatamente. | `EditStaffModal.tsx:362` pasa `member.hasPin` desde el snapshot de la lista con la que se abrió el modal; la invalidación de `['staff']` en el `onSuccess` de la mutación (línea 202) dispara un refetch en segundo plano, pero el modal reabierto puede ganarle la carrera si no se espera a que resuelva. Esperar el refetch (`await queryClient.invalidateQueries(...)`) antes de permitir reabrir, o leer el `hasPin` más reciente por `userId` en cada apertura en vez de por prop estática. |
| **E-14** | **Media** | ∅ (no autenticado) | `/actuator/health` | `GET /v1/actuator/health` sin token, con el backend corriendo en el perfil por defecto (el mismo que usa Ember Hub on-prem). | **Obtenido:** `200 OK` con detalle completo — estado de PostgreSQL, espacio en disco (bytes libres/totales) y **la ruta absoluta del sistema de archivos del servidor**. El perfil `prod` sí lo mitiga (`show-details: never`, puerto separado), pero el perfil por defecto no. **Esperado:** sin detalle para un anónimo. | Fijar `management.endpoint.health.show-details: never` (o `when-authorized`) también en `application.yml`, no sólo en `application-prod.properties`. |
| **E-15** | **Media** | CUSTOMER | Unirse a mesa (`POST /sessions/join`) | 15 llamadas seguidas en menos de 10 segundos con códigos inválidos. | **Obtenido:** las 15 devuelven `404`, ninguna `429` — no hay límite de tasa. **Esperado:** bloqueo tras N intentos, dado que el código es de 5 caracteres y se busca sin filtro de tenant. | Añadir `/sessions/join` a `RateLimitProperties.paths` (`config/RateLimitProperties.java:63`). |
| **E-16** | **Media** | ∅ (no autenticado) | `GET /sessions/{id}/status` | Llamar a esta ruta `permitAll` sin token, con un `id` inexistente. | **Obtenido:** `409 Conflict` con `detail: "No tenant bound to the current context"` — un mensaje interno de implementación filtrado a un caller anónimo. **Esperado:** 404 limpio, sin exponer detalle interno. | Decidir si la ruta debe funcionar realmente sin tenant (buscar sin filtro) o sacarla del `permitAll` en `config/SecurityConfig.java:62`. |
| **E-17** | **Baja** | WAITER | Mesas → primera carga | Iniciar sesión como WAITER con un turno de caja vencido de días atrás y llegar a `/waiter/tables`. | **Obtenido:** el diálogo "The register was never closed" y el tooltip de tour "Your tables" aparecen **superpuestos al mismo tiempo**, compitiendo por la atención. | Secuenciar los dos overlays (el sentinel de caja primero, el tour sólo después de cerrarlo). |
| **E-18** | **Baja** | Todos | Diálogos (Radix) | Abrir casi cualquier modal de la aplicación (PIN, Shift reconciliation, Edit employee, etc.) con la consola abierta. | **Obtenido:** warning repetido en consola — `Missing \`Description\` or \`aria-describedby={undefined}\` for {DialogContent}` — en múltiples diálogos distintos. No es cosmético para el usuario final, pero sí un defecto de accesibilidad real (lectores de pantalla). | Agregar `<Dialog.Description>` (o `aria-describedby` explícito con `sr-only`) al componente base de diálogo compartido. |
| **E-19** | **Baja** | ADMIN | Catálogo → Categorías | Ver la lista de categorías con la consola abierta. | **Obtenido:** `Each child in a list should have a unique "key" prop. Check the render method of \`Category\`.` — riesgo real de reordenamiento incorrecto si la lista crece. | Añadir `key` estable (id) al `.map()` de categorías. |
| **E-20** | **Baja** | ADMIN | Settings → Branding → Business Hours | Abrir la pestaña de horarios. | **Obtenido:** `Input contains an input of type time with both value and defaultValue props` — input controlado/no controlado en conflicto. | Unificar a un solo modo (controlado) en el campo de horario. |
| **E-21** | **Baja** | Todos (staff) | Login → Quick start | Cualquier empleado que haya iniciado sesión una vez en el dispositivo. | **Obtenido:** la pantalla de login expone en texto plano, **sin autenticación previa**, el nombre y el correo real de cada empleado que usó ese dispositivo (`fer1@ember.com` visible como chip de acceso rápido). Es una decisión de diseño consciente (acceso rápido en terminal de sala) pero vale la pena que el equipo confirme que es aceptable para un dispositivo compartido de piso. | Si se decide mitigar: mostrar sólo el nombre/iniciales sin el correo en la tarjeta, revelando el correo sólo tras el PIN. |
| **E-22** [DERIVADO] | Media | ∅ (no autenticado) | `POST /printing/agents/token` | No re-ejecutado en esta sesión; confirmado por lectura de código en la auditoría estática: `PrintAgentService.authenticateByApiKey` (`printing/service/PrintAgentService.java:87`) escanea **todos** los agentes de impresión de la plataforma con `findAll()` + `BCrypt.matches` por cada uno, en una ruta anónima sin límite de tasa. | Costo de CPU O(N·bcrypt) por petición anónima, escala con el número total de agentes de impresión de todos los tenants, no sólo uno. | Añadir la ruta a `RateLimitProperties.paths` y evaluar un índice indexable (hash no salteado) para el lookup inicial. |
| **E-23** [DERIVADO] | Media | ∅ (no autenticado) | `POST /auth/login/pin` | No re-ejecutado en vivo esta sesión (si se repite, bloquearía la cuenta real usada en las pruebas); confirmado por lectura de código: `AuthService.loginWithPin` devuelve 401 (email inexistente), 409 `PinNotSetException` (email real sin PIN) o 423 `PinLockedException` (bloqueado) — un oráculo que permite enumerar qué correos existen y cuáles tienen PIN configurado. | Diferencia de código/cuerpo entre "no existe" y "existe sin PIN" filtra información de cuentas a un atacante anónimo. | Unificar la respuesta a 401 genérico en los tres casos, o al menos igualar el código HTTP. |

---

## 3. Problemas de UX y Validación

Hallazgos que no son "errores" en el sentido de romper una operación, pero degradan la experiencia
o generan desconfianza en el usuario final. Todos observados en vivo salvo donde se indica.

- **Pantallas en blanco sin feedback tras iniciar sesión (E-11).** Es el primer problema de UX que
  encuentra *cualquier* ADMIN o WAITER nuevo: tras el toast "Login successful!" no hay absolutamente
  nada en pantalla. Un usuario sin experiencia previa con el sistema razonablemente pensaría que
  la aplicación está rota.
- **Formato de impuestos inconsistente entre pantallas (E-05).** El panel del camarero muestra un
  10% fijo, el de Settings permite configurar cualquier valor, y la factura real no aplica ninguno.
  Tres números distintos para el mismo concepto en tres pantallas del mismo flujo.
- **Falta de feedback de error en el cierre de caja.** Al intentar cerrar un turno con mesas
  todavía abiertas, el backend responde correctamente con 409 y un mensaje claro
  ("Cannot close cash shift: 2 table(s) still have an open session"), pero el diálogo
  "Shift reconciliation" del camarero **se queda exactamente igual** — sin toast de error, sin
  texto en rojo, sin deshabilitar el botón. El camarero no tiene forma de saber, desde la interfaz,
  por qué su clic en "Confirm count" no tuvo efecto.
- **Botón "..." con semántica engañosa (E-12).** Ver matriz — un ícono de "más opciones" que en
  realidad es un único botón de "desactivar empleado" sin menú intermedio.
- **Badge de estado desactualizado tras guardar (E-13).** El PIN se guarda correctamente pero la
  UI sigue diciendo "No PIN" — genera desconfianza sobre si la acción realmente funcionó, y podría
  llevar a un ADMIN a intentar "arreglarlo" innecesariamente.
- **Tarjetas de mesa sin rol semántico accesible.** Las tarjetas M1, M2, M3... del piso de mesas
  no aparecen como elementos interactivos en el árbol de accesibilidad (no son `<button>` ni tienen
  `role="button"`), pese a ser clicables con el mouse. Un usuario de teclado o lector de pantalla
  no puede operarlas.
- **Exposición de identidad de personal en pantalla compartida (E-21).** Ver matriz — el nombre y
  correo de empleados reales queda visible en el login sin autenticar, en un dispositivo que por
  diseño es compartido en el piso del restaurante.
- **Dos overlays compitiendo (E-17).** El recordatorio de caja vencida y el tour de onboarding
  aparecen simultáneamente la primera vez que un camarero abre "Mesas" tras iniciar sesión.
- **Validación de PIN correcta y clara.** Punto a favor: al configurar un PIN con confirmación que
  no coincide, el mensaje "PINs do not match" aparece de inmediato y de forma legible — un ejemplo
  de cómo debería verse el resto de la validación en la app.
- **Warnings de React en consola (E-18, E-19, E-20).** No visibles para el usuario final en modo
  producción, pero indican deuda técnica real (accesibilidad de diálogos, `key` de listas, inputs
  controlados/no controlados) que vale la pena limpiar antes de que se acumule más.

---

## 4. Backlog de Correcciones Prioritarias

Orden de mayor a menor urgencia técnica (no de esfuerzo — algunos de los primeros son cambios de
una línea; el orden refleja impacto y facilidad de explotación).

1. **E-06 y E-07** — dos líneas cada uno, desbloquean un fallo que **hoy mismo impide a un
   comensal confirmar su pedido** en cuanto un camarero agrega una ronda genérica a la mesa. Es el
   fix de menor esfuerzo con mayor impacto funcional inmediato de toda la lista.
2. **E-02** — una anotación (`@PreAuthorize`) que cierra la puerta a que cualquier comensal
   reescriba la configuración fiscal y el número de mesas del restaurante en vivo.
3. **E-01** — requiere tocar `JwtChannelInterceptor` y agregar tests de suscripción cruzada, pero
   es la única vía por la que datos de un restaurante llegan hoy al navegador de otro.
4. **E-05** — el impuesto configurado por el ADMIN no tiene ningún efecto en la factura real. Es
   un defecto de cumplimiento, no cosmético; corregirlo implica decidir primero si el impuesto se
   calcula en `calculateBill` o en otro punto del flujo de facturación, y alinear la vista del
   camarero al mismo cálculo.
5. **E-04** — agregar el chequeo de estado de split antes de registrar un pago físico; considerar
   extender la misma guardia al pago digital (patrón idéntico, no reprobado en vivo esta sesión
   pero visible en el mismo archivo).
6. **E-08, E-09, E-10** — mismo patrón en los tres (falta `@PreAuthorize` o verificación de
   camarero asignado); pueden resolverse en una sola pasada por `SessionController`/`SessionService`.
7. **E-11** — agregar rutas `index` a los tres layouts de rol; mejora de UX de alto impacto y bajo
   riesgo.
8. **E-13** — esperar la invalidación de la query antes de considerar "guardado" el flujo de PIN.
9. **E-14, E-15, E-16** — endurecimiento de superficie no autenticada; ninguno es explotable para
   robar dinero directamente, pero los tres bajan el costo de reconocimiento de un atacante.
10. **E-12, E-17 a E-21** — mejoras de UX/accesibilidad/higiene; no bloquean, pero conviene
    agruparlas en un solo sprint de "pulido" antes de la siguiente ronda de QA.
11. **E-22, E-23** [derivados, no re-probados en vivo] — confirmar con una ejecución dedicada antes
    de priorizar, ya que dependen de volumen (agentes de impresión) o de un ataque de enumeración
    sostenido que no se reprodujo en esta sesión para no bloquear las cuentas de prueba en uso.

---

## Anexo — Evidencia técnica citada

Todas las líneas de código referidas en este informe fueron leídas directamente del árbol de
trabajo en el momento de la simulación (`b8fd7b0`, rama `feat/waiter-quick-login-table-actions`).
Los stack traces de E-06 y E-07 se capturaron íntegros del log de arranque del backend
(`backend.log`, líneas 60-75 y 242-257 de la sesión de prueba) y coinciden exactamente con los
números de línea citados. Los frames de E-01 se capturaron con un cliente STOMP real
(`@stomp/stompjs` sobre `ws://localhost:8080/v1/ws/websocket`) conectado con un JWT legítimo del
tenant "Demo", suscrito al canal de otro tenant real ("Embers") sembrado en la misma base de datos
de desarrollo.
