# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** report 398 — **EMB-FEAT-HUB: Ember Hub waiter-managed seats**. `CreateSessionRequest.seatNames` seeds name-only `Participant` rows (`userId == null`, auto-named "Asiento N"); `POST`/`PATCH`/`DELETE /sessions/{id}/participants…` add/rename/remove them (WAITER, assigned-waiter, OPEN; rename blocked once a non-voided bill exists, cascades `OrderItem`/`SessionActivity` names; remove reuses leave semantics); `ParticipantRenamed` event on both WS topics; null-safety sweep of participant matching + `LoyaltyAccountJoinListener` null-`userId` guard. Frontend forks on `isHubBuild` (`BASE_URL !== '/'`): customer subsystem stripped from the router, assign-table modal collects seat names (no QR), seat controls on the table detail, loyalty admin UI hidden. **No DB migration.** Branch `spec/hub-waiter-seats`. Spec/plan `…/2026-09-07-hub-waiter-managed-seats*`.
- **Current Active Task:** none. EMB-FEAT-HUB done, PR pending. Next candidates: **F-21** hardcoded creds; **HUB-03 T10** manual Windows verification.
- **Predecessor context:** guest-join PR #97 merged (`7dba69fe`, report 397); Live QA findings **Q1–Q6** merged (PRs #88/#89/#91–#95, reports 387–395); menu-responsive fix on `main` (report 396). Bug X + Bug Y confirmed fixed; Hub confirmed working on a real PC.
- **System Health:** backend `./mvnw test` **1195/1195**; frontend `build` + `lint` clean (16 pre-existing warnings), `test:run` **115/115**.
- **⚠ Prod Flyway is NOT baselined** — `V7`/`V8`/`V9` run automatically on the next tagged backend release. Only the *local dev* DB is baselined at v15, so migrations ≤ 15 are skipped there → add new columns by hand for local dev. Never pre-run migration DDL on prod (a ~15 min outage on 2026-09-06 came from doing exactly that on `V7`).
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

### EMB-FEAT-HUB — Ember Hub: waiter-managed seats — DONE, report 398, PR pending
- [x] Strip the customer flow from the Hub build (`isHubBuild` = `import.meta.env.BASE_URL !== '/'`); waiter opens a table with N name-only seats (`Participant.userId == null`, auto-named "Asiento N"), renames/adds/removes them, adds items per seat via the existing `waiter-items` path, split billing works unchanged. No DB migration. Branch `spec/hub-waiter-seats` (off `main` post-#97).
  - [x] T1 seat seeding (`CreateSessionRequest.seatNames` + `createSession` 4-arg; position-based auto-name w/ collision bump)
  - [x] T2 null-safety sweep (8 `Objects.equals` sites + controller) + `LoyaltyAccountJoinListener` null-`userId` guard
  - [x] T3 `ParticipantRenamed` event → session + waiter WS topics
  - [x] T4 `POST /sessions/{id}/participants` (add seat, lowest-free "Asiento N")
  - [x] T5 `PATCH` rename (bill-blocked 409, cascades item/activity names) + `DELETE …/{name}` remove (leave semantics) + `HubSeatFlowIntegrationTest`
  - [x] T6 `lib/isHubBuild.ts`, `App.tsx` lazy customer pages + `!isHubBuild` route gate, `Login.tsx` → shared module
  - [x] T7 `ParticipantsQrModal` Hub branch (seat-name inputs, no QR) + `createSession` 3rd arg + navigate to `/waiter/tables/{id}`
  - [x] T8 `SeatFormModal` (add/rename/remove-confirm) + `TableInformation` seat controls + API + `participant.name` key + WS invalidation
  - [x] T9 hide loyalty admin UI in the Hub build (`SettingsBar`, `Settings.renderContent`, `GlobalSearchResults`)
  - [x] T10 i18n ES/EN, report 398, PROGRESS, squash, PR
- Known: `HubSeatFlowIntegrationTest` skips the bill-blocks-rename 409 (billing precondition not worth satisfying in that test); covered by `renameSeat_billExists_*` unit/slice tests.

### Guest table-join (Cloud) — DONE, report 397, PR #97 merged
- [x] T1 `GuestNameGenerator` · [x] T2 `V9` + `User.guest` + `GuestUserService` · [x] T3 `ParticipantJoined.guest` + loyalty guards · [x] T4 `POST /sessions/join-as-guest` · [x] T5 frontend "Entrar como invitado" on `/menu/join` · [x] T6 report + squash + PR.
- Follow-up not done: public **code-entry-as-guest** page (5-digit code without an account). The `JoinTableModal` guest CTA the plan listed was dropped — that modal is auth-gated.

### Next up (spec/plan pending)
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
- [x] **Guest table-join (Cloud)** — report 397, PR #97.
- [x] **EMB-FEAT-HUB — Hub waiter-managed seats** — name-only seats + 3 WAITER endpoints + `ParticipantRenamed` + null sweep + loyalty guard; frontend `isHubBuild` fork (customer subsystem stripped, seat-name assign modal, seat controls, loyalty UI hidden). Report 398, PR pending.
- [x] Prod backend deployed to `v0.2.1` (Q1–Q6 backend + platform console). Reports 387/388, deploy incident recovered.
