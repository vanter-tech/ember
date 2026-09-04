# AUDIT_BLUEPRINT.md

**Ember — Static Architecture & Security Audit / QA Execution Matrix**

| Campo | Valor |
|---|---|
| Fecha de auditoría | 2026-09-04 |
| Rama auditada | `feat/waiter-quick-login-table-actions` (HEAD `b8fd7b0`) |
| Alcance | `backend/`, `frontend/`, `printing-agent/`, `ember-hub/`, `landing/`, `deploy/` |
| Método | Análisis estático exhaustivo (lectura de fuentes). **No se ejecutó código ni pruebas.** |
| Superficie | 356 `.java` productivos / 135 de test · 186 `.ts|.tsx` · 33 controladores REST · 4 canales STOMP |
| Destinatario | Equipo de agentes QA — §8 es directamente ejecutable |

> **Nota de rigor.** Todo hallazgo en §7 cita `archivo:línea` verificable. Los hallazgos marcados
> `CONFIRMADO` se derivan de lectura directa del flujo de control. Los marcados `A VERIFICAR`
> requieren ejecución (§8) porque dependen de estado de base de datos o de constraints DDL que no
> se inspeccionaron en runtime. No se asume ninguno como explotado hasta que su caso de prueba
> pase a rojo.

---

## 1. Topología del sistema

Ember es un **monolito modular multi-tenant** con cinco desplegables distintos:

```
                       ┌─────────────────────────────────────────────┐
   Navegador tenant    │  frontend/  (React 19 + Vite + Zustand)      │
   (admin/waiter/      │  JWT en localStorage `ember-auth-storage`    │
    kitchen/customer)  └───────────────┬─────────────────────────────┘
                                       │ HTTPS  /v1/**      STOMP /v1/ws
                                       ▼
   ┌───────────────────────────────────────────────────────────────────────┐
   │  backend/  Spring Boot 3.5.14 · context-path `/v1/`                   │
   │                                                                       │
   │  ┌── Cadena @Order(1)  securityMatcher("/platform/**") ────────────┐  │
   │  │   PlatformSecurityConfig · clave `platform.jwt.secret`          │  │
   │  │   NO toca TenantContextHolder (aislamiento por clave distinta)  │  │
   │  └─────────────────────────────────────────────────────────────────┘  │
   │  ┌── Cadena @Order(2)  todo lo demás ──────────────────────────────┐  │
   │  │   SecurityConfig · clave `jwt.secret`                           │  │
   │  │   jwtAuthFilter → SecurityContext + TenantContextHolder(rid)    │  │
   │  │   → Restaurant.status == ACTIVE, si no 403 problem+json         │  │
   │  └─────────────────────────────────────────────────────────────────┘  │
   │                                                                       │
   │  Módulos: identity · catalog · session · kitchen · billing ·          │
   │           cashregister · inventory · loyalty · analytics · settings · │
   │           restaurant · printing · licensing · platform · hub          │
   │                                                                       │
   │  Eventos: 100% ApplicationEventPublisher + @EventListener (sin Kafka) │
   │  Persistencia: PostgreSQL · Hibernate DISCRIMINATOR multi-tenancy     │
   └───────┬──────────────────────────┬────────────────────────┬───────────┘
           │ JDBC                     │ S3 API                 │ STOMP /v1/ws/print-agent
           ▼                          ▼                        ▼
     PostgreSQL                 MinIO / GCS            printing-agent/ (JAR en PC del local)
                                                       credencial: agent.api-key (bearer)

   ember-hub/  = mismo JAR, perfil `hub`, context-path `/`, SPA embebida en /app, Postgres local
   landing/    = sitio Astro independiente (Cloudflare), sin acceso a datos de tenant
```

### 1.1 Frontera de confianza

| # | Frontera | Credencial | Verificador |
|---|---|---|---|
| TB-1 | Navegador → API tenant | JWT HS256 `jwt.secret`, claims `role`,`userId`,`rid` | `SecurityConfig.jwtAuthFilter` |
| TB-2 | Navegador → API plataforma | JWT HS256 `platform.jwt.secret` | `PlatformSecurityConfig.platformJwtAuthFilter` |
| TB-3 | Navegador → STOMP | mismo JWT de TB-1, sólo en el frame `CONNECT` | `JwtChannelInterceptor` |
| TB-4 | Print-agent → API | API key (bcrypt) → JWT `typ=print-agent`, TTL 20 min | `PrintAgentAuthController` / `PrintAgentService` |
| TB-5 | Hub on-prem → SaaS | licencia RSA firmada + huella de hardware | `HubActivationService` / `HubHeartbeatService` |
| TB-6 | Cliente QR → sesión | JWT firmado con `jwt.secret`, subject = `sessionId`, TTL 15 min | `QrTokenService` |

---

## 2. Modelo de identidad y taxonomía de tokens

Cinco tipos de token circulan; **tres comparten la misma clave de firma** (`jwt.secret`).

| Token | Emisor | Subject | Claims | TTL | Distinguible por |
|---|---|---|---|---|---|
| Usuario tenant | `AuthService.buildResponse` | email | `role`,`userId`,`rid?` | 24 h | — |
| QR de sesión | `QrTokenService:19` | **sessionId** | `rid` | 15 min | **nada** |
| Print-agent | `PrintAgentAuthController:34` | agentId | `rid`,`typ=print-agent` | 20 min | `typ` |
| Operador plataforma | `PlatformJwtService` | email | — | 24 h | clave distinta |
| Licencia Hub | `LicenseIssuingService` | — | RSA, no JWT | — | algoritmo |

### 2.1 Reglas de tenancy por rol

- `CUSTOMER` — **no tiene tenant** hasta unirse a una mesa (`AuthService.tenantIdOf:98`). El `rid`
  se adquiere en `POST /sessions/{id}/join` o `POST /sessions/join`, que re-emiten el token
  (`SessionController.withRescopedToken:118`).
- `ADMIN`/`WAITER`/`KITCHEN` — fijados a su restaurante en creación; `issueTenantScopedToken:90`
  ignora deliberadamente el restaurante de la sesión para el personal.
- `/auth/register` sólo crea `CUSTOMER` (`AuthService:39`). No hay escalada por registro.
- Cuenta desactivada: `EmberUserDetailsService` marca `disabled`, y `jwtAuthFilter:134` no
  autentica → 401 aunque el JWT siga vigente.

---

## 3. Modelo de multi-tenancy

Dos mecanismos coexisten. **Confundirlos es la principal fuente de riesgo del sistema.**

### 3.1 Automático — `@TenantId` (Hibernate DISCRIMINATOR)

`TenantIdentifierResolver` alimenta el filtro desde `TenantContextHolder` (ThreadLocal). Contexto
sin tenant → centinela `NO_TENANT = 00000000-0000-0000-0000-000000000000`, que **falla cerrado**
(partición vacía).

18 entidades filtradas automáticamente:
`Bill`, `BillSplit`, `Payment`, `Refund`, `CashMovement`, `CashShift`, `Category`, `MenuItem`,
`ModifierGroup`, `InventoryItem`, `KitchenOrder`, `LoyaltyAccount`, `LoyaltyReward`,
`LoyaltyTransaction`, `PrintJob`, `PrinterConfig`, `DiningTables`, `RestaurantSettings`.

### 3.2 Manual — filtrado explícito obligatorio

9 entidades **sin** `@TenantId`. Cada acceso debe filtrar a mano:

| Entidad | Razón documentada | Guardián |
|---|---|---|
| `Session` | el cliente aún no tiene tenant al canjear un código | `SessionService.findById:73` (`findByIdAndTenantId`) |
| `User` | login ocurre sin tenant enlazado | consultas `findByRestaurantId_Id*` |
| `PrintAgent` | la API key se presenta sin tenant | `PrintAgentService.getOwned:99` |
| `Restaurant` | es la tabla de tenants | `PlatformRestaurantService` |
| `PlatformOperator`, `PlatformAuditLog` | fuera del modelo tenant | cadena `@Order(1)` |
| `HubActivation` | activación pre-auth | firma de licencia |
| `MenuItemModifierGroup`, `ModifierOption` | filas hijas | alcanzadas vía padre `@TenantId` |

> **Regla invariante para QA:** cualquier ruta nueva que lea una de estas 9 entidades sin un
> predicado explícito de tenant es un IDOR cross-tenant por construcción.

### 3.3 Puntos que escriben `TenantContextHolder`

Sólo tres, y es correcto que sean sólo tres:
`SecurityConfig.jwtAuthFilter:143` · `JwtChannelInterceptor:590/607` ·
`SessionService.bindResolvedTenant:237` (valida `Restaurant.status == ACTIVE` antes de enlazar).

---

## 4. Matriz de autorización por endpoint

Leyenda: `A`=ADMIN `W`=WAITER `K`=KITCHEN `C`=CUSTOMER `*`=cualquier autenticado `∅`=permitAll
`OP`=operador de plataforma. **Celdas en negrita = desviación respecto al principio de mínimo privilegio.**

### 4.1 Cadena tenant (`@Order(2)`)

| Método | Ruta | Rol | Nota |
|---|---|---|---|
| POST | `/auth/register` | ∅ | rate-limited |
| POST | `/auth/login` | ∅ | rate-limited |
| POST | `/auth/login/pin` | ∅ | rate-limited + `PinAttemptGuard` |
| GET | `/public/ping`, `/public/restaurants/{slug}/branding` | ∅ | enumeración de slugs |
| GET | `/sessions/{id}/status` | ∅ | **ver F-19** |
| POST | `/printing/agents/token` | ∅ | **ver F-12** |
| GET | `/printing/agents/me/printers` | ∅ | JWT parseado a mano · **ver F-13** |
| POST | `/hub-activations`, `/hub-heartbeat` | ∅ | firma RSA · **ver F-15** |
| GET | `/actuator/**` | ∅ | **ver F-09** |
| GET | `/settings` | **\*** | **F-02** |
| PUT | `/settings` | **\*** | **F-02 — crítico** |
| GET | `/dashboard/status` | **\*** | **F-08** |
| GET | `/menu`, `/catalog/categories`, `/catalog/items`, `/catalog/modifier-groups` | * | lectura de carta, aceptable |
| POST/PUT/PATCH/DELETE | `/catalog/**` | A | correcto |
| ALL | `/catalog/inventory/**` | A | `@PreAuthorize` a nivel de clase |
| GET | `/admin/restaurant`, PATCH `/admin/restaurant/plan` | A | correcto |
| ALL | `/admin/users/**`, `/admin/staff/**` | A | incluye alta/baja de PIN |
| ALL | `/admin/analytics/**` | A | `@PreAuthorize` a nivel de clase |
| GET | `/identity/waiters` | W,A | expone email/nombre del personal del tenant |
| GET | `/kitchen/orders`, `/kitchen/display`, `/kitchen/orders/{sessionId}` | K,A | correcto |
| PATCH | `/kitchen/orders/{o}/items/{i}/status` | K | correcto |
| POST | `/sessions` | W | correcto |
| GET | `/sessions/{id}` | * | C validado como participante; **W/K/A de otro tenant cae en `findByIdAndTenantId` → 404. OK** |
| GET | `/sessions/{id}/qr` | W | + waiter asignado |
| POST | `/sessions/{id}/join`, `/sessions/join` | C | **sin rate-limit — F-11** |
| POST | `/sessions/{id}/leave`, `/resume` | C | verifica participación |
| PATCH | `/sessions/{id}/capacity` | W | + waiter asignado |
| POST | `/sessions/{id}/items` | C | verifica participación |
| POST | `/sessions/{id}/waiter-items` | W | **sin verificar waiter asignado — F-16** |
| POST | `/sessions/{id}/transfer` | W | + waiter asignado ✓ |
| POST | `/sessions/{s}/participants/{u}/confirm` | C | + identidad propia ✓ · **F-05** |
| DELETE | `/sessions/{id}/items/{itemId}` | C,W | dueño o waiter · **F-06** |
| DELETE | `/sessions/{id}/cancel` | W | **ignora `authentication` — F-07** |
| POST | `/billing/sessions/{s}/request`, `/bill` | W | correcto |
| GET | `/billing/sessions/{s}/bill` | W,C | correcto |
| POST | `/billing/bills/{id}/split`, `/splits/redistribute`, `/settle`, `/void` | W | correcto |
| POST | `/billing/payments/physical` | W | **sin idempotencia — F-03** |
| POST | `/billing/payments/digital` | W,C | **sin lock ni idempotencia — F-04** |
| POST | `/billing/payments/{id}/confirm` | W | correcto |
| POST | `/billing/payments/{id}/refund` | W | lock `findByIdForUpdate` ✓ |
| GET | `/billing/bills/{id}/payments`, `/payments/{id}/refunds` | W,A | correcto |
| ALL | `/cash-shifts/**` | W / W+A / A | bien acotado, con locks |
| ALL | `/loyalty/rewards/**` | A | correcto |
| GET | `/loyalty/accounts/me`, `/me/visits` | C | correcto |
| ALL | `/printing/admin/agents/**` | A | correcto |
| GET | `/printing/jobs` | A · POST `/retry` A,W | correcto |
| POST | `/printing/bills/{billId}/receipt` | W,A | correcto |

### 4.2 Cadena plataforma (`@Order(1)`)

| Método | Ruta | Rol | Nota |
|---|---|---|---|
| POST | `/platform/auth/login` | ∅ | rate-limited |
| PATCH | `/platform/auth/password` | OP | cambio propio |
| GET/POST | `/platform/restaurants` | **OP** | **F-14 — sin roles internos** |
| PATCH | `/platform/restaurants/{id}/status` | **OP** | suspende cualquier tenant |
| POST | `/platform/restaurants/{id}/hub-license` | **OP** | emite licencia |
| GET | `/platform/audit-log` | **OP** | |

---

## 5. Superficie de tiempo real (STOMP)

Broker simple. Prefijos: broker `/topic`,`/user`,`/topic/print-agent`; aplicación `/app`,`/app/print-agent`.
Todo se registra en `WebSocketConfig.configureMessageBroker:495` (único punto, deliberadamente).

| Destino | Publicado en | Contenido |
|---|---|---|
| `/topic/session/{sessionId}` | `SessionWebSocketListener`, `PaymentService:94/150/176/247/327`, `BillingService:95` | ítems, participantes, cuenta, splits, pagos, reembolsos |
| `/topic/kitchen/{tenantId}` | `KitchenWebSocketListener:20-35` | cola completa de cocina del tenant |
| `/topic/waiter/{tenantId}` | `WaiterWebSocketListener:26-46` | estado de todas las mesas, transferencias |
| `/topic/cash-register/{tenantId}` | `CashRegisterWebSocketListener:27-42` | apertura/cierre/movimientos de caja |
| `/topic/print-agent/**` | `PrintDispatchService:103` | trabajos de impresión (endpoint aislado) |

**`JwtChannelInterceptor:564` sólo procesa `StompCommand.CONNECT`.** Ningún interceptor evalúa
`SUBSCRIBE` en el canal tenant. → **F-01.**

---

## 6. Ficheros y flujos de alto riesgo (mapa para revisión dirigida)

| Prioridad | Fichero | Por qué |
|---|---|---|
| P0 | `config/JwtChannelInterceptor.java` | única puerta del canal STOMP tenant |
| P0 | `settings/controller/SettingsController.java` | escritura sin control de rol |
| P0 | `billing/service/PaymentService.java` | dinero, 420 líneas, idempotencia |
| P1 | `session/service/SessionService.java` | 691 líneas, corazón del dominio, `participantId` nulable |
| P1 | `config/SecurityConfig.java` | lista `permitAll` y enlace de tenant |
| P1 | `identity/service/AuthService.java` + `PinAttemptGuard.java` | login por PIN, baja entropía |
| P2 | `licensing/service/HubActivationService.java` | devuelve hash de contraseña |
| P2 | `printing/controller/PrintAgentSelfController.java` | parsea JWT sin comprobar `typ` |
| P2 | `platform/config/PlatformSecurityConfig.java` | super-admin sin granularidad |

---

## 7. Registro de hallazgos

### S1 — Crítico

---

#### F-01 · Los canales STOMP no autorizan el destino de `SUBSCRIBE` — fuga de datos en vivo entre tenants
**Estado:** CONFIRMADO por lectura de flujo · **Fichero:** `backend/src/main/java/com/vanter/ember/config/JwtChannelInterceptor.java:564`

`preSend` autentica únicamente el frame `CONNECT`. Para cualquier otro comando ejecuta
`bindTenantFromSession(accessor)` y devuelve el mensaje sin inspeccionar el destino. Los destinos
incorporan el identificador en la ruta (`/topic/kitchen/{tenantId}`, `/topic/waiter/{tenantId}`,
`/topic/cash-register/{tenantId}`, `/topic/session/{sessionId}`) y el broker simple entrega por
coincidencia literal de cadena, sin consultar el principal.

**Camino de explotación.** Un usuario autenticado cualquiera —incluido un `CUSTOMER` del tenant B
que sólo se sentó en una mesa— abre `/v1/ws`, envía `CONNECT` con su JWT legítimo y a continuación
`SUBSCRIBE destination:/topic/waiter/<UUID-del-tenant-A>`. Recibe en directo el estado completo de
mesas, cocina y caja del tenant A. El `restaurantId` del objetivo no es secreto: viaja en el propio
`AuthResponse` de cualquier empleado de A y aparece en `/platform/restaurants`.

**Impacto.** Ruptura del aislamiento multi-tenant en el plano de tiempo real; el plano REST está
correctamente aislado, lo que hace el fallo asimétrico y fácil de pasar por alto.

**Corrección propuesta.** Interceptar `StompCommand.SUBSCRIBE` en `JwtChannelInterceptor`: extraer
el segmento de tenant/sesión del destino y rechazar (`MessageDeliveryException`) si no coincide con
`sessionAttributes.tenantId`; para `/topic/session/{id}`, exigir además participación o rol de sala
vía `SessionService.isParticipant` (ya existe, `SessionService.java:594`).

---

#### F-02 · `PUT /v1/settings` carece de `@PreAuthorize` — cualquier autenticado reescribe la configuración del restaurante
**Estado:** CONFIRMADO · **Fichero:** `backend/src/main/java/com/vanter/ember/settings/controller/SettingsController.java:21,29`

Ni la clase ni los métodos declaran `@PreAuthorize`. La única barrera es `anyRequest().authenticated()`
más `TenantContextHolder.requireTenantId()`. Un `CUSTOMER` obtiene `rid` en cuanto se une a una mesa
(`SessionController.withRescopedToken:118`), por lo que satisface ambas condiciones.

**Camino de explotación.** Comensal → escanea QR o teclea el código de 5 caracteres → recibe token con
`rid` → `PUT /v1/settings` con un `SettingsPayload` arbitrario. Consecuencias directas:

1. `payload.billing.taxRate` y `currencySymbol` alteran el cálculo de toda cuenta posterior.
2. `payload.space.totalTables` desencadena `SettingService.syncDiningTables:52`. Con un valor bajo,
   `findByRestaurantIdAndIsActiveTrueOrderByTableNumberDesc` **desactiva mesas en producción**
   (`isActive=false`) durante el servicio.
3. `payload.businessHours` altera `CashShiftDeadlineService.computeExpiresAt`, forzando el estado
   *overdue* de la caja y bloqueando el registro de pagos físicos
   (`PaymentService.registerPhysicalPayment:72`).

`GET /settings` presenta el mismo defecto en lectura.

**Corrección propuesta.** `@PreAuthorize("hasRole('ADMIN')")` en `PUT`; `hasAnyRole('ADMIN','WAITER')`
en `GET` si el terminal de sala necesita leer moneda e impuestos (verificar consumidores en
`frontend/src/lib/api.ts` antes de restringir).

---

### S2 — Alto

---

#### F-03 · `registerPhysicalPayment` no comprueba el estado del split — doble cobro sin barrera
**Estado:** A VERIFICAR (depende de constraints DDL) · **Fichero:** `billing/service/PaymentService.java:83-107`

El método bloquea la cuenta (`findByIdForUpdate:77`) y valida que el importe **iguale** el del split
(`:87`), pero nunca verifica `split.getStatus() != BillSplitStatus.PAID` antes de marcarlo pagado
(`:92`) y persistir un nuevo `Payment` (`:98`). Dos invocaciones sucesivas para el mismo participante
producen dos filas `Payment` `CONFIRMED` por el importe completo, ambas imputadas al turno de caja
abierto vía `cashShiftId` (`:104`). El arqueo esperará el doble del efectivo realmente recibido.

El lock pesimista serializa concurrencia pero **no** aporta idempotencia frente a un reintento del
cliente, un doble clic o un reenvío tras timeout de red.

**Corrección propuesta.** Rechazar con 409 si el split ya está `PAID`; para pagos parciales, comparar
contra `netPaid` con la misma lógica que `updateSplitStatus:254`. Considerar una clave de idempotencia
por `(billId, participantName, clientRequestId)`.

---

#### F-04 · `initiateDigitalPayment` no bloquea la cuenta ni acota pagos pendientes
**Estado:** A VERIFICAR · **Fichero:** `billing/service/PaymentService.java:122-155`

A diferencia de sus hermanos, usa `billRepository.findById` (`:124`) en lugar de `findByIdForUpdate`,
y no comprueba si ya existe un `Payment` `DIGITAL`/`PENDING` para el mismo participante. Cada llamada
crea una fila nueva. `confirmDigitalPayment:158` puede confirmar cada una de ellas por separado: el
split se marca `PAID` repetidamente (idempotente en apariencia) mientras la tabla `payments` acumula
N filas `CONFIRMED` por el importe completo.

**Impacto.** Los ingresos declarados por `AnalyticsService`, `listPayments` y `CashShiftService.getDetail`
se inflan sin que la cuenta ni los splits muestren inconsistencia — un fallo de integridad contable
silencioso. `getBillState:382` mostrará además una lista de pendientes duplicada al camarero.

---

#### F-05 · NPE en `confirmDraftsForUser` cuando la mesa tiene un ítem añadido por camarero
**Estado:** CONFIRMADO · **Fichero:** `session/service/SessionService.java:559`

```java
.filter(item -> item.getParticipantId().equals(userId))   // línea 559
.filter(item -> item.getStatus() == OrderItemStatus.DRAFT) // línea 560
```

`addItemAsWaiter:347` fija `attributedId = null` siempre que `participantName` sea nulo o no case con
ningún participante (caso "Mesa", el habitual al añadir una ronda genérica). El primer `filter` se
evalúa sobre **todos** los ítems antes de que el segundo descarte los no-DRAFT, por lo que
`getParticipantId()` nulo produce `NullPointerException` → `GlobalExceptionHandler:131` → 500 genérico.

**Impacto funcional (alto).** A partir del momento en que un camarero añade un ítem "Mesa",
**ningún comensal de esa mesa puede volver a confirmar su pedido**. La cocina deja de recibir
comandas de esa sesión. Es un fallo de disponibilidad en el camino crítico del producto, no sólo un
error 500.

**Corrección propuesta.** Invertir el orden de los filtros y usar `userId.equals(item.getParticipantId())`.

---

#### F-06 · NPE en `removeItem` por evaluación temprana de `isOwner`
**Estado:** CONFIRMADO · **Fichero:** `session/service/SessionService.java:521`

```java
boolean isWaiter = session.getWaiterId().equals(user.getEmail());
boolean isOwner  = item.getParticipantId().equals(user.getId());  // línea 521 — NPE
if (!isWaiter && !isOwner) { ... }
```

`isOwner` se calcula de forma ansiosa, sin cortocircuito por `isWaiter`. Los ítems "Mesa" nacen en
estado `PENDING`, que **no** está en la lista de estados bloqueados (`:509-511`), así que el flujo
alcanza la línea 521. Un camarero intentando retirar un ítem que él mismo añadió recibe 500.

**Corrección propuesta.** `boolean isOwner = user.getId().equals(item.getParticipantId());` y mover el
cálculo dentro del `if`.

---

#### F-07 · `closeEmptySession` ignora la identidad del llamante
**Estado:** CONFIRMADO · **Ficheros:** `session/controller/SessionController.java:201` · `session/service/SessionService.java:487`

El controlador recibe `Authentication authentication` y **no lo usa**:
`sessionService.closeEmptySession(sessionId)`. El servicio tampoco compara contra
`session.getWaiterId()`. Cualquier `WAITER` del tenant puede cancelar la mesa de cualquier compañero.

Es una inconsistencia clara dentro del propio módulo: `expandCapacity:256` y `transferTable:397` sí
exigen ser el camarero asignado.

**Corrección propuesta.** Pasar `authentication.getName()` y aplicar la misma comprobación que
`transferTable:397`. Si el negocio requiere que cualquier camarero cubra una mesa ajena, documentarlo
y alinear `expandCapacity`/`transferTable` en la dirección opuesta — pero no dejar los tres criterios
divergentes.

---

### S3 — Medio

| ID | Hallazgo | Fichero | Estado |
|---|---|---|---|
| **F-08** | `GET /dashboard/status` sin `@PreAuthorize`: un `CUSTOMER` con `rid` obtiene el estado en vivo de todas las mesas del local (ocupación, camareros, importes). | `session/controller/DashboardController.java:24` | CONFIRMADO |
| **F-09** | `/actuator/**` es `permitAll` (`SecurityConfig.java:67`) y el perfil por defecto fija `health.show-details: always`. `/actuator/prometheus` publica métricas operativas y `/actuator/health` detalla estado de datasource y disco a cualquier anónimo. El perfil `prod` lo mitiga (puerto 8081, `show-details: never`), pero **el perfil por defecto es el que corre en `ember-hub` y en dev**. | `config/SecurityConfig.java:67`, `resources/application.yml` | CONFIRMADO |
| **F-10** | Oráculo de enumeración en login por PIN: email inexistente → 401 `BadCredentials`; email existente sin PIN → **409** `PinNotSetException`; email existente bloqueado → **423** `PinLockedException`. Además, 5 intentos fallidos bloquean el PIN de un empleado conocido durante 15 min (DoS dirigido al acceso rápido; el camino por contraseña sobrevive). El `PinAttemptGuard` es en memoria y por nodo (`PinAttemptGuard.java:24-26`), se reinicia con el proceso. | `identity/service/AuthService.java:63`, `config/GlobalExceptionHandler.java:91,99` | CONFIRMADO |
| **F-11** | `POST /sessions/join` no está en `RateLimitProperties.paths` (`RateLimitProperties.java:63`). El código es de 5 caracteres sobre un alfabeto de 32 (~33,5 M) pero se busca **globalmente entre sesiones OPEN de todos los tenants** (`SessionService.joinSessionCode:196`), por lo que el espacio efectivo es el número de mesas abiertas en la plataforma. Unirse concede lectura de participantes, comanda y cuenta. | `session/service/SessionService.java:196` | CONFIRMADO |
| **F-12** | `POST /printing/agents/token` es `permitAll`, no está rate-limited, y `authenticateByApiKey` (`PrintAgentService.java:87`) recorre **todos** los agentes con `findAll()` ejecutando un `BCrypt.matches` por cada uno. Coste CPU O(N·bcrypt) por petición anónima → agotamiento de CPU trivial. La justificación del escaneo (pocos agentes por tenant) no considera que `findAll()` es global a la plataforma. | `printing/service/PrintAgentService.java:87` | CONFIRMADO |
| **F-13** | Confusión de tipo de token: los QR (`QrTokenService.java:19`) se firman con la **misma** `jwt.secret` que los tokens de usuario y **no llevan claim `typ`**. `PrintAgentSelfController.myPrinters:29` parsea el bearer a mano y tampoco valida `typ`. La firma impide falsificación y `loadUserByUsername` falla para un subject que no es email, de modo que hoy no hay bypass demostrable — pero el diseño depende de un accidente, no de una comprobación. | `session/service/QrTokenService.java:19`, `printing/controller/PrintAgentSelfController.java:29` | CONFIRMADO (riesgo de diseño) |
| **F-14** | `PlatformOperator` no tiene campo de rol y ningún controlador de `/platform/**` declara `@PreAuthorize`. Todo operador es super-admin: puede suspender cualquier restaurante (`/platform/restaurants/{id}/status`) y emitir licencias Hub. Sin separación de funciones ni cuenta de sólo lectura para soporte. | `platform/model/PlatformOperator.java`, `platform/config/PlatformSecurityConfig.java:671` | CONFIRMADO |
| **F-15** | `POST /hub-activations` (permitAll) devuelve `adminPasswordHash` del ADMIN del tenant al portador de una licencia válida (`HubActivationService.activate:84`). Es intencional (el Hub siembra su BD local), pero convierte el fichero `license.key` en una credencial cuya fuga entrega un hash BCrypt para crackeo offline. El bloqueo por huella de hardware sólo actúa tras la primera activación. | `licensing/service/HubActivationService.java:84` | CONFIRMADO (riesgo de diseño) |
| **F-16** | `POST /sessions/{id}/waiter-items` no verifica que el llamante sea el camarero asignado — el controlador ni siquiera recibe `Authentication` (`SessionController.java:163`). Inconsistente con `transfer` y `capacity`. Ver F-07. | `session/controller/SessionController.java:163` | CONFIRMADO |
| **F-17** | JWT de 24 h en `localStorage` (`store/authStore.ts`, clave `ember-auth-storage`), sin refresh ni revocación del lado servidor: desactivar a un empleado sólo surte efecto porque `jwtAuthFilter:134` consulta `isEnabled()` en cada petición — pero un token robado sigue siendo válido hasta su expiración natural. `ember-quick-access` (`store/quickAccessStore.ts`) persiste hasta 6 pares email+nombre+rol del personal en el dispositivo compartido de sala, lo que suministra directamente el vector de F-10. | `frontend/src/store/authStore.ts`, `frontend/src/store/quickAccessStore.ts` | CONFIRMADO |

### S4 — Bajo / higiene

| ID | Hallazgo | Fichero |
|---|---|---|
| **F-18** | `SecurityAuditTest` sólo afirma **401 sin credenciales**; nunca verifica separación de roles (403 con rol equivocado). Peor: ~60 de sus ~90 filas usan un prefijo `/api/**` que ya no existe (el context-path es `/v1/`), de modo que aciertan por ser rutas no mapeadas bajo `anyRequest().authenticated()`. **Esas aserciones son vacías.** Faltan además `/settings`, `/dashboard/status`, `/identity/waiters`, `/menu`, `/admin/restaurant`, `/sessions/*/transfer|waiter-items|cancel|leave|resume`, `/billing/bills/*/settle|splits/redistribute`, `/printing/bills/*/receipt`. | `backend/src/test/.../config/SecurityAuditTest.java` |
| **F-19** | `/sessions/*/status` es `permitAll` (`SecurityConfig.java:62`) pero el flujo llama `TenantContextHolder.requireTenantId()` (`SessionService.findById:73`), que lanza `IllegalStateException` sin tenant → `GlobalExceptionHandler:65` → **409 con el detalle interno `"No tenant bound to the current context"`**. La ruta pública no funciona y filtra un mensaje interno. Decidir: hacerla realmente pública (buscar sin tenant) o quitarla del `permitAll`. | `config/SecurityConfig.java:62` |
| **F-20** | `ddl-auto` por defecto es `update` (`application.yml`); sólo `prod` fuerza `validate`. Hibernate y Flyway compiten por la propiedad del esquema en dev y en el perfil `hub` heredado. Ya está anotado como seguimiento en el propio `application.yml`. | `resources/application.yml` |
| **F-21** | El perfil `hub` fija credenciales en claro: `spring.datasource.password=ember`, `minio.secret-key=ember-hub-local`. Mitigado por enlace a `localhost` en la PC del local, pero cualquier proceso local las obtiene del JAR. | `resources/application-hub.yml` |
| **F-22** | `.env` estuvo versionado; el commit `d97b155` lo desindexó y rotó el `jwt.secret`. **El valor comprometido permanece en el historial de git.** Verificar que no queden entornos con la clave antigua y considerar reescritura de historial si el repositorio se abre a terceros. | historial git |
| **F-23** | `ProtectedRoute` cae en `<Outlet/>` cuando hay `token` pero `role` es `undefined` (`if (role && !allowedRoles.includes(role))`). Estado alcanzable si el store se rehidrata parcialmente. Guardia sólo de UX — el backend sigue aplicando `@PreAuthorize` — pero produce pantallas rotas en vez de un 403 limpio. | `frontend/src/components/ProtectedRoute.tsx` |
| **F-24** | `printing-agent/agent.properties` guarda la API key en claro junto al JAR en la PC del local (correctamente gitignored, `printing-agent/.gitignore:4`). Sin rotación automática ni detección de uso desde una IP distinta. Inherente al diseño; documentar el procedimiento de regeneración (`POST /printing/admin/agents/{id}/regenerate-key`). | `printing-agent/agent.properties` |

### 7.1 Áreas verificadas y **sin** hallazgos

Registrar lo que está bien es tan útil como registrar lo que no, para que QA no gaste ciclos:

- **Inyección SQL.** Ninguna consulta concatena entrada. Las 24 `@Query` usan JPQL con parámetros
  nombrados; el único `nativeQuery` (`RestaurantRepository:25`) también.
- **XSS.** Cero usos de `dangerouslySetInnerHTML` o `innerHTML` en código productivo (sólo en dos
  ficheros de test). React escapa por defecto.
- **Aislamiento REST cross-tenant.** `@TenantId` sobre 18 entidades más filtrado explícito en las 9
  restantes; respaldado por 12 tests `*TenantIsolationTest`.
- **Concurrencia en caja.** `CashShiftService` usa `findByIdForUpdate` en `recordMovement`,
  `prolongShift` y `closeShift`, más `DataIntegrityViolationException` como red de seguridad en
  `openShift:84`.
- **Reparto de importes.** `redistributeSplit:315` reparte con `RoundingMode.FLOOR` y asigna el
  resto al último destinatario — sin creación ni pérdida de céntimos.
- **Aislamiento de la cadena plataforma.** Claves de firma disjuntas; `EmberUserDetailsService` es
  `@Primary` y `PlatformSecurityConfig` inyecta por tipo concreto para evitar la resolución errónea.
- **Rate limiter.** `AuthRateLimiterFilter` maneja correctamente `X-Forwarded-For` (recorrido de
  derecha a izquierda, sólo tras proxy de confianza), rechaza literales no-IP para evitar DNS sobre
  entrada del atacante, y acota el mapa con `maxTrackedKeys`.
- **Cabeceras y CORS.** `allowCredentials=true` con patrones de origen (nunca `*`), compartido entre
  REST y el handshake SockJS.

---

## 8. Matriz de pruebas QA — ejecutable

### 8.0 Protocolo de ejecución

**Antes de tocar nada:**

```bash
cd backend  && ./mvnw test          # línea base verde obligatoria
cd frontend && pnpm run build && pnpm run lint && pnpm run test:run
```

Reglas para el equipo de agentes:

1. **Un agente por suite.** Las suites S1..S8 son independientes: no comparten ficheros de test ni
   estado. Ejecutables en paralelo.
2. **Rojo primero.** Cada caso `[REPRO]` debe **fallar** contra `HEAD` antes de que se escriba
   ninguna corrección. Un `[REPRO]` que pasa a la primera invalida el hallazgo → reportarlo como
   *no reproducible* en lugar de ajustar el test hasta que falle.
3. **Sin correcciones dentro de la suite de pruebas.** Este blueprint produce evidencia. Las
   correcciones son tareas separadas del backlog con su propio ciclo de `PROGRESS.md`.
4. **Convenciones del repo.** Tests backend en `backend/src/test/java/com/vanter/ember/<módulo>/`;
   frontend con Vitest junto al componente. Comandos canónicos: CLAUDE.md §2.
5. **Salida por caso:** `PASS` / `FAIL(esperado)` / `FAIL(inesperado)` / `BLOCKED`, con la aserción
   exacta y el fichero:línea creado.

---

### SUITE S1 — Autorización de destinos STOMP  *(F-01 · Crítico)*

Fichero destino: `backend/src/test/java/com/vanter/ember/config/WebSocketSubscribeAuthorizationTest.java`
Base a copiar: `WebSocketEndpointIsolationTest.java` (ya monta cliente STOMP real sobre `RANDOM_PORT`).

| ID | Preparación | Acción | Resultado esperado |
|---|---|---|---|
| S1-01 `[REPRO]` | Tenants A y B activos; usuario `waiter@a` y `customer@b` | `customer@b` hace CONNECT con su JWT, luego `SUBSCRIBE /topic/waiter/{tenantA}`; publicar un `TableTransferred` de A | **No se recibe frame** en 3 s. *Actual esperado: el frame llega → FAIL* |
| S1-02 `[REPRO]` | ídem | `SUBSCRIBE /topic/kitchen/{tenantA}`; disparar `KitchenItemsConfirmed` en A | sin entrega |
| S1-03 `[REPRO]` | ídem | `SUBSCRIBE /topic/cash-register/{tenantA}`; abrir turno en A | sin entrega |
| S1-04 `[REPRO]` | sesión `sess-A` en tenant A con un comensal | `customer@b` hace `SUBSCRIBE /topic/session/sess-A`; añadir ítem en `sess-A` | sin entrega |
| S1-05 | `customer@a` participante de `sess-A` | `SUBSCRIBE /topic/session/sess-A` | **sí** recibe — no romper el caso legítimo |
| S1-06 | `waiter@a` | `SUBSCRIBE /topic/waiter/{tenantA}` | **sí** recibe |
| S1-07 | agente de impresión de A | CONNECT en `/ws/print-agent` + `SUBSCRIBE /topic/print-agent/{agentId}` | sigue funcionando (no regresión de `PrintAgentChannelInterceptor`) |
| S1-08 | sin cabecera `Authorization` | CONNECT en `/ws` | `MessageDeliveryException`, conexión rechazada |

---

### SUITE S2 — Autorización REST faltante  *(F-02, F-08, F-16, F-07, F-18)*

Ficheros destino: `settings/controller/SettingsControllerAuthTest.java` (nuevo),
`session/controller/DashboardControllerAuthTest.java` (nuevo),
más reescritura de `config/SecurityAuditTest.java`.

| ID | Actor | Petición | Esperado |
|---|---|---|---|
| S2-01 `[REPRO]` | `CUSTOMER` con `rid` (post-join) | `PUT /v1/settings` payload válido | **403**. *Actual esperado: 200 → FAIL* |
| S2-02 `[REPRO]` | mismo | `PUT /v1/settings` con `space.totalTables = 1` | 403 **y** `DiningTables` activas del tenant sin cambios |
| S2-03 `[REPRO]` | `WAITER` | `PUT /v1/settings` | 403 |
| S2-04 | `ADMIN` | `PUT /v1/settings` | 200 (camino legítimo intacto) |
| S2-05 `[REPRO]` | `CUSTOMER` con `rid` | `GET /v1/settings` | 403 (o decisión documentada de permitir) |
| S2-06 `[REPRO]` | `CUSTOMER` con `rid` | `GET /v1/dashboard/status` | **403**. *Actual esperado: 200 con toda la sala* |
| S2-07 `[REPRO]` | `KITCHEN` | `GET /v1/dashboard/status` | 403 |
| S2-08 `[REPRO]` | `waiter2@a`, mesa asignada a `waiter1@a` | `POST /v1/sessions/{id}/waiter-items` | 403. *Actual esperado: 200* |
| S2-09 `[REPRO]` | `waiter2@a`, mesa vacía de `waiter1@a` | `DELETE /v1/sessions/{id}/cancel` | 403. *Actual esperado: 204* |
| S2-10 | — | Corregir `SecurityAuditTest`: sustituir todo prefijo `/api/` por la ruta real y añadir las 14 rutas ausentes listadas en F-18 | todas devuelven 401 sin token |
| S2-11 | — | **Añadir a `SecurityAuditTest` una segunda matriz rol×ruta** que afirme **403** con rol autenticado incorrecto para las 60+ rutas con `@PreAuthorize` de §4.1 | ninguna devuelve 2xx |

---

### SUITE S3 — Integridad de pagos  *(F-03, F-04)*

Fichero destino: ampliar `billing/service/PaymentServiceTest.java` y
`billing/controller/BillingControllerTest.java`.

| ID | Preparación | Acción | Esperado |
|---|---|---|---|
| S3-01 `[REPRO]` | cuenta con split de 100 para "Ana"; turno de caja abierto | `registerPhysicalPayment` dos veces con importe 100 | 2.ª llamada → **409**; exactamente **1** fila `Payment`. *Actual esperado: 2 filas → FAIL* |
| S3-02 `[REPRO]` | ídem | tras el doble cobro, `GET /cash-shifts/{id}` | el efectivo esperado refleja 100, no 200 |
| S3-03 `[REPRO]` | split de 100 para "Ana" | `initiateDigitalPayment` ×3 | sólo 1 `Payment` `PENDING`, o 409 en las siguientes |
| S3-04 `[REPRO]` | 3 pagos digitales pendientes del mismo split | confirmar los 3 | sólo 1 queda `CONFIRMED`; `listPayments` suma 100 |
| S3-05 | pago confirmado de 100 | reembolsar 60, luego 60 | 2.º reembolso → 409 ("excede saldo restante") — **debe seguir pasando** |
| S3-06 | 2 participantes, cuenta 100,01 | `redistributeSplit` de un split impago | la suma de splits resultantes = total original, al céntimo |
| S3-07 | cuenta con un split impago | `settleAndClose` | 409 — regresión de guardia existente |
| S3-08 | turno de caja vencido | `registerPhysicalPayment` | 409 `CashShiftOverdueException` — regresión |
| S3-09 | concurrencia: 2 hilos, mismo `billId`+participante | ambos llaman `registerPhysicalPayment` | exactamente uno tiene éxito |

---

### SUITE S4 — Robustez del dominio de sesión  *(F-05, F-06, F-19)*

Fichero destino: ampliar `session/service/SessionServiceTest.java`.

| ID | Preparación | Acción | Esperado |
|---|---|---|---|
| S4-01 `[REPRO]` | sesión con 1 comensal (1 ítem DRAFT) **y** 1 ítem añadido por camarero sin `participantName` | comensal llama `confirmDraftsForUser` | 200, su DRAFT pasa a PENDING. *Actual esperado: NPE → 500 → FAIL* |
| S4-02 `[REPRO]` | ídem | verificar que se publica `KitchenItemsConfirmed` | la cocina recibe la comanda |
| S4-03 `[REPRO]` | ítem "Mesa" en estado PENDING | el camarero asignado hace `removeItem` | 200 y el ítem desaparece. *Actual esperado: NPE → 500* |
| S4-04 | ítem "Mesa" PENDING | un `CUSTOMER` no dueño hace `removeItem` | 409/403, nunca 500 |
| S4-05 | ítem en `PREPARING` | `removeItem` | 409 — regresión |
| S4-06 `[REPRO]` | sin cabecera `Authorization` | `GET /v1/sessions/{id}/status` | 401 **o** 200 con estado, nunca **409 con `"No tenant bound to the current context"`** |
| S4-07 | `addItemAsWaiter` con `participantName` que sí casa | — | `participantId` no nulo; S4-01/03 pasan |
| S4-08 | sesión con 8 h + 1 min de antigüedad | `addItem` | 409 "has expired" — regresión |
| S4-09 | comensal ya sentado en otra mesa OPEN del mismo tenant | `joinSessionCode` | 409 — regresión de `rejectIfSeatedElsewhere` |

---

### SUITE S5 — Login por PIN y acceso rápido  *(F-10, F-17)*

Ficheros destino: ampliar `identity/service/AuthServiceTest.java`,
`identity/service/PinAttemptGuardTest.java`, `frontend/src/pages/auth/Login.quickaccess.test.tsx`.

| ID | Acción | Esperado |
|---|---|---|
| S5-01 `[REPRO]` | `POST /auth/login/pin` con email inexistente vs. email real sin PIN | **mismo código y cuerpo** en ambos casos. *Actual: 401 vs 409 → oráculo* |
| S5-02 `[REPRO]` | 5 PIN erróneos, luego uno más | 401 genérico, no **423** distinguible desde fuera |
| S5-03 | 5 fallos, esperar > 15 min (reloj inyectado), 1 intento correcto | éxito — la ventana expira |
| S5-04 | 5 fallos para `a@x`, después PIN correcto de `b@x` | `b@x` entra — el bloqueo no debe ser global |
| S5-05 | PIN correcto de un usuario `active=false` | 401, y `recordFailure` invocado |
| S5-06 | `POST /auth/login/pin` × 11 en 60 s desde una IP | la 11.ª → **429** (verifica que `/auth/login/pin` está en `RateLimitProperties.paths`) |
| S5-07 | PIN de 3 y de 7 dígitos | 400 de validación |
| S5-08 (FE) | tras `logout()`, inspeccionar `localStorage` | `ember-auth-storage` limpio; documentar explícitamente si `ember-quick-access` sobrevive a propósito |
| S5-09 (FE) | pulsar "olvidar" en un chip | el perfil desaparece de `ember-quick-access` |

---

### SUITE S6 — Superficie no autenticada y límites de tasa  *(F-09, F-11, F-12)*

Fichero destino: `backend/src/test/java/com/vanter/ember/config/UnauthenticatedSurfaceTest.java` (nuevo).

| ID | Acción | Esperado |
|---|---|---|
| S6-01 `[REPRO]` | `GET /v1/actuator/health` sin token, perfil por defecto | sin detalles de datasource/disco. *Actual: `show-details: always`* |
| S6-02 `[REPRO]` | `GET /v1/actuator/prometheus` sin token | 401 fuera del perfil `prod` |
| S6-03 | `GET /v1/actuator/env`, `/heapdump`, `/loggers` | 404 (no expuestos) — confirmar que `exposure.include` los excluye |
| S6-04 | `ProdManagementPortConfigTest` | sigue verde: `prod` mueve actuator a 8081 |
| S6-05 `[REPRO]` | `POST /v1/sessions/join` × 30 en 60 s con códigos aleatorios | throttling a partir del umbral. *Actual: sin límite* |
| S6-06 | dos tenants con el mismo `joinCode` abierto | `joinSessionCode` | 409 explícito, sin filtrar a qué restaurantes pertenece |
| S6-07 `[REPRO]` | `POST /v1/printing/agents/token` × 50 con clave inválida | throttling; medir tiempo con 100 agentes sembrados y documentar el coste O(N·bcrypt) |
| S6-08 | `POST /v1/hub-activations` × 20 | 429 (ya está en `paths` — regresión) |
| S6-09 | `GET /v1/public/restaurants/{slug}/branding` con slug inexistente | 404 sin filtrar existencia diferencial de otros slugs |

---

### SUITE S7 — Tipos de token y frontera del print-agent  *(F-13, F-15, F-14)*

Ficheros destino: ampliar `session/service/QrTokenServiceTest.java`,
`printing/controller/PrintAgentAuthControllerTest.java`; nuevo
`platform/controller/PlatformAuthorizationTest.java`.

| ID | Acción | Esperado |
|---|---|---|
| S7-01 `[REPRO]` | Presentar un **token QR** como `Authorization: Bearer` en `GET /v1/sessions/{id}` | 401 limpio, nunca 500 ni acceso |
| S7-02 `[REPRO]` | Presentar un token QR en `GET /v1/printing/agents/me/printers` | 401 — exigir `typ=print-agent` |
| S7-03 `[REPRO]` | Presentar un **token de usuario** en `/printing/agents/me/printers` | 401, no `IllegalArgumentException` de `UUID.fromString` |
| S7-04 | Token de print-agent en `GET /v1/kitchen/orders` | 401 (sin principal Spring Security) — regresión |
| S7-05 | Token de print-agent caducado (>20 min) en `/me/printers` | 401 |
| S7-06 | Token de plataforma contra una ruta tenant y viceversa | 401 en ambos sentidos — regresión de `PlatformAuthIsolationTest` |
| S7-07 | `POST /v1/hub-activations` con licencia válida | verificar que la respuesta contiene `adminPasswordHash` y **documentarlo** como decisión consciente; añadir aserción que congele el contrato |
| S7-08 | Reactivar con huella de hardware distinta | 409 "ya fue activada en otra PC" — regresión |
| S7-09 `[REPRO]` | Operador de plataforma cualquiera | `PATCH /platform/restaurants/{id}/status` → SUSPENDED | pasa hoy; registrar como decisión de diseño o abrir tarea de RBAC |
| S7-10 | Tenant suspendido | cualquier ruta tenant con JWT válido | 403 `application/problem+json` con detalle "This tenant account is…" — regresión de `RestaurantStatusEnforcementTest` |

---

### SUITE S8 — Frontend: guardias, sesión y regresión de build

Ficheros destino: `frontend/src/components/ProtectedRoute.test.tsx` (nuevo) y ampliación de
`store/authStore.test.ts`.

| ID | Acción | Esperado |
|---|---|---|
| S8-01 `[REPRO]` | Store con `token` presente y `role: undefined`, navegar a `/admin` | redirección a `/login`. *Actual: renderiza `<Outlet/>`* |
| S8-02 | `role: 'CUSTOMER'` navegando a `/admin` | pantalla 403 |
| S8-03 | Respuesta 401 de la API | `logout()` invocado y `queryClient` limpiado |
| S8-04 | Respuesta 403 con detalle de tenant suspendido | se abre `TENANT_SUSPENDED`; sin logout |
| S8-05 | Respuesta 403 con detalle `"Access denied"` | **no** abre el modal — regresión de `isTenantSuspendedDetail` |
| S8-06 | `setAuth` con una identidad distinta | `queryClient.clear()` antes del `set` — regresión anti fuga de caché entre tenants |
| S8-07 | — | `pnpm run build && pnpm run lint && pnpm run test:run` | verde (política de tolerancia cero, CLAUDE.md §3) |

---

### 8.9 Resumen de cobertura

| Suite | Casos | `[REPRO]` | Hallazgos cubiertos | Paralelizable |
|---|---|---|---|---|
| S1 STOMP | 8 | 4 | F-01 | ✅ |
| S2 AuthZ REST | 11 | 8 | F-02, F-07, F-08, F-16, F-18 | ✅ |
| S3 Pagos | 9 | 4 | F-03, F-04 | ✅ |
| S4 Sesión | 9 | 4 | F-05, F-06, F-19 | ✅ |
| S5 PIN | 9 | 2 | F-10, F-17 | ✅ |
| S6 Superficie pública | 9 | 4 | F-09, F-11, F-12 | ✅ |
| S7 Tokens/plataforma | 10 | 4 | F-13, F-14, F-15 | ✅ |
| S8 Frontend | 7 | 1 | F-23 | ✅ |
| **Total** | **72** | **31** | 20 de 24 | |

Hallazgos **sin** caso automatizable (revisión manual / decisión de producto):
F-20 (`ddl-auto`), F-21 (credenciales del perfil hub), F-22 (historial git), F-24 (clave del agente en disco).

---

## 9. Orden de remediación sugerido

Independiente de la ejecución de pruebas; entrada para el backlog de `PROGRESS.md`.

| Orden | Hallazgo | Esfuerzo | Razón |
|---|---|---|---|
| 1 | F-02 | XS | dos anotaciones; corta un fallo crítico de control de acceso |
| 2 | F-05, F-06 | XS | dos líneas; **desbloquean un fallo funcional en producción** |
| 3 | F-01 | M | requiere autorización de destino en el interceptor + tests |
| 4 | F-08, F-07, F-16 | S | anotaciones y comprobación de camarero asignado |
| 5 | F-03, F-04 | M | guardia de estado + lock + posible índice único |
| 6 | F-09 | S | quitar `/actuator/**` de `permitAll` fuera de `prod` |
| 7 | F-11, F-12 | S | añadir rutas a `RateLimitProperties.paths` |
| 8 | F-10 | S | unificar respuestas del login por PIN |
| 9 | F-13 | S | claim `typ` en tokens QR + validación en el consumidor |
| 10 | F-18 | M | reescribir `SecurityAuditTest` con rutas reales y matriz de roles |
| 11 | F-14, F-15 | L | decisión de producto (RBAC de plataforma, contrato de activación) |

---

## 10. Notas de ejecución para el equipo de agentes

- **Comandos canónicos** (CLAUDE.md §2): backend `cd backend && ./mvnw test`
  (`-Dtest=NombreTest` para uno solo); frontend `cd frontend && pnpm run build`, `pnpm run lint`,
  `pnpm run test:run`. No sustituir por `mvn` ni por `tsc -b` a secas.
- **Anti-bucle** (CLAUDE.md §5): si un caso falla dos veces seguidas por razones distintas a la
  esperada, detenerse y reportar el bloqueo — no seguir editando.
- **Reportes** (CLAUDE.md §4): cada suite completada genera su informe en `reports/NNN-…md` con
  numeración secuencial asignada en el commit, y actualiza `PROGRESS.md`.
- **Staging acotado**: `git add <rutas concretas>` más `PROGRESS.md` y el informe. Nunca `git add -A`
  — el repositorio tuvo secretos versionados (F-22) y hay artefactos sin seguir en el árbol
  (`ember-hub/dist/`, `ember-hub/build.env`, `to_delete/`, `docs/~$CHITECTURE.docx`).
- **Este documento no es un informe de tarea.** Vive en la raíz como entregable con nombre propio;
  los informes de las suites sí van a `reports/`.
