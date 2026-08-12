# Informe Preliminar — Proyecto Ember

**Fecha:** 2026-08-12
**Rama analizada:** `feature/kitchen-view` (con cambios sin commitear)
**Alcance:** Análisis estático de solo lectura. No se modificó código.

---

## 1. Resumen ejecutivo

Ember es una app de gestión de restaurante (backend Java/Spring Boot + frontend React/TS) organizada como **monolito modular**, con seis módulos de negocio bien delimitados en el backend y vistas por rol en el frontend (cliente, mesero, cocina, admin). El flujo core — sesión de mesa → carrito colaborativo → cocina → facturación — está implementado end-to-end y cubierto por una suite de 39 archivos de test en el backend, incluyendo un test E2E completo.

**Hallazgo más relevante para corregir el marco mental del proyecto:** a pesar del nombre del curso/repo y de que `spring-kafka` está en el `pom.xml`, **Kafka no se usa en ningún lugar del código**. La comunicación entre módulos (`session`, `kitchen`, `billing`) se hace con `ApplicationEventPublisher`/`@EventListener` de Spring — eventos en memoria, síncronos, sin bus de mensajería real. Esto es consistente con el roadmap (`ember_backend_roadmap.csv`), así que no parece una migración abandonada, sino una dependencia sobrante. Vale la pena decidir explícitamente si se completa la integración con Kafka o se retira la dependencia.

**Estado general:** backend sólido y bien testeado pero con varios problemas de concurrencia/transaccionalidad de fondo (ver §3). Frontend funcional pero con **el build roto ahora mismo** (`tsc -b` falla) y sin ningún test automatizado.

---

## 2. Arquitectura

### Backend (`backend/`)
- **Stack:** Spring Boot 3.5.14, Java 17, Spring Security (JWT/JJWT 0.12.3, HS256), Spring Data JPA + Spring Data MongoDB, WebSocket/STOMP, MinIO (imágenes), Micrometer + Prometheus/Grafana, springdoc-openapi.
- **Persistencia poliglota:** PostgreSQL/JPA para `identity`, `catalog`, `billing`, `settings`/`restaurant`. MongoDB para `session` (documento `Session` con `participants`/`items` embebidos) y `kitchen` (`KitchenOrder` con `KitchenItem` embebidos).
- **Módulos:** `identity`, `catalog`, `session`, `kitchen`, `billing`, `settings`, `restaurant`, cada uno con `controller/dto/model/repository/service` y, donde aplica, `event/listener`.
- **Flujo principal:**
  1. **Sesión:** el mesero crea una sesión (`SessionService.createSession`) → se genera un JWT de QR (15 min) → clientes se unen por QR o código de 5 caracteres → carrito colaborativo en tiempo real vía STOMP (`/topic/session/{id}`).
  2. **Cocina:** al confirmar el pedido se publica `KitchenItemsConfirmed` → `KitchenService` crea/actualiza el `KitchenOrder` en Mongo → transiciones de estado controladas (DRAFT→PENDING→PREPARING→READY→DELIVERED) → evento de vuelta hacia `SessionService` para sincronizar el estado en la sesión y remitirlo por WebSocket.
  3. **Facturación:** el mesero calcula la cuenta sobre ítems DELIVERED/READY, se divide (igual o por consumo), se registran pagos, y al completarse todos los splits se publica `PaymentCompleted` que cierra la sesión.
- **Seguridad:** JWT stateless, `@PreAuthorize` por endpoint, BCrypt, interceptor STOMP que autentica en el handshake, rate limiter simple para login/registro.
- **Testing:** 39 archivos de test (controller/service/repository/config) + un E2E completo (`E2EOrderFlowTest`, 228 líneas) que recorre todo el flujo real.

### Frontend (`frontend/`)
- **Stack:** React 19 + TypeScript, Vite, react-router-dom v7, TanStack Query 5, Zustand 5, axios, shadcn/ui + Tailwind 4, STOMP sobre SockJS para WebSocket.
- **Routing:** rutas protegidas por rol (`ProtectedRoute`) para `/customer`, `/admin`, `/waiter`, `/kitchen`; redirección automática por rol desde `/`.
- **Estado:** stores de Zustand persistidos en localStorage (`authStore`, `sessionStore`), `uiStore` no persistido, y `queryClient.ts` (nuevo, sin opciones por defecto configuradas).
- **API:** instancia única de axios con interceptor de auth (Bearer token) y logout automático en 401.
- **WebSocket:** store dedicado que conecta a un STOMP/SockJS, gestiona una única suscripción activa a `/topic/session/{id}` y despacha eventos al `sessionStore`.
- **Trabajo en curso sin commitear (rama `feature/kitchen-view`):** se está construyendo la vista de cocina (`pages/kitchen/`, `OrdersDisplay`, `QueueCard`, `FocusedCard`), moviendo `QueryClientProvider` a `App.tsx`, y añadiendo `kitchenServices` al cliente API. Es scaffolding temprano: botones sin `onClick`, texto placeholder (`Cliente: #-Por-iterar`).

---

## 3. Errores latentes y riesgos

### Backend — concurrencia y consistencia (los más importantes)
1. **Sin bloqueo optimista en `Session` (Mongo).** `session/model/Session.java` no tiene `@Version`. Operaciones de lectura-modificación-escritura concurrentes (dos clientes agregando ítems a la vez, o cocina actualizando estado mientras un cliente agrega ítems) pueden pisarse silenciosamente ("last write wins"). Es el riesgo más consecuente dado que el producto se basa en carritos colaborativos en tiempo real.
2. **Doble facturación posible (`BillingService.calculateBill`):** el chequeo "¿ya existe factura?" no está protegido por `@Transactional` ni por constraint único en `Bill.sessionId`. Dos solicitudes concurrentes de cálculo de cuenta pueden crear dos `Bill` para la misma sesión.
3. **Detección de "todo pagado" con condición de carrera** (`PaymentService`): cada pago relee independientemente los splits; si dos pagos finales llegan casi simultáneos, ninguno puede ver `allPaid == true` y `PaymentCompleted` nunca se dispara — la sesión no se cierra automáticamente.
4. **Falta de transaccionalidad generalizada:** solo `SettingService` usa `@Transactional` en todo el backend. Flujos multi-escritura en `billing`/`session`/`kitchen` (guardar split, guardar pago, verificar y publicar evento) quedan expuestos a estados intermedios inconsistentes si algo falla a mitad de camino.
5. **`confirmDraftsForUser` con fallo parcial:** si la búsqueda de la mesa falla después de persistir ítems como PENDING pero antes de publicar `KitchenItemsConfirmed`, los ítems quedan "enviados" sin que cocina los vea nunca.
6. **Cap de participantes con valor obsoleto:** `joinSession` valida contra el `maxParticipants` codificado en el JWT del QR (al momento de generarlo), no contra el valor actual de la sesión — si un mesero amplía el aforo, códigos QR ya compartidos siguen aplicando el límite viejo.
7. **Falta de verificación de identidad en `confirmMyOrder`:** el endpoint solo exige rol `CUSTOMER`, sin comprobar que el `userId` del path coincida con el usuario autenticado — cualquier cliente de la sesión podría confirmar (enviar a cocina) los borradores de otro participante.

### Backend — configuración y robustez
8. **Secretos versionados en `application.yml`** (sin sufijo de perfil, siempre cargado): password de BD, secreto JWT y credenciales de MinIO por defecto hardcodeadas. `application-prod.properties` sí externaliza correctamente vía variables de entorno, pero conviene rotar el secreto JWT y no dejarlo como default en el repo.
9. **CORS y orígenes WebSocket fijados a `localhost`**, sin equivalente configurado para producción — tal como está, un despliegue real rechazaría el origen del frontend en ambos casos.
10. **Rate limiter de login con fuga de memoria lenta:** el mapa de IPs nunca elimina claves antiguas; además usa `getRemoteAddr()` directo, que detrás de un proxy/LB reportará la IP del proxy, no la del cliente real.
11. Sin manejador de excepciones "catch-all" (`GlobalExceptionHandler` no cubre `Exception.class`): errores no mapeados (NPE, timeouts de BD) caen al manejo por defecto de Spring en vez del formato `ProblemDetail` consistente del resto de la API.
12. Sin paginación en listados (`KitchenController`, `MenuItemController`) — bajo riesgo hoy, pero a vigilar con el crecimiento de datos.
13. Política de contraseña débil en registro (`@NotBlank` solamente, sin longitud mínima).
14. Inconsistencias menores: bucket de MinIO hardcodeado en `MenuItemService.update` en vez de reutilizar la config; parsing frágil de URLs en `ImageUploadService.deleteImage` (puede lanzar `ArrayIndexOutOfBoundsException`); mensaje de error incorrecto en `createSession` ("Session not found" cuando debería decir "Table not found"); variables con nombres en español mezcladas en código en inglés.

### Frontend — bloqueantes
15. **El build está roto ahora mismo:** `tsc -b --noEmit` falla con ~23 errores `TS6133`/`TS6192` (variables/imports no usados bajo `noUnusedLocals`/`noUnusedParameters`), tanto en el código nuevo de cocina como en código ya existente (`ComandaView.tsx`, `Menu.tsx`, `ItemsFloatingIsland.tsx`, `Tables.tsx`). Como `npm run build` es `tsc -b && vite build`, **el proyecto no compila** en este momento.
16. **`npm run lint` falla al iniciar:** `eslint.config.js` importa `eslint-plugin-prettier/recommended`, pero el paquete no está en `devDependencies` ni instalado. No hay señal de lint disponible para nadie del equipo.
17. **Sin ningún test automatizado en el frontend** (no hay vitest/jest/testing-library, ni un solo `*.test.tsx`). Cobertura efectiva: 0%.

### Frontend — otros riesgos
18. **URL de WebSocket hardcodeada a `localhost`** (`store/websocket.ts`), a diferencia del cliente REST que sí resuelve la URL por entorno — romperá la conexión en cualquier despliegue que no sea local.
19. **`isConnected` nunca se resetea** al perderse la conexión (no hay `onDisconnect`/`onStompError`/`onWebSocketError`), dejando estado obsoleto que otros componentes usan para decidir si suscribirse.
20. **Suscripción duplicada al mismo tópico:** el store de WebSocket y `FloatingNav.tsx` se suscriben ambos, por separado, a `/topic/session/{id}`; además `FloatingNav` chequea un campo (`eventData.status === 'CLOSED'`) que no coincide con el esquema que realmente emite el backend (`eventData.type === 'SESSION_CLOSED'`), por lo que esa rama de detección de cierre probablemente nunca se ejecuta.
21. Reconexión completa (desconectar + reconectar) del socket en cada cambio de `sessionId`, sin garantía de secuenciación entre llamadas async.
22. Posible violación de reglas de hooks en `FloatingNav.tsx` (early return antes de un `useEffect`), riesgo de orden de hooks inconsistente entre renders.
23. Tipado débil (`any`) en varios stores (`currentSubscription`, `modalPayload`, `updateSession`), combinado con uso liberal de aserciones no-nulas (`!`) en un `tsconfig` que no tiene `strict: true`.
24. `console.log`/`console.error` dejados en código de producción en múltiples archivos (no bloqueante, pero ruido en consola de usuarios finales).
25. Sin Error Boundary en toda la aplicación — cualquier excepción de render tumba el árbol de React completo sin fallback.
26. Vista de cocina nueva sin estados de carga/error (`OrdersDisplay.tsx`) y sin `key` en el `.map()` de la lista de pedidos.

---

## 4. Puntos de mejora sugeridos (priorizados)

**Antes de mergear `feature/kitchen-view`:**
- Arreglar los errores de `tsc` (limpiar imports/variables no usados) para que el build vuelva a pasar — es el bloqueante más urgente.
- Instalar `eslint-plugin-prettier` o quitar esa importación de `eslint.config.js` para recuperar la señal de lint.
- Resolver la suscripción WebSocket duplicada/inconsistente en `FloatingNav` vs. el store.

**Corto plazo (backend):**
- Agregar `@Version` a `Session` para bloqueo optimista, o rediseñar las actualizaciones como operaciones atómicas de Mongo (`findAndModify`) en vez de leer-modificar-guardar todo el documento.
- Envolver en `@Transactional` los flujos de `billing`/`payment`, y agregar constraint único en `Bill.sessionId`.
- Corregir la verificación de identidad en `confirmMyOrder` y el cap de participantes basado en JWT obsoleto.
- Definir explícitamente si Kafka se integrará (y para qué casos: cross-service, durabilidad, retries) o se retira la dependencia sobrante.
- Mover los defaults sensibles de `application.yml` a variables de entorno / `.env`, y configurar orígenes CORS/WebSocket por perfil.

**Corto plazo (frontend):**
- Introducir un framework de testing (Vitest + Testing Library) aunque sea con cobertura mínima inicial en flujos críticos (auth, carrito, WebSocket).
- Resolver la URL de WebSocket hardcodeada usando el mismo mecanismo de resolución de entorno que ya tiene `lib/api.ts`.
- Añadir manejo de `onDisconnect`/`onStompError` para mantener `isConnected` consistente.
- Agregar un Error Boundary global.

**Mediano plazo (ambos):**
- Backend: manejador catch-all de excepciones, paginación en listados, política de contraseñas más estricta.
- Frontend: code-splitting por rol (`React.lazy`), extraer un hook común para las mutaciones CRUD de admin (patrón repetido en varios modales), habilitar `strict: true` en TypeScript de forma incremental.

---

## 5. Nota metodológica

Este informe se generó mediante análisis estático (lectura de código, `git log`, ejecución de `tsc -b` y `npm run lint` en modo diagnóstico) sin ejecutar la aplicación ni modificar ningún archivo. Los hallazgos de concurrencia (§3, ítems 1–6) están basados en inspección de código, no en tests de carga reales; se recomienda validarlos con pruebas dirigidas antes de priorizar su corrección.
