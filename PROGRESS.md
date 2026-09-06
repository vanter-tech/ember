# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** report 368 — admin section sidebars (Settings / Inventory / Cash Register) were a full-width vertical stack above the content on `<md`; each `*Bar` now renders a horizontal `overflow-x-auto` pill strip on mobile (`flex md:hidden`) and keeps the existing `w-64` vertical nav on `hidden md:flex`. SettingsBar mobile strip is driven by a new flat `SETTINGS_SECTIONS` array. Container gap `gap-8` → `gap-4 md:gap-8`. Committed to `main` (not pushed).
- **Predecessor Task:** report 367 (SaaS login mobile gutter + Quick Login PIN/password toggle, committed to `main`).
- **Current Active Task:** none.
- **System Health:** backend `./mvnw test` last verified 1044/1044 (FIX-QA pass, report 364). Frontend `pnpm run build` + `lint` clean and `pnpm run test:run` 78/78 pass (report 368).

## Active Context & Recent Decisions
- Monolith at `ember/`: Java 17 + Spring Boot 3.5.14 / React 19 + TS + pnpm. Every module (`identity`/`catalog`/`billing`/`settings`/`restaurant`/`session`/`kitchen`) is on Postgres/JPA; event bus is Spring `ApplicationEventPublisher`/`@EventListener` only — do not reintroduce Kafka (dependency is vestigial, see root `CLAUDE.md`).
- **Tenant isolation model:** `@TenantId` (Hibernate discriminator) on `Category`/`MenuItem`/`Bill`/`BillSplit`/`Payment`/`DiningTables`/`RestaurantSettings`/`KitchenOrder`. `User` and `Session` are deliberately excluded — both need untenanted lookups before a customer's JWT is tenant-bound (`findByJoinCodeAndStatus`, login/register). **Customers are not tenant-bound** until they join a table (`SessionService.bindResolvedTenant` is the one non-filter place allowed to touch `TenantContextHolder`). `@DataJpaTest` picks up every `@TenantId` entity project-wide regardless of package — always `@Import(TenantIdentifierResolver.class)`, and use `@Transactional(propagation = NOT_SUPPORTED)` whenever the test binds tenant itself (see `AbstractTenantIsolationTest`).
- **WebSocket multi-endpoint gotcha (reports 193-194):** Spring merges ALL `WebSocketMessageBrokerConfigurer` beans onto one shared broker/channel — a second config's `enableSimpleBroker(...)` call SILENTLY REPLACES the first's registration, and its channel interceptor runs on every endpoint's frames unless handshake-tagged. Any future 2nd/3rd STOMP endpoint (like `/ws/print-agent`) needs handshake-time session tagging for interceptor scoping AND all broker prefixes registered from the ONE owning config. No unit test catches this — only a real multi-client STOMP integration test (`WebSocketEndpointIsolationTest`) does.
- Flyway: `V1__baseline_consolidated.sql` is the schema root (V2-V15 archived, not scanned); prod runs `ddl-auto=validate`. `ddl-auto=update` in dev can silently no-op a `NOT NULL` column add on a non-empty table — check boot logs, don't trust a clean boot.
- Analytics money semantics (don't redefine ad hoc): revenue = CONFIRMED `Payment` sums (never bill totals/PENDING); AOV = PAID-`Bill` avg; `activeSessions`/`activeTableCount` are always LIVE counts that ignore the query window.
- Missing shadcn primitives (`checkbox.tsx`, `radio-group.tsx`) are built from the installed unified `radix-ui` package's export, `data-slot` convention — not a new dependency. Repeat this pattern for any future missing primitive.
- Recurring lesson across MOD-02/03 and EMB-i18N-08: grep live code for every call site of a changed signature/pattern — a plan's stated file list has repeatedly missed a real caller (test mocks especially).
- **Digital payments are a stub** (`PaymentService.initiateDigitalPayment` returns `gatewayRef = "STUB-..."`) — no real gateway is wired. This is the platform's biggest revenue gap; blocked on `GATEWAY-01` (provider decision).
- Ember Hub license heartbeat (`feat/hub-license-heartbeat`) is merged to `main` (PR #60) — code-complete; whether the manual 2-process smoke test was actually run before merge is unconfirmed, worth a live check before relying on it.

## Task Queue Status
- [x] **Core platform** (multi-tenancy, catalog, session/cart, KDS, waiter+caja, refunds/voids, physical payments, staff, admin analytics, loyalty, platform console, Astro landing, i18n ES/EN) — reports 1-181.
- [x] **EMB-PRINT** (ESC-POS hardware bridge, tenant-isolated print-agent WS) — complete except PRINT-07 (real printer test, no hardware available). Report 180.
- [ ] **EMB-GATEWAY** — real payment gateway. Blocked on GATEWAY-01 (confirm certified provider(s) for Nicaragua). GATEWAY-02..06 (adapter interface, implementation, webhooks, reconciliation, wire existing settings tab) all open.
- [x] **EMB-MOD** (reusable modifier groups, cart/KDS/ticket propagation) — complete. Reports 182-187.
- [x] **EMB-INV** (per-item stock, low-stock alerts, auto-86) — complete. Reports 188-192.
- [x] **Restaurant onboarding** (admin wizard + waiter tour) — complete. Reports 202-213.
- [x] **Ember Hub HUB-01** (portable Postgres/MinIO bootstrap, RSA license, hardware fingerprint, grace period, dashboard launcher, bundled frontend, license-activation) — complete. Reports 223-258.
  - [ ] Hub v2: Tauri/webview shell reusing `frontend/`'s design — not started, needs its own spec/plan.
  - [ ] Hub: Windows service auto-start (`sc.exe`/SCM recovery) — not started, depends on the installer below.
  - [ ] Hub: `jpackage`/`jlink` embedded-JRE `.exe` installer — not started. **Until this + the service exist, Hub isn't installable by a non-technical customer.**
- [x] **Ember Hub license heartbeat** (HEARTBEAT-01..07, cloud + Hub-side, suspended-grace enforcement) — merged to `main` via PR #60. Reports 265-271.
- [x] **EMBER-FIX** (cash-shift expiry, forced daily close, sentinel modals) — complete, merged to `main` (`78f5fe9`). Report 259.
- [x] **Hosted Production Deployment** HPD-01..20 (GCP VM, Cloudflare DNS/WAF/Worker, GCS media+backups, monitoring, uptime check) — complete. Reports 272-286, 322-329, PR #76.
  - [ ] HPD-21: restore test — latest GCS dump → throwaway `pg_restore`, document the real-disaster procedure in `deploy/RUNBOOK.md`.
  - [ ] HPD-22: E2E product walkthrough on `app.ember.vanter.net` (2 devices, full order flow) + close-out.
- [x] **Landing SEO — technical base** (LSEO-01/02/03/05: Search Console, Bing, prod CTAs, PageSpeed/Rich-Results/404) — done.
  - [ ] LSEO-04: Google Business Profile for Vanter.
  - [ ] LSEO-06..08: content (blog/recursos, 8-15 keyword articles, case studies) — the real traffic lever, not yet started.
  - [ ] LSEO-09..13: conversion (real contact/demo form, WhatsApp button, hero screencast video, retargeting pixel, social proof near CTA).
  - [ ] LSEO-14..17: authority/off-page (software directories, Product Hunt, social profiles, local partnerships).
  - [ ] LSEO-18: minor JSON-LD tweaks (`BreadcrumbList`, real `AggregateOffer`, `VideoObject`, `sameAs`) — do on request.
- [x] **Payment-flow bug cluster** (bill fetch endpoint, block removing sent items, settle-partial-and-close, leave-table/reject-2nd-session) — complete. Reports 317-320.
- [x] **FIX-QA** (22 of 23 live QA findings across all 4 roles) — complete except E-23 (PIN-login enumeration oracle, deliberately deferred — needs a product decision, fighting it breaks the "no PIN set" UX). Reports 361-364.
- [ ] **Security/hardening debt — surfaced 2026-09-04, none yet has a spec/plan:**
  - [ ] F-15: Ember Hub activation endpoint returns `adminPasswordHash` in the response — redesign the activation contract + a migration path for already-installed Hub instances.
  - [ ] F-21: hardcoded credentials literal in `PortableDatabaseBootstrap`/`PortableMinioBootstrap`'s process-launch code — needs the same installed-instance migration path as F-15; do together, same bootstrap code.
  - [ ] F-24: print-agent API key stored in plaintext on disk — deprioritized under F-15/F-21, no plan yet.
  - [ ] F-10/E-23: PIN-login account-enumeration oracle — accepted as-is; only revisit with an explicit product decision.
  - [ ] F-22: a secret is present in git history — accepted as-is; rotate the value if ever revisited, do not rewrite history without explicit approval.
