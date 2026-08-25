# Vanter Hub — Diseño (en progreso)

> Estado: **borrador de decisiones**, no es spec final. Arquitectura núcleo
> (secciones 2.1–2.12) ya cerrada — retomada y completada el 2026-08-24. Queda
> abierto el modelo de pagos digitales (sección 5), que se discute en su
> propia sesión antes de escribir el plan de implementación.

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
Vanter Hub — evita mantener dos soluciones de impresión distintas. Un
cliente cloud puro no necesita instalar Vanter Hub completo solo para
imprimir: instala únicamente el agente liviano (`printing-agent/`, ya
construido en EMB-PRINT). Ver 2.11 para su ampliación a otros periféricos.

### 2.5 Alcance de paridad de funcionalidades offline

- **Analítica:** el Hub muestra analítica **solo de su propio
  restaurante/sucursal**, nunca cross-sucursal. Esto no es una versión
  reducida — `/admin/analytics` (task-5.17–5.21) ya está scopeada por
  tenant, así que correr localmente no requiere cambios de código. El
  dashboard cross-sucursal para dueños multi-local es una feature del
  panel cloud central (consumiendo lo que cada Hub sube), fuera del
  alcance del Hub mismo.
- **Loyalty:** corre igual que hoy si el tenant lo tiene activado — 100%
  local sobre Postgres, sin cambios.
- **Pagos digitales:** ver sección 5 — parqueado, pendiente de su propia
  sesión de diseño (posible pivote de pasarela tradicional a depósito
  bancario + conciliación).

### 2.6 Topología de red en el local

Un solo PC actúa como "servidor": corre el Hub completo (Spring Boot +
Postgres portátil), normalmente la PC de caja. Las demás terminales
(tablets de mesero, pantalla de cocina) se conectan por navegador a la IP
local de esa PC dentro de la LAN del restaurante — mismo modelo que hoy
usa un navegador para conectarse al dominio cloud, solo que apuntando a
una IP local. No se contempla para v1 un modelo multi-nodo (cada puesto
con su propia instancia sincronizando en LAN); eso implicaría replantear
la ruta técnica de la sección 2.3.

### 2.7 Mecanismo de sincronización (data flow)

Polling HTTP periódico (cada 5–10 min), no conexión persistente. Un solo
endpoint del lado Hub hace, en una sola llamada saliente:
1. Heartbeat de licencia (ver 2.2/2.8).
2. Descarga de deltas de catálogo/precios/configuración (nube → Hub).
3. Subida de ventas/órdenes cerradas pendientes (Hub → nube).

Manejo de errores: si un ciclo falla (sin internet, timeout, etc.), no se
reintenta de inmediato — simplemente se reintenta completo en el
siguiente ciclo programado. No hace falta lógica de reconexión de socket
ni heartbeat de conexión persistente (se evita deliberadamente el patrón
WebSocket del Hardware Bridge aquí, que ya tuvo bugs sutiles de
aislamiento de broker — ver `PROGRESS.md`, "WebSocket endpoint isolation
gotcha"). Las ventas subidas son records cerrados/inmutables, así que un
reintento de subida es siempre seguro (idempotente por id).

### 2.8 Periodo de gracia de licencia sin conexión

4 días desde el último heartbeat exitoso. Dentro de la gracia, el Hub
opera con normalidad aunque no haya internet. Al superar los 4 días sin
un heartbeat exitoso, el Hub bloquea su operación (no procesa nuevas
órdenes/pagos) hasta reconectar — pero **nunca borra datos locales**; al
recuperar conexión y validar la licencia, vuelve a operar con todo el
historial intacto.

### 2.9 Backup local de base de datos

`pg_dump` automático diario (ej. de madrugada, fuera de horario de
operación) hacia una carpeta local, con rotación (ej. últimos 14 días).
Protege contra corrupción de datos o error humano, no contra pérdida
física de la PC/disco. Sin subida a la nube en v1 — se evalúa como mejora
futura una vez el canal de sync (2.7) esté validado en producción.

### 2.10 Estrategia de actualizaciones (auto-updater)

Manual con aviso, no automático/silencioso en v1. El Hub revisa la
versión disponible en la respuesta de cada heartbeat (2.7); si hay una
más nueva, muestra un aviso en el ícono de bandeja/UI con link al
instalador nuevo. El dueño/mesero decide cuándo reinstalar (fuera de
horario pico) — evita el riesgo de romper un turno en producción por un
update automático fallido, dado el tamaño del equipo (2 personas) y el
plazo (2 meses).

### 2.11 Hardware Bridge — ampliación a otros periféricos

Se mantiene como el mismo agente Java standalone compartido entre cloud y
Hub (2.4). Primeros dos periféricos priorizados más allá de impresión:

- **Gaveta de dinero (cash drawer):** se abre normalmente con un comando
  ESC-POS enviado a través de la misma impresora térmica (puerto RJ11 de
  kick-out), no es un dispositivo USB aparte. Candidato a implementarse
  como un nuevo tipo de `PrintJob` (ej. `CASH_DRAWER_KICK`) disparado al
  confirmar un pago en efectivo, reutilizando `PrintDispatchService` tal
  cual — probablemente no requiere nueva arquitectura de agente.
- **Lector de código de barras:** en la mayoría de casos es un
  dispositivo USB-HID tipo teclado — el navegador ya lo recibe como texto
  tecleado, sin pasar por el agente. El trabajo real es de UX frontend
  (un campo de búsqueda por SKU con foco/auto-submit en las pantallas
  relevantes de admin/mesero), no de Hardware Bridge.

Ambos supuestos (que no requieren nueva arquitectura del agente) se
confirman en la prueba con hardware real (ver sección 4) antes de
comprometerse a más trabajo del que hoy parece necesario.

### 2.12 Modelo comercial / pricing

Pago anual que cubre mantenimiento y soporte — no suscripción mensual
estilo cloud. No impone restricciones técnicas nuevas: el mecanismo de
heartbeat/licencia (2.2/2.8) ya soporta este modelo de facturación tal
cual está diseñado.

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
- Notificaciones push (a celular del admin, etc.) dependen de un servicio
  externo (FCM/APNs) y no pueden funcionar sin salida a internet — se
  degradan sin más análisis necesario, no requieren diseño propio.
- Las impresoras Epson ya instaladas para la prueba de la sección 4 podrían
  no ser térmicas POS (tipo TM-) y por tanto no hablar ESC-POS crudo por
  USB/red 9100 como espera el Hardware Bridge — se confirma en la prueba
  misma, no se asume de antemano.

## 4. Plan de pruebas

- Se prueba primero con las impresoras Epson ya instaladas en el negocio
  del usuario (ver riesgo arriba sobre compatibilidad ESC-POS). Si no
  compilan como impresoras térmicas POS, PRINT-07 (prueba end-to-end con
  impresora física) queda pendiente de último, sin bloquear el resto del
  Hub.
- Mismo entorno sirve para validar 2.6 (topología LAN), 2.11 (gaveta de
  dinero vía RJ11, lector de código de barras vía HID) y el instalador
  `jpackage`/`jlink` en Windows real — no hay CI que cubra ninguno de
  estos, es verificación manual.
- Manejo de errores de arranque (Postgres portátil corrupto, licencia
  inválida al iniciar, puerto ocupado, etc.) no se detalló en esta sesión
  de brainstorming — queda para especificarse durante el plan de
  implementación, informado por lo que se observe en esta prueba real.

## 5. Pendiente por decidir

- **Modelo de pagos digitales:** el usuario propone reemplazar la
  pasarela de tarjeta tradicional (asumida en EMB-GATEWAY/GATEWAY-01) por
  un modelo de depósito bancario — se despliega la cuenta bancaria del
  restaurante al cliente para que pague desde su app bancaria, y el
  mesero concilia los depósitos contra las cuentas por cobrar (posiblemente
  vía scraping del banco). Afecta por igual al SaaS cloud y a Vanter Hub
  (no es específico de uno u otro), y probablemente reemplaza/redefine
  GATEWAY-01 en vez de solo resolverlo. Se **parqueó deliberadamente**
  fuera de esta sesión para no repetir la pausa anterior — requiere su
  propia sesión de brainstorming antes de tocar el plan de implementación
  del Hub o de EMB-GATEWAY.
