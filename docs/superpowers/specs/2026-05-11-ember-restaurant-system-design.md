# Ember — Diseño del Sistema de Gestión de Restaurantes

**Fecha:** 2026-05-11
**Autor:** Fernand-O-band01
**Estado:** Aprobado

---

## 1. Contexto y Objetivo

Ember es una aplicación de gestión de restaurantes para Vanter. El objetivo central es un **lobby de mesas** donde los clientes hacen pedidos grupales en tiempo real, con lógica de división de cuentas al final. El sistema cubre cuatro roles: Cliente, Mesero, Cocina y Administrador.

---

## 2. Estrategia de Implementación

**Monolito modular como arquitectura inmediata. Microservicios como visión objetivo.**

El sistema se construye como un único proyecto Spring Boot organizado en módulos que respetan los límites de dominio definitivos. Esto evita la sobrecarga de infraestructura de microservicios en etapa temprana con un solo desarrollador, mientras garantiza que la migración futura sea quirúrgica (extraer un paquete → convertirlo en servicio).

### Regla de oro del monolito modular
Ningún módulo accede directamente a las clases internas de otro. La comunicación entre módulos ocurre únicamente a través de interfaces de servicio o eventos internos (ApplicationEvent de Spring). Esta disciplina es lo que hace la migración posible sin reescritura.

---

## 3. Actores del Sistema

| Actor | Interface | Descripción |
|---|---|---|
| Cliente | React Native (móvil) | Escanea QR, navega menú, pide, paga |
| Mesero | React Web | Gestiona mesas, sesiones, cobros |
| Cocina | React Web (display) | Ve cola de pedidos, actualiza estados |
| Administrador | React Web | Gestiona menú, mesas, reportes |

---

## 4. Estructura del Monolito Modular

### Paquete raíz: `com.vanter.ember`

```
com.vanter.ember
├── identity/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── model/
├── catalog/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── model/
├── session/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── model/
├── billing/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── model/
└── kitchen/
    ├── controller/
    ├── service/
    ├── repository/
    └── model/
```

### Responsabilidades por módulo

| Módulo | Responsabilidad |
|---|---|
| `identity` | Registro, login, JWT, roles (CUSTOMER, WAITER, KITCHEN, ADMIN) |
| `catalog` | Menú (ítems, categorías, precios, imágenes), mesas (número, capacidad, estado), configuración del restaurante |
| `session` | Sesiones de mesa (crear, aforo controlado, QR, unirse), pedidos colaborativos en tiempo real, WebSocket |
| `billing` | Cálculo de cuentas, división (por consumo / partes iguales), integración con pasarela de pago, pago físico |
| `kitchen` | Cola de pedidos de cocina, actualización de estado por ítem |

---

## 5. Flujo Principal del Sistema

```
1. Mesero crea sesión de mesa con N participantes exactos → genera QR
2. Clientes escanean QR → entran a la sesión
3. Cada cliente agrega ítems del menú → carrito compartido en tiempo real (WebSocket)
4. Todos ven el pedido de todos con nombre del participante
5. Cocina recibe pedidos → actualiza estado (pending → preparing → ready → delivered)
6. Clientes ven actualizaciones en tiempo real
7. Mesero o cliente solicita la cuenta
8. Sistema ofrece: dividir por consumo OR partes iguales
9. Pago: digital (pasarela) o físico (marcado manualmente por mesero)
10. Sesión cerrada → mesa liberada
```

### Sesión de mesa — control de acceso

- El mesero crea la sesión y define el aforo exacto
- El sistema genera un QR firmado (JWT con sessionId + expiración corta)
- El QR solo admite hasta el número de participantes definido
- El mesero puede ampliar el aforo si se necesita agregar alguien
- Un participante extra solo puede unirse si el mesero incrementa el aforo primero

---

## 6. Bases de Datos

### PostgreSQL — datos relacionales y financieros

**Módulo `identity`**
```sql
users (id, name, email, password_hash, role, created_at)
-- role: CUSTOMER | WAITER | KITCHEN | ADMIN
```

**Módulo `catalog`**
```sql
categories  (id, name)
menu_items  (id, name, description, price, category_id, available, image_url)
tables      (id, number, capacity, status)
-- status: AVAILABLE | OCCUPIED
```

**Módulo `billing`**
```sql
bills        (id, session_id, total, split_method, status, created_at)
-- split_method: BY_CONSUMPTION | EQUAL_PARTS
-- status: OPEN | PAID
bill_splits  (id, bill_id, participant_name, amount, paid)
payments     (id, bill_id, amount, method, gateway_ref, status, created_at)
-- method: DIGITAL | PHYSICAL
```

### MongoDB — documentos flexibles y alta frecuencia de escritura

**Módulo `session`**
```json
{
  "_id": "session-id",
  "tableId": "table-id",
  "waiterId": "user-id",
  "status": "OPEN",
  "maxParticipants": 4,
  "participants": [
    { "userId": "u-1", "name": "Ana" },
    { "userId": "u-2", "name": "Luis" }
  ],
  "items": [
    {
      "itemId": "m-10",
      "name": "Pasta",
      "price": 12.50,
      "participantName": "Ana",
      "status": "PREPARING"
    }
  ],
  "createdAt": "2026-05-11T20:00:00Z"
}
```

**Módulo `kitchen`**
```json
{
  "_id": "kitchen-order-id",
  "sessionId": "session-id",
  "tableNumber": 5,
  "items": [
    {
      "itemId": "m-10",
      "name": "Pasta",
      "participantName": "Ana",
      "status": "PENDING",
      "updatedAt": "2026-05-11T20:01:00Z"
    }
  ]
}
```

### MinIO — almacenamiento de imágenes

Las imágenes del menú se almacenan en MinIO (object storage S3-compatible). El campo `image_url` en `menu_items` contiene la URL pública del archivo. En ningún caso se almacena base64 en la base de datos.

---

## 7. Tiempo Real — WebSocket con STOMP

**Protocolo:** STOMP sobre WebSocket, nativo en Spring Boot (`spring-boot-starter-websocket`).

### Topics de suscripción

```
/topic/session/{sessionId}         → todos los participantes de la mesa
/topic/session/{sessionId}/waiter  → mesero asignado
/user/queue/personal               → notificaciones individuales
```

### Tipos de mensajes

| Evento | Destinatario | Trigger |
|---|---|---|
| `ITEM_ADDED` | Todos en la sesión | Cualquier participante agrega ítem |
| `ITEM_STATUS_UPDATED` | Todos en la sesión | Cocina actualiza estado |
| `PARTICIPANT_JOINED` | Todos en la sesión | Cliente entra via QR |
| `BILL_READY` | Todos en la sesión | Billing calcula la cuenta |
| `SESSION_CLOSED` | Todos en la sesión | Pago completado |

### Autenticación WebSocket

JWT enviado en header STOMP al conectarse (no como query param):
```
CONNECT
Authorization: Bearer <jwt>
```

El módulo `session` valida el JWT, extrae el `sessionId` activo del usuario, y lo registra internamente. Si el cliente se desconecta (pérdida de señal móvil), puede reconectarse y retomar — el estado vive en MongoDB, no en memoria.

---

## 8. Flujo de Eventos (preparación para Kafka)

Aunque en el monolito los módulos se comunican via Spring ApplicationEvents, los eventos están diseñados con los mismos contratos que usarán los topics de Kafka en la migración.

| Evento | Origen | Destino | Datos clave |
|---|---|---|---|
| `SessionOpened` | `session` | `catalog` | sessionId, tableId |
| `OrderItemAdded` | `session` | `kitchen` | sessionId, tableNumber, item, participantName |
| `KitchenItemUpdated` | `kitchen` | `session` | sessionId, itemId, newStatus |
| `BillingRequested` | `session` | `billing` | sessionId, items[], participants[] |
| `PaymentCompleted` | `billing` | `session`, `catalog` | sessionId, tableId, billId |

**Cuando se extraigan microservicios:** cada `ApplicationEvent` interno se reemplaza por un produce/consume de Kafka con el mismo payload. El contrato no cambia, solo el transporte.

---

## 9. Frontends

### React Web — 3 interfaces

**Admin Dashboard**
- CRUD de menú (ítems, categorías, precios, subida de imágenes a MinIO)
- Configuración de mesas y capacidades
- Reportes de sesiones y ventas

**Waiter App**
- Lobby de mesas en tiempo real (disponible / ocupada / pendiente de pago)
- Crear sesión → definir aforo → mostrar QR
- Ajustar aforo (agregar participante)
- Ver pedido consolidado de la mesa
- Solicitar cuenta → elegir división → procesar pago físico

**Kitchen Display**
- Vista full-screen de cola de pedidos en tiempo real
- Ítems agrupados por mesa con nombre del participante
- Botones de estado: Pendiente → Preparando → Listo
- Rol kitchen con sesión persistente (no requiere login frecuente)

### React Native — Customer App

| Pantalla | Función |
|---|---|
| QR Scanner | Escanea y se une a la sesión |
| Menú | Navega categorías, ve precios e imágenes |
| Carrito compartido | Ve en tiempo real todos los pedidos de la mesa |
| Mis pedidos | Vista filtrada de sus propios ítems y estados |
| Cuenta | Total, elección de división, pago digital |

---

## 10. Infraestructura Docker (desarrollo local)

```yaml
# Servicios en docker-compose
services:
  postgres:     PostgreSQL 16
  mongodb:      MongoDB 7
  minio:        MinIO (object storage)
  zookeeper:    Zookeeper (para Kafka futuro)
  kafka:        Kafka (puede activarse cuando sea necesario)
  app:          Spring Boot (monolito modular)
```

En desarrollo local, Kafka puede desactivarse — los eventos corren como Spring ApplicationEvents. Se activa cuando se inicia la migración a microservicios.

---

## 11. Autenticación y Seguridad

- JWT firmado con clave secreta (HS256 en monolito, RS256 al migrar a microservicios)
- El API Gateway (Nginx o Spring Cloud Gateway) valida JWT antes de rutear
- Roles verificados a nivel de endpoint con Spring Security
- QR de sesión: JWT de vida corta (ej. 15 minutos) firmado con sessionId + maxParticipants

---

## 12. Visión de Migración a Microservicios

Cuando el producto esté validado y el equipo crezca, cada módulo se extrae en este orden recomendado (de menor a mayor dependencia):

1. `kitchen` — el menos acoplado, primer candidato
2. `identity` — auth centralizado, extracción limpia
3. `catalog` — solo lectura para el resto del sistema
4. `billing` — necesita contrato claro con session antes de separar
5. `session` — el núcleo, se migra último

En ese punto, los `ApplicationEvent` internos se reemplazan por topics de Kafka con los mismos contratos ya definidos en la sección 8.

---

## 13. Stack Tecnológico Completo

| Capa | Tecnología |
|---|---|
| Backend | Java 17, Spring Boot 3.5, Spring Security, Spring Data JPA, Spring Data MongoDB, Spring WebSocket |
| Mensajería (futuro) | Apache Kafka |
| Bases de datos | PostgreSQL 16, MongoDB 7 |
| Almacenamiento | MinIO |
| Frontend web | React, Vite, Tailwind CSS |
| Mobile | React Native (Expo) |
| Infraestructura | Docker, Docker Compose |
| Auth | JWT (HS256) |
| WebSocket | STOMP sobre WebSocket |
