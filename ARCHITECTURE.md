# Ember — System Architecture

Ember is a multi-tenant, modular-monolith restaurant management platform. This
document shows how the deployable systems connect, and how a request/event
flows through the backend during the core order lifecycle.

## 1. System Connection Diagram

```mermaid
flowchart TD
    subgraph Clients["Clients"]
        SPA["React SPA (frontend/)\nrole-routed: admin · waiter · kitchen · customer · console · auth"]
        Landing["Landing site (landing/)\nstandalone Astro, no backend calls"]
    end

    SPA -->|"REST (TanStack Query, JWT bearer)"| API
    SPA <-->|"STOMP over SockJS, /ws\nJWT via JwtChannelInterceptor"| Broker

    subgraph Backend["Backend — Spring Boot monolith"]
        API["REST Controllers"]
        Broker["STOMP simple broker (in-memory)\n/topic/session/{id} · /topic/kitchen/{tenantId}"]

        subgraph Modules["Modules"]
            identity["identity"]
            catalog["catalog"]
            session["session"]
            kitchen["kitchen"]
            billing["billing"]
            restaurant["restaurant"]
            settings["settings"]
            analytics["analytics"]
            platform["platform"]
        end

        EventBus["Internal event bus\nApplicationEventPublisher / @EventListener\n(in-process — no message broker)"]

        API --> Modules
        Modules <--> EventBus
        EventBus --> Broker
    end

    identity --> Postgres[("PostgreSQL\nidentity · catalog · billing\nsettings · restaurant")]
    catalog --> Postgres
    billing --> Postgres
    settings --> Postgres
    restaurant --> Postgres
    platform --> Postgres

    session --> Mongo[("MongoDB\nsession · kitchen\n(embedded documents)")]
    kitchen --> Mongo

    analytics -.->|"reads across billing/session/catalog\nowns no persistence itself"| billing
    analytics -.-> session
    analytics -.-> catalog
```

Notes:
- The frontend is **one** React SPA; admin/waiter/kitchen/customer/console are
  routed sections of it, not separate apps.
- `landing/` is a fully standalone Astro site with no runtime dependency on
  the backend (its contact form makes no API call).
- PostgreSQL uses JPA discriminator-based multi-tenancy
  (`TenantContextHolder` / `TenantIdentifierResolver`); MongoDB documents
  (`Session`, `KitchenOrder`) carry `tenantId` directly, with every finder
  routed tenant-first.
- There is no Kafka/RabbitMQ. Cross-module communication inside the backend
  is 100% synchronous, in-process `ApplicationEventPublisher` /
  `@EventListener`.
- The STOMP broker is an in-memory simple broker living inside the same
  backend process — not a separate infrastructure component.

## 2. Order Lifecycle — Sequence Diagram

Traced directly from the event wiring in `SessionService`, `KitchenService`,
`PaymentService`, and the `*WebSocketListener` / `*EventListener` classes.

```mermaid
sequenceDiagram
    actor Customer
    actor Waiter
    actor KDS as Kitchen Display
    participant SessionM as session module
    participant KitchenM as kitchen module
    participant BillingM as billing module
    participant Mongo as MongoDB
    participant Postgres as PostgreSQL

    Customer->>SessionM: join table (QR / code)
    SessionM-->>Mongo: persist participant
    SessionM-)Customer: ParticipantJoined → /topic/session/{id}

    Customer->>SessionM: add item to cart
    SessionM-)Customer: ItemAdded → /topic/session/{id}

    Customer->>SessionM: send items to kitchen
    SessionM-)KitchenM: ItemSent + KitchenItemsConfirmed
    KitchenM-->>Mongo: create KitchenOrder
    KitchenM-)KDS: KitchenItemsConfirmed → /topic/kitchen/{tenantId}

    KDS->>KitchenM: advance status\n(PENDING→PREPARING→READY→DELIVERED)
    KitchenM-)KDS: KitchenItemUpdated → /topic/kitchen/{tenantId}
    KitchenM-)SessionM: KitchenItemUpdated
    SessionM-)Customer: item status → /topic/session/{id}

    Waiter->>BillingM: request bill
    BillingM-->>Postgres: calculateBill (BillingRequested)

    Waiter->>BillingM: process payment
    BillingM-->>Postgres: PaymentCompleted
    BillingM-)SessionM: PaymentCompleted → closeSession()
    SessionM-)KitchenM: SessionClosed → retire remaining orders
    SessionM-)Customer: SessionClosed → /topic/session/{id}
```

## 3. Data Stores

| Store | Owning modules | Notes |
|---|---|---|
| PostgreSQL | identity, catalog, billing, settings, restaurant, platform | JPA, discriminator multi-tenancy via `@TenantId`; `platform` tables (`PlatformOperator`, `PlatformAuditLog`) have no tenant FK by design |
| MongoDB | session, kitchen | Document models with embedded participants / order items; no replica set — fail-fast validation instead of `@Transactional` |
