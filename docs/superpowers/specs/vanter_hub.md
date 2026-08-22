# Vanter Hub — Diseño (en progreso)

> Estado: **borrador de decisiones**, no es spec final. Se pausó la sesión de
> brainstorming para discutir temas del SaaS cloud antes de completar las
> secciones de arquitectura detallada, data flow y testing.

## 1. Motivación

Vanter Hub es la variante on-premise/offline de Ember, pensada para
restaurantes clientes que prefieren operar de forma local en vez de
contratar la versión alojada en la nube. No es un producto nuevo: es el
mismo SaaS (Ember), reempaquetado para correr en la PC del restaurante sin
depender de conexión a internet para su operación diaria (POS, KDS,
impresión de tickets, cierre de caja).

**Alcance de negocio confirmado:** se va a ofrecer como SKU real a
restaurantes cliente (no es solo para uso interno del propio negocio del
usuario).

**Restricciones del proyecto:**
- Equipo: 2 (usuario + IA).
- Plazo: máximo 2 meses para tener una v1 tangible y vendible.

## 2. Decisiones tomadas

### 2.1 Modelo de sincronización con la nube: sync oportunista, acotado por dominio

El Hub opera siempre en local (no depende de internet para funcionar), pero
cuando hay conexión sincroniza con la nube de Vanter. Se descartó el modelo
de "isla 100% aislada" porque:
- El check-in periódico de licencia ya requiere un canal de red saliente, así
  que agregar sync de datos es incremental, no un subsistema nuevo.
- Sin ningún phone-home, no hay forma de mitigar copias no autorizadas de la
  instalación.
- Aislar totalmente pierde el valor comercial de dashboards centralizados
  multi-sucursal.

Para evitar el problema difícil de resolución de conflictos genérica
(CRDTs, vector clocks), la dirección de sync se define **por dominio**, no
bidireccional genérica:

| Dominio | Dueño de la verdad | Dirección de sync |
|---|---|---|
| Catálogo / precios / configuración | Nube | Nube → Hub (push cuando hay internet) |
| Órdenes / ventas / historial | Hub local | Hub → Nube (upload de registros cerrados/inmutables) |
| Licencia | — | Heartbeat bidireccional |

Esto evita edición concurrente del mismo registro: el catálogo se edita en
el panel admin central, las ventas se generan solo en el Hub.

### 2.2 Licenciamiento

- **Hardware fingerprint** (CPU + placa madre): evita instalar la misma
  licencia en más de una PC sin autorización.
- **Restaurant ID / tenant vinculado**: la licencia no es genérica, está
  atada a un restaurante específico del cliente. Permite:
  - Desactivación remota (próximo heartbeat bloquea el Hub de un cliente
    moroso, si tiene internet).
  - Reasignación controlada cuando el cliente cambia de PC (revocar +
    reemitir atada al mismo `restaurant_id`).
  - Evitar que datos sincronizados se mezclen entre tenants.
- **Firma RSA** sobre el `license.key`, validada al iniciar el Hub.
- Flujo de activación: admin de Vanter emite la licencia desde el panel
  cloud (elige restaurante → genera `license.key` firmado con ese
  `restaurant_id`) → cliente instala → Hub valida firma + guarda hardware
  fingerprint la primera vez → heartbeats posteriores validan ambos.

### 2.3 Ruta técnica para v1 (Approach A — "Hub JVM simple")

Se descartó ir directo a Tauri + Rust + GraalVM native-image como v1 por
riesgo de no llegar a los 2 meses (ni el usuario ni la IA tienen experiencia
previa con Rust/GraalVM native-image, y la fricción esperada con Spring
AOT + Hibernate + driver de Mongo + STOMP es real). Se eligió una ruta que
usa únicamente herramientas del stack Java/Spring ya conocido:

- **Empaquetado:** `jpackage` + `jlink` → JRE embebido, instalador `.exe`
  nativo de Windows. El cliente no instala Java por separado.
- **Proceso único:** el propio Spring Boot corre como el proceso principal;
  sin Rust, sin Tauri, sin watchdog externo.
- **Bandeja del sistema:** `java.awt.SystemTray` dentro del mismo proceso
  (ícono, estado, salir) — no hay un segundo proceso que supervisar.
- **Frontend:** el mismo Spring Boot sirve los estáticos de React ya
  compilados; se abre el navegador por defecto automáticamente al iniciar.
- **Persistencia:** Postgres portátil (binarios oficiales de EDB extraídos,
  sin instalador, iniciado localmente vía `pg_ctl`). Se mantiene el mismo
  motor relacional que ya usa el backend (cero migración de JPA).
  - Los dominios que hoy viven en MongoDB (`session`, `kitchen`) se migran a
    tablas Postgres usando columnas `JSONB` para los arrays embebidos
    (participants, order items) — conserva el modelo de "documento
    embebido" sin correr dos motores de base de datos en la máquina del
    cliente.
- **Recuperación ante caídas:** registrar el proceso como servicio de
  Windows y usar `sc.exe failure` (recovery nativo del Service Control
  Manager) en vez de construir un watchdog custom.
- **Protección de IP:** se acepta que un JAR es más débil que un
  native-image; como mitigación barata para v1 se evalúa ofuscación de
  bytecode (ProGuard). No se persigue protección fuerte en v1.

**Diferido a versiones futuras (no en v1):**
- v1.1: Tauri como shell de UI/bandeja (mejor cara de producto), backend
  sigue siendo JAR normal — sin GraalVM native todavía.
- v2+: GraalVM native-image (Approach B del plan original) una vez el SKU
  offline esté validado comercialmente y la protección de IP contra
  clonación importe a mayor escala (más clientes = más incentivo de
  ingeniería inversa).

### 2.4 Puente de impresión (reutilizable entre cloud y Hub)

Del análisis previo del SaaS cloud: la impresión de tickets/ESC-POS desde
la nube requiere un agente local con conexión saliente (WebSocket) porque
el servidor cloud no puede alcanzar impresoras dentro de la LAN del
restaurante. Se decidió que este mismo componente de "Hardware Bridge" sea
el único mecanismo de impresión, tanto para el modo cloud puro como para
Vanter Hub — evita mantener dos soluciones de impresión distintas.

## 3. Riesgos identificados (documentados, no bloqueantes para v1)

- GraalVM native-image + Spring AOT tiene fricción real con
  Hibernate/Jackson/driver de Mongo/STOMP — por eso se excluyó de v1.
- La afirmación "binario cerrado, no descompilable" del plan original es
  una sobreestimación: un native-image sube la barrera de ingeniería
  inversa, pero no la elimina.
- Docker Desktop **no** es una vía de distribución viable para el cliente
  final (fricción de instalación, licenciamiento de Docker Business fuera
  de negocios pequeños, expone `.jar` extraíbles, consumo de recursos alto)
  — descartado explícitamente como estrategia de empaquetado comercial.
  Docker sigue siendo válido solo como entorno de desarrollo interno.

## 4. Pendiente por decidir (próxima sesión de diseño)

- Alcance de paridad de funcionalidades: qué debe seguir funcionando
  offline vs qué se degrada sin internet (pagos con tarjeta, notificaciones
  push, analítica cross-sucursal, etc.).
- Arquitectura detallada de componentes, data flow y manejo de errores del
  Hub (sección de diseño formal aún no presentada).
- Estrategia de actualizaciones (auto-updater) para instalaciones ya en
  campo.
- Estrategia de backup local de la base de datos del restaurante.
- Plan de pruebas/testing para el Hub (entorno Windows real con impresora
  física, distinto del CI actual).
- Modelo comercial (pricing) del SKU offline vs el SKU cloud.

**Nota de contexto:** antes de continuar con las secciones pendientes de
este diseño, el usuario quiere discutir temas del SaaS cloud actual.
