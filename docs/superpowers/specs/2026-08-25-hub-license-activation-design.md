# Ember Hub — Emisión de licencia y activación (Restaurant + admin) — Diseño

> Estado: **spec aprobada, lista para plan de implementación.**
> Complementa `ember_hub.md` §2.2 (Licenciamiento), que ya decidió el flujo de
> alto nivel ("admin de Vanter emite la licencia desde el panel cloud →
> cliente instala → Hub valida firma + hardware fingerprint"). Esta spec
> detalla la pieza que §2.2 no cubría: cómo el `Restaurant` + su usuario
> admin llegan a existir en la base de datos LOCAL del Hub. Surgió el
> 2026-08-25 al probar en vivo HUB-01-11 + el dashboard (reports 233–238):
> con `license.key` validando correctamente, el Hub arrancaba con una base
> de datos "ember" completamente vacía — sin ningún `Restaurant` ni usuario
> admin, haciendo el login imposible.

## 1. Motivación

`LicenseService.validateOrActivate()` (HUB-01-05) valida la firma RSA del
`license.key` y bloquea la licencia a un hardware fingerprint, pero **nunca
toca Postgres** — solo escribe el `hub-state.json` local. El `license.key`
en sí (`LicensePayload`, HUB-01-03) solo carga `restaurantId` +
`issuedAt`, sin nombre de restaurante ni credenciales de admin. No existe
hoy ninguna herramienta para que un operador de Vanter emita un
`license.key` real — HUB-01-11 usó una firma de prueba desechable
(`KeyPairGenerator` + un test JUnit temporal, nunca comiteado) porque la
clave privada RSA real tampoco existe todavía en ningún lugar seguro.

Este documento diseña de punta a punta:
1. Cómo un operador emite un `license.key` real para un restaurante ya
   existente (creado como cualquier tenant de nube, vía `/console` →
   `POST /platform/restaurants`, sin cambios).
2. Cómo, en su primer arranque real, el Hub usa ese `license.key` para
   traer los datos de ese restaurante + su admin desde la nube UNA VEZ
   (requiere internet solo esa primera vez — igual que la mayoría de
   software con licencia) y sembrarlos en su Postgres local, quedando
   utilizable offline desde ahí en adelante.

## 2. Decisiones de alcance (confirmadas con el usuario, 2026-08-25)

- **Activación requiere internet la primera vez, no debe ser 100% offline
  desde cero.** Consistente con el modelo de "grace period" de 4 días ya
  decidido en §2.8 — el Hub siempre asume alguna conectividad periódica,
  nunca aislamiento total.
- **La contraseña del admin es la MISMA en nube y Hub** — se copia el hash
  bcrypt en la activación, nunca la contraseña en texto plano. Cero pasos
  de configuración extra para el dueño del restaurante.
- **Un restaurante Hub nace exactamente igual que uno de nube**: se crea
  primero vía `/console` → `POST /platform/restaurants` (sin cambios a ese
  flujo). Emitir el `license.key` es un paso *siguiente*, sobre un
  restaurante ya existente — no hay un flujo de creación paralelo.
- **El servidor de activación recuerda que una licencia ya se activó**
  (tabla nueva, ver §4.3) — defensa en profundidad; hoy el bloqueo "ya
  activada en otra PC" solo vive en el `hub-state.json` local, que se
  puede borrar.
- **Alcance incluye la UI de `/console`** para emitir/descargar la
  licencia (botón en `ConsoleRestaurantDetail.tsx`), no solo el endpoint.
- **El botón "Registrarse" se oculta en el Login** para el build de Hub
  — pero el autoregistro de **clientes** (join de mesa, carrito
  colaborativo, lealtad) NO se toca; solo desaparece el enlace visible
  desde la pantalla de Login.

## 3. Fuera de alcance (explícitamente diferido)

- El heartbeat periódico real (`LicenseService.recordHeartbeatSuccess`)
  sigue sin ser llamado por nadie — es una pieza separada, ya conocida
  desde HUB-01, no se aborda aquí.
- Reasignar una licencia a otra PC (revocar + reemitir) — mencionado en
  §2.2 de `ember_hub.md` como capacidad futura, no se diseña aquí.
- Sincronización de datos Hub↔nube más allá de esta activación única (ver
  §2.1/§2.7 de `ember_hub.md`, sync oportunista) — esta spec solo cubre el
  arranque inicial, no sync continuo.
- Múltiples admins por restaurante: si el restaurante de nube tiene más de
  un usuario `ADMIN`, la activación solo trae el primero.

## 4. Lado nube: nuevo paquete `com.vanter.ember.licensing`

Deliberadamente separado de `com.vanter.ember.hub.*` (que es exclusivamente
código del **cliente**, activado por el perfil `hub`) — este paquete vive
siempre activo, sin gating de perfil, porque el propio backend de nube es
quien emite licencias y atiende activaciones.

### 4.1 `LicenseIssuingService`

- Carga la clave privada RSA real desde una variable de entorno nueva
  (`.env`, mismo patrón que cualquier otro secreto del proyecto — fail-fast
  si falta, sin fallback). La clave se genera **una sola vez, manualmente,
  fuera de la app** (ej. `openssl genrsa`), igual que ya se hizo con la de
  prueba en HUB-01-11 pero guardada de verdad esta vez.
- `issue(UUID restaurantId): String` construye `new LicenseKey(restaurantId,
  Instant.now())` y reutiliza `LicenseKeyParser.sign(...)` (HUB-01-03, sin
  cambios) — devuelve el contenido del `license.key`.

### 4.2 Endpoint de emisión: `POST /platform/restaurants/{id}/hub-license`

Se agrega directamente a `PlatformRestaurantController`/
`PlatformRestaurantService` existentes (mismo patrón que
`updateStatus` — operador autenticado vía el `/platform/**`
`SecurityFilterChain` ya existente, sin cambios de seguridad). Verifica que
el restaurante exista, delega a `LicenseIssuingService.issue(id)`, devuelve
el `license.key` como texto plano. Se audita en `PlatformAuditLog` (acción
`HUB_LICENSE_ISSUED`), mismo patrón que `RESTAURANT_CREATED`.

### 4.3 Entidad nueva: `HubActivation`

```
id                  UUID (generado)
restaurant_id       UUID (único, FK lógica a Restaurant — sin @TenantId,
                    mismo patrón que Restaurant/User)
hardware_fingerprint String
activated_at        Instant
```

### 4.4 `HubActivationService` + endpoint público `POST /hub-activations`

- **Público** (`permitAll` en el `SecurityConfig` del tenant, NO bajo
  `/platform/**` — el Hub no tiene JWT de operador ni de tenant en este
  punto, se autentica únicamente con la firma del `license.key`).
- Request: `{licenseKey: string, hardwareFingerprint: string}`.
- Lógica:
  1. `LicenseKeyParser.parseAndVerify(licenseKey, publicKey)` (reutilizado
     tal cual) → obtiene `restaurantId`. Firma inválida → 400.
  2. Busca `Restaurant` por ese ID → no existe → 404 (no debería pasar en
     operación normal, defensivo).
  3. Busca `HubActivation` por `restaurantId`:
     - No existe → la crea (fingerprint + `activatedAt` = ahora), continúa.
     - Existe con el MISMO fingerprint → permite (reintento legítimo desde
       la misma PC), continúa.
     - Existe con OTRO fingerprint → 409, mismo mensaje que ya usa
       `LicenseService` localmente ("Esta licencia ya está activada en
       otra PC...").
  4. Busca el primer `User` con `role=ADMIN` de ese restaurante (reutiliza
     la misma query que `PlatformRestaurantController.getById` ya usa).
  5. Devuelve `{name, slug, adminName, adminEmail, adminPasswordHash}`.
- **Nota de despliegue:** este endpoint transmite un hash bcrypt — debe
  servirse por HTTPS en producción (fuera del alcance de código de esta
  spec, es config de despliegue).

## 5. Lado Hub: nuevo paquete `com.vanter.ember.hub.provisioning`

### 5.1 `HubProvisioningRunner`

- `@Component @Profile("hub")`, implementa `ApplicationRunner`.
- Corre automáticamente dentro del ciclo de vida de Spring Boot, **después**
  de que el contexto termina de arrancar pero **antes** de
  `ApplicationReadyEvent` — por eso ni el tray icon (HUB-01-10) ni el
  dashboard (report 237) muestran "listo" hasta que el admin ya existe
  localmente.
- Lógica:
  1. Lee el `HubState` ya validado (vía `HubStateStore`, ya expuesto como
     bean en `HubBeansConfig` desde HUB-01-08) para obtener `restaurantId`.
  2. `restaurantRepository.existsById(restaurantId)` → si ya existe, no
     hace nada (arranques posteriores son gratis, cero llamadas de red).
  3. Si no existe: lee el contenido crudo de `license.key`
     (`HubProperties.licenseFile()`), calcula el fingerprint
     (`HardwareFingerprintService`, ya existe), llama a
     `POST /hub-activations` vía `java.net.http.HttpClient` (JDK, sin
     dependencia nueva) contra una URL nueva configurable
     (`EMBER_HUB_ACTIVATION_URL`, sin default hardcodeado — el dominio real
     de producción se decide al implementar/desplegar, no es parte de este
     diseño).
  4. Con la respuesta: inserta `Restaurant` (con el `id` explícito —
     confirmado viable, `GenerationType.UUID` de Hibernate respeta un ID
     ya asignado) + `User` admin con el `passwordHash` recibido tal cual
     (nunca se recalcula ni se re-hashea).

### 5.2 Manejo de errores

Cualquier falla (sin internet, 409 de otra PC, 404, respuesta inválida)
lanza una excepción sin capturar dentro del `ApplicationRunner` — Spring
Boot aborta el arranque del contexto igual que ya pasa hoy con una licencia
inválida. `HubDashboard.startServicesInBackground()` necesita ampliar su
`catch` (hoy solo atrapa `InvalidLicenseException`/
`PortableDatabaseException`, que son pre-Spring) para también mostrar en
diálogo cualquier falla que `SpringApplication.run()` propague — mismo
patrón de error retryable ya establecido, solo se amplía qué excepciones
cubre.

## 6. Frontend

### 6.1 `/console`: botón "Emitir licencia Hub"

En `ConsoleRestaurantDetail.tsx`, mismo patrón que el botón de
`toggleStatus` ya existente (`useMutation` + `platformApi.ts`). Nuevo
método `platformRestaurantService.issueHubLicense(id)` → `POST
/platform/restaurants/{id}/hub-license`; la respuesta (texto plano) se
descarga como archivo `license.key` vía un Blob + `<a download>` temporal
(patrón estándar del navegador, no relacionado con las restricciones de
descarga de Artifacts).

### 6.2 Login: ocultar "Registrarse" en el build de Hub

Reutiliza la MISMA señal que ya distingue el build de Hub del de nube —
`import.meta.env.BASE_URL` (ya usado en `App.tsx` para el `basename` del
`BrowserRouter`, sin necesidad de una env var nueva). El enlace
"Registrarse" en la pantalla de `Login.tsx` se oculta condicionalmente
cuando `BASE_URL !== '/'`. El resto del flujo de autoregistro de clientes
(`/register`, sesiones, carrito colaborativo) no cambia.

## 7. Testing

- **Unit (nube):** `HubActivationServiceTest` — firma inválida → 400; sin
  restaurante → 404; primera activación → crea `HubActivation` + devuelve
  datos; mismo fingerprint → permite; fingerprint distinto → 409. Mismo
  patrón que `LicenseServiceTest`/`GracePeriodInterceptorTest` (HUB-01).
- **Unit (Hub):** `HubProvisioningRunnerTest` con `RestaurantRepository`/
  `UserRepository`/`HttpClient` mockeados — restaurante ya existe → no-op;
  no existe + activación exitosa → inserta con el ID correcto; activación
  falla → excepción propagada.
- **Manual end-to-end (como HUB-01-11):** crear un restaurante real vía
  `/console`, emitir su licencia real, activar un Hub de prueba contra el
  backend real, confirmar login con las credenciales de nube. Se prueba en
  vivo una vez implementado — el usuario ya indicó que así lo hará.
