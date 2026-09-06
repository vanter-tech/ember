# Ember Hub — Instalador Windows (HUB-03) — Diseño

> Estado: **spec para revisión**. Continúa `docs/superpowers/specs/ember_hub.md`
> (sección 2.3, "Ruta técnica v1 — Approach A"). Cubre **solo el empaquetado y el
> arranque automático**; el runtime del Hub ya está construido y verificado a mano
> (reports 223-258, 265-271; verificación end-to-end en report 236).

## 1. Contexto

El Hub corre hoy como el mismo monolito Spring con el perfil `hub` activo:
`HubBootstrapRunner` arranca Postgres portátil y MinIO portátil **antes** de
`SpringApplication.run` (validando la licencia primero), un `HubDashboard`/tray
muestra los fallos de arranque de forma reintentable, y `GracePeriodInterceptor`
bloquea la operación si se agota la gracia de licencia sin conexión. Todo eso
funciona.

Lo que falta para que un cliente no técnico lo pueda instalar:

1. Un **JRE embebido** (el cliente no instala Java).
2. Un **instalador `.exe`** de doble clic que deje todo en su sitio (binarios,
   config, permisos de red, accesos directos).
3. **Auto-arranque**: el Hub debe levantarse solo cuando el operador enciende la
   PC de caja y abre su sesión.

### Alcance confirmado en brainstorming (2026-09-05)

- **Topología:** un único PC-servidor corre el Hub; las demás terminales (caja,
  mesero, cocina) solo abren el navegador apuntando a su IP LAN. Instalador = una
  corrida por restaurante. (Reafirma `ember_hub.md` §2.6.)
- **Pedido del cliente:** en Hub v1 el pedido lo toma **el mesero desde una
  terminal de staff**. No hay self-order desde el teléfono del cliente en Hub —
  se descartó explícitamente conectar teléfonos de clientes a la LAN local. Las
  rutas `/customer/*` de la SPA quedan presentes pero sin uso previsto en Hub v1
  (no se invierte en ocultarlas).
- **Herramienta de instalador:** `jpackage` arma la *app-image* (JRE + jar +
  launcher, puro Java); **Inno Setup** arma el `.exe` final (servicio de
  arranque, firewall, `%ProgramData%`, desinstalador, accesos directos,
  reinstalación sobre versión previa).
- **Arranque automático:** al **iniciar sesión** el usuario (entrada en la
  carpeta *Startup* / clave `Run`), **no** un servicio Windows headless. Motivo:
  conservar el tray + dashboard existentes como superficie de control del
  operador; un servicio headless obligaría a reimplementar ese status/retry en un
  proceso aparte con IPC. La PC de caja está encendida y con sesión iniciada
  durante todo el horario.

## 2. Artefacto de instalación

### 2.1 Contenido del instalador

| Pieza | Origen | Notas |
|---|---|---|
| App-image (`Ember Hub.exe` + `runtime/` JRE + `app/ember-hub.jar`) | `jpackage --type app-image` sobre el fat jar de `mvn package` | JRE derivado con `jlink`; el fat jar de Spring Boot no es modular, se incluye el runtime completo salvo módulos claramente innecesarios |
| Frontend embebido | `ember-hub/build-frontend.ps1` (ya existe) copia `frontend/dist` → `backend/src/main/resources/static/` antes de `mvn package` | Servido en `/` por `HubWebConfig` (context-path `/`) |
| PostgreSQL **16.6-1** Windows x64, "binaries only" (EDB) | descarga fijada por versión (script de build) → `pgsql/` | ~200 MB. Versión exacta confirmada en report 236 |
| MinIO (`minio.exe`) | descarga fijada por versión → `minio/` | Release ya usado en dev |
| Clave pública RSA (`hub-public-key.der`) | del repo (`ember-hub/keys/`, gitignored en real, valor de producción) | Igual para todos los clientes; valida la firma del `license.key` |
| Valores de `ember-hub/build.env` | horneados como constantes del instalador | `EMBER_HUB_ACTIVATION_URL`, `EMBER_HUB_HEARTBEAT_URL`, `EMBER_HUB_SERVER_PORT` |
| Ícono, textos de licencia/EULA | `ember-hub/installer/` | |

El `license.key` **no** va en el instalador — es específico del cliente y lo
entrega el admin de Vanter tras la compra (ver 2.4).

### 2.2 Layout tras instalar

```
%ProgramFiles%\Ember Hub\           (solo lectura para el operador; se reemplaza en cada update)
  Ember Hub.exe                      launcher jpackage
  runtime\                           JRE embebido
  app\ember-hub.jar
  pgsql\bin\ …                       binarios Postgres portátiles
  minio\minio.exe
  hub-public-key.der
  uninstall.exe

%ProgramData%\EmberHub\             (datos; sobrevive updates y desinstalación con opción "conservar datos")
  data\postgres\                     PGDATA (initdb en el primer arranque)
  data\minio\
  license.key                        lo coloca el cliente / el instalador (2.4)
  hub-state.json                     fingerprint + lastHeartbeatAt (lo escribe LicenseService)
  logs\                              stdout/stderr del launcher, pg_ctl-*.log
  backups\                           pg_dump diario con rotación (ember_hub.md §2.9)
  hub.env                            paths absolutos EMBER_HUB_* que lee el launcher
```

### 2.3 Cómo llegan los `EMBER_HUB_*` al proceso

`HubProperties.fromEnvironment()` ya lee variables `EMBER_HUB_*` con defaults
relativos. Se usa un **shim `.cmd`** (`Iniciar Ember Hub.cmd` en
`%ProgramFiles%\Ember Hub\`): carga `%ProgramData%\EmberHub\hub.env` (fichero
`clave=valor` con las rutas absolutas de 2.2, escrito por el instalador),
exporta esas variables y llama a `Ember Hub.exe`. Tanto el acceso directo como el
`.lnk` de auto-arranque apuntan a este shim, nunca al `.exe` directo. No se tocan
los defaults del código. (Fallback si el shim resulta frágil — §10.)

### 2.4 Licencia — primer arranque

En el primer arranque `HubBootstrapRunner` llama a
`LicenseService.validateOrActivate()`. Si no hay `license.key`, hoy eso es un
fallo reintentable que el dashboard muestra. Se añade a ese estado del dashboard
un **botón "Seleccionar license.key"** que copia el fichero elegido a
`%ProgramData%\EmberHub\license.key` y reintenta. (Alternativa evaluada y
descartada para v1: una página del instalador que pida el fichero — el cliente
suele recibir la licencia por correo *después* de instalar.)

## 3. Proceso de build

Un script `ember-hub/build-installer.ps1` orquesta, pensado para correr igual en
local y en CI (runner Windows):

1. `pnpm --dir frontend run build:hub` (ya lo hace `build-frontend.ps1`).
2. `mvn -pl backend -am package -DskipTests` → `backend/target/ember-*.jar`.
3. `jlink` → runtime mínimo (lista de módulos fijada en el script).
4. `jpackage --type app-image` con ese runtime + el jar → `ember-hub/dist/app-image/`.
5. Descargar/verificar (hash fijado) Postgres 16.6-1 y MinIO a
   `ember-hub/.vendor-cache/` (ya gitignored); copiarlos a la app-image.
6. `iscc ember-hub/installer/EmberHub.iss` → `ember-hub/dist/EmberHubSetup-<version>.exe`.

La versión sale de la del `pom.xml`. WiX **no** se usa (Inno no lo necesita).
`ember-hub/dist/` y `.vendor-cache/` quedan gitignored.

## 4. El instalador (Inno Setup — `EmberHub.iss`)

- Requiere privilegios de administrador (firewall + `%ProgramData%` + `Program Files`).
- Copia la app-image a `%ProgramFiles%\Ember Hub\`.
- Crea `%ProgramData%\EmberHub\{data,logs,backups}` y escribe `hub.env` con las
  rutas absolutas (2.2) y `EMBER_HUB_POSTGRES_PORT` / `EMBER_HUB_SERVER_PORT` de
  `build.env`.
- **Regla de firewall entrante** para `EMBER_HUB_SERVER_PORT` (perfil privado y
  de dominio; no público) — sin esto las otras PC no alcanzan el server por LAN.
  `netsh advfirewall firewall add rule …` desde `[Run]`.
- Acceso directo en el menú Inicio y en el escritorio → el shim de 2.3.
- **Auto-arranque:** copia un `.lnk` al mismo shim en `shell:common startup`
  (arranca sin importar qué usuario inicie sesión — cubre el cambio de operador).
- Desinstalador: quita `%ProgramFiles%\Ember Hub\`, la regla de firewall y el
  `.lnk` de arranque. **Pregunta** si conservar o borrar `%ProgramData%\EmberHub\`
  (datos + licencia); por defecto conservar.
- Reinstalación sobre versión previa (update manual, `ember_hub.md` §2.10): cerrar
  el Hub en ejecución (mensaje al operador), reemplazar `%ProgramFiles%`, dejar
  `%ProgramData%` intacto, re-lanzar. Flyway aplica migraciones nuevas en el
  siguiente arranque (`ddl-auto: validate`, la DB del Hub es propiedad de Flyway).

## 5. Arranque en runtime

1. El shim carga `hub.env` → lanza `Ember Hub.exe`.
2. `EmberApplication.main` construye `HubProperties.fromEnvironment()` y llama a
   `HubBootstrapRunner.startServices()`:
   - valida/activa licencia (2.4);
   - `PortableDatabaseBootstrap`: si `PGDATA` está vacío corre `initdb`, luego
     `pg_ctl start`; Flyway migra al arrancar Spring;
   - `PortableMinioBootstrap`: arranca `minio.exe`, crea el bucket.
3. Fallo en cualquier paso → `HubDashboard` (ventana + tray) con mensaje en
   español y botón *Reintentar*; nunca `System.exit` abrupto.
4. Éxito → Spring arranca, tray queda como indicador; el operador abre
   `http://localhost:8080/` y las otras PC `http://<ip-lan>:8080/`.
5. Shutdown hook detiene Postgres/MinIO limpiamente en cualquier salida de la JVM.

## 6. Errores de arranque

Report 236 ya verificó a mano, con mensaje accionable en español y salida no
cero / dashboard: puerto ocupado, `license.key` ausente, hardware distinto,
`PGDATA` corrupto, `initdb` sobre directorio no vacío. Este spec **no añade
casos nuevos**; el plan de implementación revisa que cada uno siga mostrándose
bien tras el empaquetado (rutas absolutas, permisos de `%ProgramData%`,
Postgres portátil real en `Program Files`).

## 7. Seguridad — conocido, fuera de alcance de HUB-03

- `application-hub.yml` trae credenciales Postgres (`ember`/`ember`) y MinIO
  (`ember-hub`/`ember-hub-local`) literales — deuda **F-21** ya registrada en
  `PROGRESS.md`. El instalador **no** la resuelve; se aborda con F-15 (contrato
  de activación) en su propia tarea, con ruta de migración para instalaciones ya
  hechas.
- El `.exe` contiene `ember-hub.jar` extraíble. `ember_hub.md` §3 ya asume que
  "binario cerrado" es una sobreestimación; se acepta para v1.
- La regla de firewall se limita a perfiles privado/dominio, nunca público.

## 8. Plan de pruebas

Sin CI para el `.exe` final — verificación manual en Windows real (mismo criterio
que `ember_hub.md` §4):

1. Instalar en una PC limpia (sin Java) → doble clic → el Hub arranca solo tras
   reiniciar sesión.
2. Otra PC en la misma LAN abre `http://<ip>:8080/` y opera (mesero toma un
   pedido, cocina lo ve, se cierra caja).
3. Colocar `license.key` vía el botón del dashboard → activa y persiste
   `hub-state.json`.
4. Reinstalar una versión mayor encima → datos y licencia intactos, Flyway migra.
5. Desinstalar conservando datos → reinstalar → historial intacto.
6. Los 5 errores de arranque de report 236 siguen mostrándose bien empaquetados.

El build (`build-installer.ps1`) sí puede correr en un job de CI Windows que
produzca el `.exe` como artefacto, aunque no lo pruebe.

## 9. Fuera de alcance

Servicio Windows headless + SCM recovery · modelo multi-nodo (cada terminal su
instancia) · shell Tauri (Hub v2) · auto-updater silencioso · backup a la nube ·
self-order del cliente en Hub · pagos digitales (`ember_hub.md` §5) · soporte
Mac/Linux.

## 10. Riesgos / preguntas abiertas

- **`jlink` sobre el classpath de Spring Boot:** el fat jar no declara módulos;
  hay que fijar a mano la lista de módulos del runtime (`jdk.crypto.ec`,
  `java.naming`, `java.sql`, `jdk.unsupported`, `java.management` para OSHI, …).
  Si falta uno, falla en runtime, no en build — se descubre en la prueba 1.
- **`%ProgramData%` en `jpackage --java-options`:** jpackage resuelve variables en
  build, no en runtime. Por eso el spec usa el shim + `hub.env`; si el shim
  resulta frágil, alternativa es que el propio `EmberApplication.main` lea
  `hub.env` de una ruta fija (`%ProgramData%\EmberHub\hub.env`) sin depender de
  variables de entorno — decisión del plan.
- **Tamaño del instalador:** ~250-300 MB (Postgres domina). Aceptable para
  entrega por descarga directa; no cabe en algunos adjuntos de correo.
- **Antivirus/SmartScreen:** un `.exe` sin firma de código dispara SmartScreen.
  Firmar el instalador (certificado de firma de código) queda como pregunta de
  negocio; no bloquea la v1 interna pero sí la venta.
- **Cambio de puerto 8080:** si la PC de caja ya usa el 8080, el instalador
  debería permitir elegir otro puerto y escribirlo en `hub.env` + la regla de
  firewall. Se decide en el plan si entra en v1 o se documenta.
