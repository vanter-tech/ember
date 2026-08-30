<div align="center">

# 🔥 Ember

**A multi-tenant, real-time restaurant management platform by [Vanter](https://ember.vanter.net).**

From the diner scanning a QR code to the kitchen display, the waiter's floor view, billing,
and the admin analytics dashboard — one modular monolith runs the whole service.

[![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square&logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-19-61DAFB?style=flat-square&logo=react&logoColor=white)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6?style=flat-square&logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-4-38BDF8?style=flat-square&logo=tailwindcss&logoColor=white)](https://tailwindcss.com/)
[![Astro](https://img.shields.io/badge/Astro-7-BC52EE?style=flat-square&logo=astro&logoColor=white)](https://astro.build/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)

</div>

---

## ✨ What Ember does

Ember is built as a **modular monolith**: a single Spring Boot deployable split into
cohesive modules (`identity`, `catalog`, `session`, `kitchen`, `billing`, `analytics`,
`inventory`, `loyalty`, `platform`, …) that talk to each other through **synchronous
in-process events** (`ApplicationEventPublisher` / `@EventListener`) — no message broker.
Every tenant is isolated at the data layer and served from the same runtime.

### By role

| Role | Highlights |
| --- | --- |
| 🧑‍🤝‍🧑 **Customer** | Join a table by QR scan or a 5-character join code. A **live collaborative cart** — every diner sees everyone's items in real time over WebSocket/STOMP, and each person places and confirms their own order. Leave the table, resume on re-login. |
| 🧑‍🍳 **Kitchen (KDS)** | Real-time order queue with a strict status machine: `PENDING → PREPARING → READY → DELIVERED`. |
| 🧑‍💼 **Waiter** | Assign and close table sessions, add items manually, split and settle bills (including redistributing a departing diner's share), and run the end-of-shift cash-register close (*cierre de caja*). |
| 🛠️ **Admin** | Catalog management (categories, items, pricing), live floor monitoring, business analytics by day / week / month / year, employee and role management, and per-tenant configuration. |

---

## 🗂️ Repository layout

Ember is a monorepo. Each top-level directory is an independently buildable piece:

| Path | Stack | What it is |
| --- | --- | --- |
| [`backend/`](backend) | Java 17 · Spring Boot 3.5 · Spring Security (JWT) · JPA + Flyway · WebSocket/STOMP · springdoc OpenAPI · MinIO · Actuator + Micrometer/Prometheus | The core API and business logic. Package root `com.vanter.ember`; API served under `/v1/`. |
| [`frontend/`](frontend) | React 19 · TypeScript · Vite 8 · Tailwind 4 · Zustand 5 · TanStack Query 5 · shadcn/ui (Radix) · STOMP/SockJS | The staff + customer SPA (waiter floor, KDS, admin panel, customer cart). |
| [`landing/`](landing) | Astro 7 · React islands · Tailwind 4 | Marketing site `ember.vanter.net`, Spanish-first with English at `/en/` (hreflang, sitemap, JSON-LD). |
| [`ember-hub/`](ember-hub) | PowerShell build scripts | Packaging for the **self-hosted "Hub" SKU** — bakes the frontend and license config into an on-prem installer. |
| [`printing-agent/`](printing-agent) | Java · Maven | Standalone agent that bridges the platform to local receipt / ticket printers. |
| [`deploy/`](deploy) | Docker Compose · shell | Hosted-production runbook, `docker-compose.prod.yml`, monitoring and backup configuration. |
| [`docker-compose.yml`](docker-compose.yml) | Docker Compose | Local infra stack: PostgreSQL 16, pgAdmin, MinIO, Prometheus, Grafana (+ optional app / frontend images). |

---

## 🚀 Quick start

### Prerequisites

- **Java 17** (JDK)
- **Node.js 22.12+** and **pnpm** (`npm i -g pnpm`)
- **Docker** (for Postgres, MinIO, and the rest of the local stack)

### 1. Configuration

```bash
cp .env.example .env
```

Fill in the values that have **no in-code fallback** — the backend refuses to boot without them:
`SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `PLATFORM_JWT_SECRET`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`.
The backend imports `.env` automatically, and `docker-compose` feeds it to the `app` service.

### 2. Infrastructure

```bash
docker compose up -d postgres minio
```

### 3. Backend — `backend/`

```bash
cd backend
./mvnw spring-boot:run
```

API → `http://localhost:8080/v1/` · Swagger UI → `http://localhost:8080/v1/swagger-ui.html`
· health → `http://localhost:8080/v1/actuator/health`

### 4. Frontend — `frontend/`

```bash
cd frontend
pnpm install
pnpm run dev
```

App → `http://localhost:5173`

### 5. Landing (optional) — `landing/`

```bash
cd landing
pnpm install
pnpm run dev
```

Site → `http://localhost:4321`

---

## 🔌 Local ports

| Service | Port | Notes |
| --- | --- | --- |
| Backend API | `8080` | base path `/v1/` |
| Frontend (Vite) | `5173` | |
| Landing (Astro) | `4321` | |
| PostgreSQL | `5432` | db `ember` / user `ember` |
| pgAdmin | `5050` | |
| MinIO | `9000` / `9001` | API / console |
| Prometheus | `9090` | |
| Grafana | `3000` | |

---

## 🧪 Testing & quality gates

| Area | Command |
| --- | --- |
| Backend — all tests | `cd backend && ./mvnw test` |
| Backend — one test | `./mvnw test -Dtest=E2EOrderFlowTest` |
| Backend — style | Checkstyle runs in the Maven build |
| Frontend — type-check + build | `cd frontend && pnpm run build` (`tsc -b && vite build`) |
| Frontend — lint | `pnpm run lint` |
| Frontend — unit tests | `pnpm run test` (Vitest) |

CI (GitHub Actions) runs backend + frontend lint on every push and builds the backend image.

**The build policy is zero-tolerance:** no TypeScript compilation errors, no lint failures.

---

## 🏗️ Architecture notes

- **Persistence:** PostgreSQL via JPA for every module. `session` and `kitchen` were migrated off
  MongoDB; their embedded collections (participants, order items) now live in JSON columns
  (`@JdbcTypeCode(SqlTypes.JSON)`). Schema is managed with **Flyway** migrations.
- **Concurrency:** optimistic locking (`@Version`) on JPA entities, explicit `@Transactional`
  boundaries, and atomic race-condition prevention in billing / payments.
- **Real-time:** STOMP over WebSocket (SockJS fallback); the JWT is validated on the STOMP
  `CONNECT` frame. State is cleaned up on disconnect.
- **Media:** images are stored in MinIO (S3-compatible) and served from a public/CDN base URL.
- **Auth:** two independent JWT secrets — one for tenant users, a separate one for the
  platform / super-admin console — which is what keeps a tenant token off `/platform/**`.
- **Events:** 100% internal and synchronous. There is a Kafka dependency on the classpath from
  an earlier iteration; it is **not used or configured**.

---

## 🤝 Contributing

Development conventions — the task lifecycle, `PROGRESS.md` tracking, report format, and the
strict commit rules (scoped staging, one squashed atomic commit per task, Conventional Commits,
no AI/co-author signatures) — are defined in [`CLAUDE.md`](CLAUDE.md). Read it before opening a PR.

---

<div align="center">
<sub>Proprietary software · © Vanter · All rights reserved.</sub>
</div>
