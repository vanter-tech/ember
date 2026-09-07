# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** report 397 — **guest table-join (Cloud)**. `POST /sessions/join-as-guest` mints a throwaway `guest` `User` (gated on a valid open table, `@Transactional` rollback) then reuses the existing join internals; loyalty/visits suppressed for guests (`ParticipantJoined.guest` + listener guards); `V9` adds `users.guest`; frontend "Entrar como invitado" on `/menu/join`. Branch `spec/guest-join`, PR pending. Spec/plan `…/2026-09-06-guest-join*`.
- **Current Active Task:** none. Guest-join done (PR pending merge — `V9` applies automatically on the next tagged backend release). Next candidates: **Ember Hub waiter-managed seats** (own spec/plan, approved direction), **F-21** hardcoded creds. See below.
- **Predecessor context:** Live QA findings **Q1–Q6** merged (PRs #88/#89/#91–#95, reports 387–395); menu-responsive fix pushed direct to `main` (report 396). Bug X + Bug Y confirmed fixed; Hub confirmed working on a real PC.
- **System Health:** backend `./mvnw test` **1167/1167**; frontend `build` + `lint` clean (16 pre-existing warnings), `test:run` **104/104**.
- **⚠ Prod Flyway is NOT baselined** — `V7`/`V8` (and the upcoming `V9`) run automatically on the next tagged backend release. Only the *local dev* DB is baselined at v15, so migrations ≤ 15 are skipped there → add new columns by hand for local dev. Never pre-run migration DDL on prod (a ~15 min outage on 2026-09-06 came from doing exactly that on `V7`).
- **Prod deploy:** tag `v*` on `main` → `backend-image.yml` builds the image → `./deploy/deploy.sh <tag>` from Cloud Shell (pure gcloud/IAP). Frontend auto-deploys from `main` via Cloudflare Pages. Backend currently on `v0.2.1`.

## Active Context & Recent Decisions
- Monolith at `ember/`: Java 17 + Spring Boot 3.5.14 / React 19 + TS + pnpm. Event bus is Spring `ApplicationEventPublisher`/`@EventListener` only — do **not** reintroduce Kafka (dependency vestigial).
- **Tenant isolation:** `@TenantId` discriminator on catalog/billing/settings/restaurant/kitchen entities. `User` and `Session` are deliberately excluded (untenanted lookups before a customer JWT is tenant-bound). Customers become tenant-bound only on table-join (`SessionService.bindResolvedTenant`). Every `@DataJpaTest` must `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)`.
- **WebSocket multi-endpoint gotcha (reports 193-194):** all `WebSocketMessageBrokerConfigurer` beans merge onto one broker/channel; a 2nd `enableSimpleBroker(...)` silently replaces the 1st and its interceptor runs on every endpoint unless handshake-tagged. Only `WebSocketEndpointIsolationTest` catches it.
- **Digital payments are a STUB** (`gatewayRef = "STUB-..."`) — no real gateway. Biggest revenue gap; blocked on GATEWAY-01 (provider decision).
- **Commit/PR attribution:** zero Claude attribution in commit messages **or** PR descriptions (no `Co-Authored-By`, no `Claude-Session`, no "Generated with Claude Code" badge/URL). User is sole author — reaffirmed 2026-09-06 after stripping it from 16 PRs.
- Analytics money semantics: revenue = CONFIRMED `Payment` sums (never bill totals/PENDING); AOV = PAID-`Bill` avg; live counts ignore the query window.
- Missing shadcn primitives are built from the installed unified `radix-ui` export (`data-slot` convention), not a new dep.

## Task Queue Status

### Guest table-join (Cloud) — DONE, report 397, PR pending
- [x] T1 `GuestNameGenerator` · [x] T2 `V9` + `User.guest` + `GuestUserService` · [x] T3 `ParticipantJoined.guest` + loyalty guards · [x] T4 `POST /sessions/join-as-guest` · [x] T5 frontend "Entrar como invitado" on `/menu/join` · [x] T6 report + squash + PR.
- Follow-up not done: public **code-entry-as-guest** page (5-digit code without an account). The `JoinTableModal` guest CTA the plan listed was dropped — that modal is auth-gated.

### Next up (spec/plan pending)
- [ ] **Ember Hub — waiter-managed seats.** Strip the customer flow from the Hub build; waiter assigns table + participant count, names each seat (name-only `Participant`, `userId` null), adds items per seat via the existing `addWaiterItem` (already carries `participantName`), split billing works as-is. Own spec + plan. Approved direction, not started.
- [ ] **F-21 — hardcoded credentials** in `PortableDatabaseBootstrap`/`PortableMinioBootstrap`: generate random creds at first boot, persist in `hub.env`, read from env. Own small task.

### Open / deferred
- [ ] **EMB-GATEWAY** — real payment gateway. Blocked on GATEWAY-01 (certified provider for Nicaragua). GATEWAY-02..06 open.
- [ ] **HUB-03 T10** — manual Windows verification (clean install, LAN 2nd PC, license picker, upgrade-in-place, uninstall-keep, boot errors) → `reports/382-…`. Spec/plan `…/2026-09-05-hub-installer*`.
- [ ] **Hub v2** — Tauri/webview shell reusing `frontend/`'s design. Needs its own spec/plan.
- [ ] **HPD-21** restore test (GCS dump → throwaway `pg_restore`, document in `deploy/RUNBOOK.md`); **HPD-22** E2E prod walkthrough (2 devices).
- [ ] **Bug Y** — live retest with two diners now that `v0.2.1` cleared the deploy skew (marked fixed by the user, no formal retest logged).
- [ ] **LSEO** — content (blog / keyword articles / case studies), conversion (contact/demo form, WhatsApp, hero video, retargeting), off-page, minor JSON-LD. LSEO-04 Google Business Profile.
- [ ] **Security debt:** F-15 (Hub activation returns `adminPasswordHash` — redesign contract + migration path for installed Hubs); F-24 (print-agent key plaintext on disk); F-10/E-23 (PIN-login enumeration oracle — accepted, needs a product decision); F-22 (secret in git history — accepted, rotate if revisited).

### Done (collapsed)
- [x] Core platform, EMB-PRINT, EMB-MOD, EMB-INV, restaurant onboarding, Ember Hub HUB-01, license heartbeat, EMBER-FIX, HPD-01..20, Landing SEO technical base — reports 1–329.
- [x] **FIX-QA** (22/23 live findings) — reports 361-364 (E-23 deferred).
- [x] **Platform console improvements A–D** — `updateRole` guard, retire/soft-delete tenants, Hub liveness/heartbeats in the console, console redesign + `GET /platform/stats`. Reports 383–385, PRs #80/#81/#82.
- [x] **Real-time bill bug cluster** — Bug X (EQUAL_PARTS divide-by-zero) + `@Transactional` handler. Reports 387-388, PRs #85/#86.
- [x] **Live QA findings Q1–Q6** — Q1a stale digital intent, Q1b loyalty reversal on refund, Q2 customer tax breakdown, Q3 `/menu/join` page, Q4 in-app QR scanner, Q5 print-agent setup docs, Q6 delete buttons. Reports 389–395, PRs #88/#89/#91–#95.
- [x] **Menu responsive** — report 396.
- [x] Prod backend deployed to `v0.2.1` (Q1–Q6 backend + platform console). Reports 387/388, deploy incident recovered.
