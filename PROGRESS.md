# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** report 545 — **WAITER-ADD-ITEM-SECOND-REVIEW-PASS** (on `main`, not yet pushed) — second review round on the same modal: bigger product cards (dropped the 3-col breakpoint, taller photo/text), `cursor-pointer` added to every raw `<button>` in the file (chips, category tabs, "+", the new controls below — `Button`-component buttons already had it via `buttonVariants`, these didn't), and the bottom inline cart strip replaced by a floating "cart panel" (plain fixed `<div>`, not a second Radix Dialog — no overlay/focus-trap) opened via a new "Ver pedido" button, positioned left of the main modal, listing the active client's lines with one delete-per-line button (qty bump stays on the product "+"). TDD: RED confirmed (8 failures) against r544's component first. `pnpm run test:run` **192/192**, `build`/`lint` clean. **Not done:** live browser check of the panel's fixed-position math against a real touch viewport (same deferral as r544). **Pending (user-gated):** commit + merge, rebuild `EmberHubSetup-0.3.0.exe` again.
- **Prev (r544, merged to `main` locally 2026-09-22):** WAITER-ADD-ITEM-REVIEW-FEEDBACK — first review round: modal `6xl`, more spacing, dish photos, dedicated "+" add button (misclick guard). `pnpm run test:run` **190/190**. Hub `.exe` rebuilt after this round (before r545's changes).
- **Prev (compressed, reports 540-542 — three independent `landing/`-only tasks, merged to `main` locally 2026-09-22):** PRICING-TABLE-HONESTY (r540) — `/planes` audited against `PlanGateService`'s 6 real gates, 3 fabricated rows removed, 5 falsely-gated rows fixed. DOWNLOADS-BUTTON-ALIGNMENT (r541) — `mt-auto` fix for the Hub/agent download CTAs. HUB-PRICE-MODAL (r542) — "Ver precio" button + `<dialog>` on `LocalPlan.astro` showing Hub's list price ($490/año, $294/semestre). All `pnpm run build` clean. **Pending (user-gated):** push to `origin`. Also this session: defined Ember Hub's pricing + sales cadence — see memory `ember-hub-pricing`; paused a prod-infra hardening brainstorm before writing its spec — see memory `ember-prod-infra-hardening`.
- **Prev (compressed, reports 534-539 — all 5 security-debt items + RESTAURANT-DEPLOYMENT-MODE, merged to `main` locally 2026-09-22 and pushed to `origin/main`):** F-21 Hub Postgres/MinIO random credentials (r536); F-14 `/platform/**` per-operator RBAC (r535); F-15 Hub generates its own admin password (r537); F-17 JWT session revocation via `tokenVersion` (r538); F-10/E-23 PIN-login enumeration rate-limited + audit-logged (r539); RESTAURANT-DEPLOYMENT-MODE per-restaurant Web/Hub mode, `V15` (r534, still awaits end-to-end manual acceptance + tag `v0.3.0` rollout). Full detail in each report + Task Queue Status below.
- **Current Active Task:** none in code. r540-545 are merged to `main` locally, not yet pushed. The F-14/F-15/F-17/F-21/F-10/RESTAURANT-DEPLOYMENT-MODE batch (r534-539) is on `origin/main` (pushed 2026-09-22, bypassed 3 required status checks — **confirm CI still goes green**). Prod-infra hardening remains the next user-directed thread — see memory `ember-prod-infra-hardening`. HUB-BACKUP-RESTORE and LIVE-BUG-BATCH's hotfix gates remain pending as above — one task per context, `/clear` between.
- **Prev (compressed r401-533, full detail in `reports/` + Task Queue Status below):** LIVE-BUG-BATCH → HUB-LICENSE-HARDENING → HUB-PRINT 1-2 → AGENT-UI-HARDENING → FEAT-REAL-RECEIPT → FIX-HUB-SPA-DEEP-LINK → FIX-PRINTING-UX (r495-533); CASH-SHIFT-DENOMINATION-COUNT → PLAN-GATING-PHASE1 → ACCOUNTANT role → KDS-BULK-STATUS-UPDATE, EMBER-HUB-V2/PRINT-AGENT-V2 Tauri shells, EMB-EXPORT, EMB-PRINT-AGENT, landing pages (r401-472).
- **System Health:** backend `./mvnw test` **1528/1528** (`main`, pushed to `origin`, 2026-09-22 — unaffected by this session's frontend/landing tasks). `frontend`: `pnpm run test:run` **187/188** (1 pre-existing unrelated), `build`/`lint` clean (this report). `landing`: `pnpm run build` clean, 28 pages, live-tested in Chrome. `ember-hub/ui` last verified in the r534-539 batch.
- **⚠ Prod Flyway is NOT baselined.** Prod `flyway_schema_history` is at **v11** — `v0.2.5` deploy (2026-09-15) ran V11 clean 10→11 (widens `users_role_check` for `ACCOUNTANT`), verified live via `psql ... flyway_schema_history`, all `success=t`. `V9`/`V10`/`V11` are idempotent (`IF NOT EXISTS`); `V7`/`V8` are NOT. Only the *local dev* DB is baselined at v15, so migrations ≤ 15 are skipped there — add new columns by hand for local dev (done for `V11`/`V13` via `docker exec` this session). Never pre-run migration DDL on prod. **TODO (ops, not code):** inspect prod `flyway_schema_history` from Cloud Shell and baseline properly. `V12`/`V13` (denomination breakdown + `restaurants_status_check` widen) are code-only until this branch and `feat/cash-shift-denomination-count` both deploy — prod will run both automatically, no manual step needed there.
- **Prod deploy:** tag `v*` on `main` → `backend-image.yml` builds `ember-backend:<tag-without-v>` → `./deploy/deploy.sh <tag-without-v>` from Cloud Shell (pure gcloud/IAP; the `v` is stripped — `deploy.sh 0.2.5`, not `v0.2.5`). Frontend + landing auto-deploy from `main` via Cloudflare. Backend on **`v0.2.5`** (ACCOUNTANT role, PLAN-GATING-PHASE1, Corte Z shifts table, hourly backups; tag pushed straight from `origin/main` after PR #123 squash-merged reports 473–485, 2026-09-15). V11 applied clean.
- **Backup cadence live in prod (2026-09-15):** hourly `pg_dump` → `gs://ember-backups-ember-prod-vanter/postgres/` confirmed working (`2026-09-15T02.dump.gz`), report 485's change fully applied (was code-only until now). Found + fixed one real bug while applying it: `RUNBOOK.md`'s rebuild command was missing `-f docker-compose.prod.yml` (docker compose silently looked for the default filename and failed with "no configuration file provided") — fixed in both the doc and the live run.
- **Hub/Agent installers:** manual, no CI (`ember-hub/build-installer.ps1`, `printing-agent/build-installer.ps1`) → `deploy/publish-installer.sh <agent|hub> <version> <path>` from Cloud Shell (upload + `-latest.exe` + Cloudflare purge). Bucket `gs://ember-downloads-prod`: `EmberAgentSetup-latest.exe` live; Hub has only the stale `EmberHubSetup-0.0.1.exe` — `EmberHubSetup-0.2.4.exe` built (r437), **publish still pending**.

## Active Context & Recent Decisions
- Monolith at `ember/`: Java 17 + Spring Boot 3.5.14 / React 19 + TS + pnpm. Event bus is Spring `ApplicationEventPublisher`/`@EventListener` only — do **not** reintroduce Kafka (dependency vestigial).
- **Tenant isolation:** `@TenantId` discriminator on catalog/billing/settings/restaurant/kitchen entities. `User` and `Session` are deliberately excluded (untenanted lookups before a customer JWT is tenant-bound). Customers become tenant-bound only on table-join (`SessionService.bindResolvedTenant`). Every `@DataJpaTest` must `@Import(com.vanter.ember.config.TenantIdentifierResolver.class)`.
- **WebSocket multi-endpoint gotcha (reports 193-194):** all `WebSocketMessageBrokerConfigurer` beans merge onto one broker/channel; a 2nd `enableSimpleBroker(...)` silently replaces the 1st and its interceptor runs on every endpoint unless handshake-tagged. Only `WebSocketEndpointIsolationTest` catches it.
- **Digital payments are a STUB** (`gatewayRef = "STUB-..."`) — no real gateway. Biggest revenue gap; blocked on GATEWAY-01 (provider decision).
- **Commit/PR attribution:** zero Claude attribution in commit messages **or** PR descriptions (no `Co-Authored-By`, no `Claude-Session`, no "Generated with Claude Code" badge/URL). User is sole author — reaffirmed 2026-09-06 after stripping it from 16 PRs.
- Analytics money semantics: revenue = CONFIRMED `Payment` sums (never bill totals/PENDING); AOV = PAID-`Bill` avg; live counts ignore the query window.
- Missing shadcn primitives are built from the installed unified `radix-ui` export (`data-slot` convention), not a new dep.
- **CHECK-constraint trap (recurring):** adding a new enum constant (Java) without a matching Flyway migration widening the DB's `CHECK` constraint causes a silent-looking 500 the first time that value is actually persisted (seen with `ACCOUNTANT`/`users_role_check` → V11 fix, and again with `RestaurantStatus.DELETED`/`restaurants_status_check` → bug 7/V13). When adding an enum value backed by a `CHECK`-constrained string column, always grep the baseline/migrations for that column's `_check` constraint.

## Task Queue Status

### WAITER-ADD-ITEM-TOUCH-SPLIT-VIEW — touch-friendly clients × categorized products for `AddItemModal` — DONE, report 543 — branch `feat/waiter-add-item-touch-split-view`
- [x] Left: client chips (participants + "Mesa"), badge shows draft-cart count, tap to switch active client
- [x] Right: search + category tabs (categories derived from `MenuItemResponse.category`, no new API) + item grid
- [x] Draft cart per client, confirmed all at once — one `addWaiterItem` call per unit on confirm, same backend contract, no backend change
- [x] TDD: 12 tests, RED confirmed against the old component first
- [x] `pnpm run test:run` 187/188 (1 pre-existing unrelated), `build`/`lint` clean — push to `origin` still pending (user-gated)

### HUB-PRICE-MODAL — Ember Local card shows price + explanation in a modal — DONE, report 542 — branch `feat/hub-price-modal`
- [x] "Ver precio" button + native `<dialog>` modal (same pattern as `videos.astro`), no React island
- [x] Shows list price only ($490/año, $294/semestre) — founder discount stays private, never in the modal
- [x] Reworded `pay.note` (was "a convenir por tablets/mesas", now states the price is fixed)
- [x] Live-tested in Chrome (open/close, ES+EN); `pnpm run build` clean — push to `origin` still pending (user-gated)

### DOWNLOADS-BUTTON-ALIGNMENT — `/info/descarga` CTA buttons weren't lined up — DONE, report 541 — branch `fix/downloads-button-alignment`
- [x] `mt-auto` on the download button (was `mt-6`), same fix pattern as `PlanCards.astro`
- [x] `pnpm run build` clean — push to `origin` still pending (user-gated)

### PRICING-TABLE-HONESTY — `/planes` claims match what `PlanGateService` actually enforces — DONE, report 540 — branch `fix/pricing-table-honesty`
- [x] Audited every row/bullet against `PlanGateService`'s 6 real gates (tables/periodfilters/cashclose/roles/branding/export)
- [x] Removed 3 fabricated rows (multi-waiter limit, rooms/areas, multi-branch) — zero code backs any of them
- [x] Fixed 5 rows wrongly marked paid-tier-exclusive (floor mgmt, bill split, printing, "advanced" analytics) — actually unrestricted for every tier
- [x] Relabeled the roles row (only Kitchen/Accountant gated, not Waiter/Admin); Pro card trimmed to 3 honest bullets
- [x] `pnpm run build` clean (landing, 28 pages) — push to `origin` still pending (user-gated)
- Enterprise's SLA/integrations/account-manager bullets deliberately kept despite no code — legitimate sales-negotiated commitments, unlike a software capability like multi-branch
- Out of scope: Ember Hub's own pricing/landing section — untouched, see memory `ember-hub-pricing`
### F-10-PIN-ENUMERATION-MITIGATION — rate limit + audit logging for the PIN-login oracle — DONE, report 539 — branch `fix/pin-enumeration-rate-limit`, merged to `main` locally 2026-09-22
- [x] `RateLimitProperties.pinLoginMaxRequests` (default 5/min) — third, tighter budget on top of the shared one
- [x] `AuthRateLimiterFilter` checks it only for `/auth/login/pin`, own bucket key, doesn't touch other paths' budgets
- [x] `AuthService.loginWithPin` logs `WARN` (email + reason, never the PIN) on unknown-email/no-PIN/locked/wrong-PIN
- [x] Merge to `main` (local, 2026-09-22) — push to `origin` still pending (user-gated)
- Evaluated and rejected: removing `email` from `quickAccessStore.ts` — load-bearing for `QuickLoginModal`'s login call, not just its display
- Out of scope (deliberate, unchanged): the 401/409/423 response split itself — user-ratified product decision (2026-09-04), `QuickLoginModal` UX depends on it

### F-17-TOKEN-REVOCATION — JWT session revocation via `tokenVersion` — DONE, report 538 — branch `fix/jwt-token-revocation`, merged to `main` locally 2026-09-22
- [x] `users.token_version` (`V17`, idempotent) + `JwtService.extractTokenVersion` (`ver` claim, defaults to 0)
- [x] `AuthService.buildResponse` puts `tokenVersion` in every issued token (register/login/PIN/tenant-scoped re-issue)
- [x] `EmberUserDetails` carries live `tokenVersion`; `SecurityConfig.jwtAuthFilter` rejects a stale `ver` next to the existing `isEnabled()` check
- [x] `UserAdminService.setPin`/`clearPin`/deactivate bump `tokenVersion`; new ADMIN `POST /admin/staff/{id}/revoke-sessions`
- [x] Merge to `main` (local, 2026-09-22) — push to `origin` still pending (user-gated)
- Out of scope (deliberate, see report 538 §5): moving the JWT out of `localStorage` into an `httpOnly` cookie, shortening the 24h TTL — bigger cross-cutting change (STOMP handshake, CORS, CSRF)

### HUB-ACTIVATION-NO-PASSWORD-HASH — F-15, Hub generates its own admin password — DONE, report 537 — branch `fix/hub-activation-no-password-hash`, merged to `main` locally 2026-09-22
- [x] Cloud: `HubActivationResponse`/`HubActivationService` no longer send `adminPasswordHash`
- [x] Hub: `HubProvisioningRunner` generates + hashes a random local password itself
- [x] `FirstRunCredentialHolder` (in-memory only) shared between the embedded context and the sidecar via `SpringApplication.addInitializers`
- [x] `HubControlServer` `GET`/`DELETE /api/first-run-credentials` + `ember-hub/ui` `FirstRunCredentialsModal` (copy + two-step ack)
- [x] Merge to `main` (local, 2026-09-22) — push to `origin` still pending (user-gated)
- Known gap (stated in the UI): lost if not acknowledged before a full process restart — no "forgot password" flow yet
- Out of scope: none — this closes F-15 as originally scoped (no data migration needed, activation is one-time)

### HUB-LOCAL-CREDENTIAL-ROTATION — F-21, random Postgres/MinIO credentials + real local auth — DONE, report 536 — branch `fix/hub-local-credentials`, merged to `main` locally 2026-09-22
- [x] Rust `ensure_hub_env` generates `EMBER_HUB_POSTGRES_PASSWORD`/`EMBER_HUB_MINIO_SECRET_KEY` into `hub.env` on first run
- [x] `HubProperties`/`PortableDatabaseBootstrap`/`PortableMinioBootstrap`/`PostgresTools` thread the real credentials through (back-compat ctors preserved)
- [x] `PortableDatabaseBootstrap` forces `-A scram-sha-256` on `initdb` (was silently `trust` on Windows) + `PGPASSWORD` on `createdb`/`PostgresTools`
- [x] Verified for real against vendored Postgres 16/MinIO: rotation works, old default correctly rejected, full backup/restore round trip unaffected
- [ ] Rust side spot-check with a real `cargo build` before the next Hub installer (couldn't run in this sandbox — env issue, not a code issue)
- [x] Merge to `main` (local, 2026-09-22) — push to `origin` still pending (user-gated)
- Out of scope: rotating already-installed Hubs (their data dir already has the old password + `trust` baked in) — needs its own migration task

### PLATFORM-OPERATOR-RBAC — F-14, role-based access on `/platform/**` — DONE, report 535 — branch `fix/platform-operator-rbac`, merged to `main` locally 2026-09-22
- [x] `PlatformOperatorRole` enum + `platform_operators.role` (`V16`, backfilled `SUPER_ADMIN`)
- [x] `PlatformOperatorDetailsService` grants the real role as the Spring Security authority
- [x] `@PreAuthorize` on `PlatformRestaurantController`/`PlatformStatsController`/`PlatformAuditLogController` (`SUPER_ADMIN` for mutations, both roles for reads)
- [x] Tests proving `SUPPORT` gets 403 on mutations / 200 on reads, `SUPER_ADMIN` unchanged
- [x] Merge to `main` (local, 2026-09-22) — push to `origin` still pending (user-gated)
- Out of scope: no operator-management UI (operators are still hand-inserted in the DB); exposing `role` in the Console is a follow-up if that UI gets built

### RESTAURANT-DEPLOYMENT-MODE — per-restaurant Web (CLOUD) vs Hub mode — DONE, report 534 — branch `feat/restaurant-deployment-mode` (not pushed) — spec `docs/superpowers/specs/2026-09-20-restaurant-deployment-mode-design.md`, plan `docs/superpowers/plans/2026-09-20-restaurant-deployment-mode.md` (spec+plan committed on `spec/restaurant-deployment-mode`, not pushed). Report numbers assigned at commit time (next: 533). Rollout: cloud first (V15), then Hub.
- [x] Tasks 1-9 (all DONE) — `DeploymentMode` enum + idempotent `V15`; cloud login/PIN/JWT/STOMP/QR-join/public-slug all refuse HUB; `issueHubLicense`/heartbeat require HUB (`MIGRATED` > `SUSPENDED` > `OK`); Console create/filter/change-mode (audited); Hub `migratedSince` + 48h courtesy + `ReadOnlyModeInterceptor`; full verification on real Postgres, bumped 0.3.0. **Local dev DB: add `deployment_mode` by hand.**
- [ ] Rollout (user-gated) — manual end-to-end acceptance on a real Hub, PR (squash), tag `v0.3.0`, deploy cloud FIRST, then build/publish Hub
- Out of scope: data migration Hub↔Web (never), CSV menu importer (medium term), retention of cloud data left behind, WS SEND frames in read-only mode.

### HUB-BACKUP-RESTORE - Hub backup (USB/this-machine modal) + restore from uploaded file - plan `docs/superpowers/plans/2026-09-19-hub-backup-restore.md`, spec `docs/superpowers/specs/2026-09-14-hub-backup-restore-design.md`
Branch `feat/hub-backup-restore` off `main`. Strictly in order, ONE task per context: TDD per the plan -> verify -> report `reports/NNN-...` (509-514 expected) -> tick below -> single commit -> **finish by telling the user to run `/clear`** before the next task.
- [x] Task 1 - models, `HubVersion`, `BackupConfigStore`, `BackupArchive` (report 509)
- [x] Task 2 - `PostgresTools` + `HubBackupService` backup/list/inspect/prune/scheduled-if-due (report 510)
- [x] Task 3 - orchestrator `stopAndWait` + `restore` + real-Postgres round-trip test (report 511)
- [x] Task 4 - `BackupScheduler` + 6 `HubControlServer` routes + `EmberApplication` wiring (report 512)
- [x] Task 5 - Hub UI: types/api/`Modal`/`BackupCard` + tests (report 513)
- [x] Task 6 - mount in `Dashboard`, `VERIFY.md` items 14-19, full verification (report 514); afterwards rebuild the Hub installer + run VERIFY 14-19

### LIVE-BUG-BATCH — 7 live user bug reports (2026-09-18) + 3 Settings polish follow-ups — DONE, reports 495-504 — branch `fix/live-bug-batch-2026-09-18` off `origin/main`
- [x] Bugs 1-7 + Follow-ups 1-2 (QR join name form r495, `PARTICIPANT_LEFT` activity r496, misleading closed banner r497, Settings grid/double-card/dividers r498/r502/r503, Corte Z payments by table r499, ADMIN `/cash-shifts/current` r500, delete-restaurant 500 = `V13` + `onError` toast r501 **not yet live-verified**)
- [x] Follow-up 3 — "Exportar datos" tab gets the same real `Card` structure as every other tab — report 504, **LIVE-BUG-BATCH complete**. Ad-hoc after the batch (same branch): [x] r505 openapi regen, [x] r506 guest-join loses CUSTOMER identity, [x] r507 Hub duplicate Flyway V12 (installer rebuild pending), [x] r508 hotfix 0.2.6.1 prep (join-card padding + version)

### PRINT-AGENT-V2 — Tauri native shell — spec `docs/superpowers/specs/2026-09-12-tauri-native-shells-design.md`, plan `docs/superpowers/plans/2026-09-12-printer-agent-v2-tauri-shell.md`
Replaces printer-agent's Swing UI (`AgentDashboard`/`AgentTrayIcon`/`PairDialog`) with a Tauri+WebView2 window (Astro+React+Tailwind); Java becomes a headless sidecar exposing a new loopback-only `LocalControlServer`. Fase 1 = parity with today's Swing (incl. test-print/logs/diagnostics, already existing — corrected in spec after initial miscategorization). No `AgentRunner`/`StatusHub`/pairing/printing logic changes. Branch `spec/tauri-native-shells` off `main`. Ember Hub v2 uses the same pattern but is a **separate plan**, done after this one ships. One task per context, `/clear` between.
- [x] Task 1 — `LocalControlServer` (JDK `HttpServer`, loopback-only: `/api/status`, `/api/pair`, `/api/printers`, `/api/test-print`, `/api/diagnostics`, `/api/paths`) — report 438
- [x] Task 2 — collapse `Main` to a single headless sidecar mode (prints `PORT=<n>`), delete Swing UI classes, `jlink-modules.txt` += `jdk.httpserver` — report 439
- [x] Task 3 — `printing-agent/ui/` (Astro+React+Tailwind window content, 4 sections + component tests) — report 440
- [x] Task 4 — Tauri shell (`src-tauri/`: sidecar spawn, port discovery, crash watcher, tray, close-to-tray, autostart) — report 441
- [x] Task 5 — installer pipeline: Tauri bundler replaces Inno Setup, `build-installer.ps1` + CI `build-print-agent` updated — report 442
- [x] Task 6a — `VERIFY.md` updated for the Tauri shell (+ sidecar crash/orphan checks 11–13) — report 443
- [ ] Task 6b — run the 13-item checklist on a clean Windows machine/VM (ops action, pending)

### Landing — Ember Local + nice-to-have batch (r401-407, PRs #100/#101) — DONE except one config item
- [x] `EmberLocal.astro` home band, `LocalPlan.astro` on `/planes`, `/info/local` page, `/info/seguridad`, `/contacto` + `/gracias`, contact endpoint moved to `landing/worker/index.ts` (Worker + static assets)
- [ ] **Config owed (owner, on the `ember` Worker → Settings → Variables and Secrets):** `TURNSTILE_SECRET_KEY` + `RESEND_API_KEY` (Secret), `CONTACT_TO` (Text) — `POST /api/contact` returns 500 until set

### EMB-PRINT-AGENT — Print agent: desktop app + Windows installer — plan `docs/superpowers/plans/2026-09-08-print-agent-installer.md`
Cloud agent only (spec §4.1 Hub-local detection = separate future plan). One task per context, `/clear` between.
- [x] T1 backend — `V10` (`pairing_codes` + `discovered_printers` jsonb), `createPairingCode`/`redeemPairingCode`/`saveDiscoveredPrinters`, `POST /printing/admin/agents/{id}/pairing-code` + `/printing/agents/pair` (rate-limited) + `/printing/agents/me/discovered-printers`, `PrintAgentResponse` += `paired`/`discoveredPrinters`, `SecurityAuditTest`. Report 417. **Types regen deferred to T5** (openapi diff was ~1230 lines of pre-existing drift — out of scope for a backend commit).
- [x] T2 agent — `StatusHub` observable, `credential/{CredentialStore,DpapiCredentialStore,PlaintextCredentialStore,CredentialStores}` (JNA Crypt32, machine scope) + `jna-platform` dep, `AgentPaths` (`%ProgramData%\EmberAgent`), `AgentConfig.resolve` (credential first, `agent.properties` fallback, empty otherwise; `load(Path)` shim kept for `Main` until T4). **Closes F-24.** Report 418.
- [x] T3 agent — `DiscoveredPrinter`/`PairingException`/`PairingClient.redeem`, `WindowsPrinterEnumerator` (`Get-Printer` + `parse` + `looksLikeInkjet`), `DiscoveredPrintersClient` (best-effort). +9 tests. Report 419. Runtime wiring deferred to T4.
- [x] T4 agent — `AgentRunner` extracted from `Main` (loop unchanged + `resolve`/`UNPAIRED` re-poll + discovered-printers report on connect + `StatusHub` feed), `PrintJobHandler` nullable `StatusHub` ack-mirror, `Main` `--headless`/`--tray`, `AgentConfig.load` shim dropped; Swing `ui/{AgentDashboard,AgentTrayIcon,PairDialog}` + `DiagnosticsReport`. +4 tests (Swing = T7). Report 420.
- [x] T5 frontend — `CreateAgentModal` shows pairing code (key folded), `PrintingSettings` "Nuevo código" + Emparejado/Sin emparejar badge + `.exe` download link, `AddPrinterModal` `windowsQueueName` → `<select>` from `discoveredPrinters` + auto-`DRIVER` for inkjet, i18n ES/EN. Report 421. `backend-types.ts` hand-patched (no full `openapi` regen — ~1230 lines of unrelated drift).
- [x] T6 installer — `printing-agent/build-installer.ps1` + `installer/EmberAgent.iss` + `jlink-modules.txt` + `ember-agent.ico` (clone `ember-hub/`), `{commonstartup}` shortcut → `--tray`, `%ProgramData%\EmberAgent\`, CI `build-print-agent` job (fat jar + `jpackage` app-image; Inno `.exe` manual). Report 422. `-Stage appimage` verified on Windows/JDK17.
- [~] T7 manual verification — `printing-agent/VERIFY.md` 10-point checklist written (report 423); the clean-Windows-PC run + go/no-go is still pending (`iscc`/`.exe` build + a Java-less Windows VM — owner/ops action)

### PILOT-READINESS — gate before onboarding the first paying customer (~1 week, not more)
Rationale: architecture is sound but prod infra is early (single VM, no HA, manual deploy). These four close the gap between "demo-able" and "safe to run a real restaurant's money on". Framing to customers stays **early access / founding restaurant**, no uptime SLA, no "diner pays from phone" (gateway is a stub — EMB-GATEWAY).
- [x] **1 — Lower the RPO.** Nightly → hourly `pg_dump` to `gs://ember-backups-ember-prod-vanter` — report 485, **live in prod since 2026-09-15**, verified (`2026-09-15T02.dump.gz` uploaded). Ideal (WAL archiving/PITR) still not done. Pairs with **HPD-21** (restore test, still pending).
- [ ] **2 — Rehearse rollback once.** `./deploy/deploy.sh <previous-tag>` documented in `deploy/RUNBOOK.md` and actually run once against prod (or a clone), not improvised during an incident.
- [ ] **3 — Bus factor = 1, acknowledged.** Founder reachable + able to fix fast; a support/status channel for pilots and a basic "prod is down" response checklist.
- [ ] **4 — Privacy/Terms lawyer pass.** `landing/src/pages/privacy.astro` is thin; taking payment + handling restaurant (and possibly diner) data needs a real review. Not a pilot blocker, is a scale blocker.

### Open / deferred
- [ ] **F-21 follow-up — migrate already-installed Hubs** off the old hardcoded Postgres password + `trust` auth (connect with the known legacy default, `ALTER ROLE`, rewrite `pg_hba.conf`); needs a real portable-Postgres install to verify against, not attempted in r536. Own task.
- [ ] **EMB-GATEWAY** — real payment gateway. Blocked on GATEWAY-01 (certified provider for Nicaragua) — real blocker is **user lacks persona jurídica**, which every bank gateway requires; on hold indefinitely. GATEWAY-02..06 open.
- [ ] **HUB-BACKUP-RESTORE** - code-complete (reports 509, 513-517), see its section above; awaiting PR/merge + installer rebuild + manual VERIFY 14-19. Restore = upload file from the dashboard, USB-recommended modal, settings covered by the Postgres dump.
- [ ] **HUB-LOCAL-PRINTING** — the Hub still needs the `printing-agent` to print (same pairing flow as cloud, agent connects back to the Hub on the LAN). Design already says it shouldn't (`2026-09-08-print-agent-installer-design.md` §4.1): the Hub runs on the same PC as the printers, so it should enumerate them in-process (`WindowsPrinterEnumerator`, `GET /hub/local-printers`), fill `AddPrinterModal`'s `<select>` from it when `isHubBuild()`, dispatch jobs in-process (senders live in `printing-agent/`; share or depend on that jar) and hide the "agent" concept. Intended UX: Settings → Impresión creates a printer per role (caja/cocina) and picks the local queue directly — no agent install. **Not implemented; the planned `2026-09-08-hub-local-printer-detection.md` was never written.** Caveat: only covers printers attached to the Hub PC (network/IP printers work from anywhere); USB printers on other PCs would still need an agent. Waiters on other PCs don't pick printers — the backend dispatches by role. Next after HUB-BACKUP-RESTORE.
- [ ] **HUB-03 T10** — manual Windows verification (clean install, LAN 2nd PC, license picker, upgrade-in-place, uninstall-keep, boot errors) → `reports/382-…`. Spec/plan `…/2026-09-05-hub-installer*`.
- [ ] **EMBER-HUB-V2 / PRINT-AGENT-V2 clean-machine `VERIFY.md` runs** — both Tauri shells are code-complete; only the manual clean-Windows-machine/VM checklist runs (ops action) remain open for each.
- [ ] **HPD-21** restore test (GCS dump → throwaway `pg_restore`, document in `deploy/RUNBOOK.md`); **HPD-22** E2E prod walkthrough (2 devices).
- [ ] **Bug Y** — live retest with two diners now that `v0.2.1` cleared the deploy skew (marked fixed by the user, no formal retest logged).
- [ ] **LSEO** — content (blog / keyword articles / case studies), conversion (contact/demo form, WhatsApp, hero video, retargeting), off-page, minor JSON-LD. LSEO-04 Google Business Profile.
- [ ] **Login: no "recuperar contraseña" (forgot password)** — flagged live by the user (report 461), explicitly deferred by them ("puede ser después"); no plan yet.
- [x] **Security debt — closed out 2026-09-22 (r535-539):** ~~F-15~~ Hub own admin password (r537); ~~F-21~~ Hub Postgres/MinIO random creds (r536, new installs only); ~~F-14~~ `/platform/**` RBAC (r535); ~~F-17~~ JWT `tokenVersion` revocation (r538); ~~F-10/E-23~~ PIN-login enumeration rate-limited + audit-logged, response split kept by product decision (r539); ~~F-24~~ print-agent key DPAPI (r418). Only remaining: F-22 (secret in git history — accepted, already rotated, rewrite only if the repo opens to untrusted third parties). All 5 branches merged to `main` locally, pending PR/merge + push to `origin`.

### Done (collapsed)
- [x] **HUB-PRINT + AGENT-UI-HARDENING + HUB-LICENSE-HARDENING (r520-525)** — print agent pairs with the Hub via a Nube/Local dropdown (cloud URL never in the UI bundle), `RECEIPT` jobs route to the requesting caja's agent by source IP; Hub heartbeat answers are RSA-signed + `hub-state.json` HMAC fail-closed + monotonic clock guard.
- [x] **CASH-SHIFT-DENOMINATION-COUNT (r488-494, PR #128)** — accountant open/close cash-shift counts bills/coins by 14 BCN denominations instead of one typed total; breakdown JSON on `CashShift` (`V12`), backend re-validates sum + legal denominations, close gains optional notes.
- [x] **PLAN-GATING-PHASE1 (r474-483)** — `PlanGateService` (402 `PLAN_LIMIT_EXCEEDED`) gates 6 features (tables/cashclose/periodfilters/export/branding/staff-roles) by `restaurant.plan`; Console assigns+changes plans; tenant self-service plan-change endpoint removed.
- [x] **KDS-BULK-STATUS-UPDATE (r464-467)** — focused-ticket bulk select (checkbox + select-all) + status dropdown, each dish walks every intermediate status one at a time. Plan `docs/superpowers/plans/2026-09-13-kds-bulk-status-update.md`.
- [x] **EMB-EXPORT (r428-433)** — tenant data CSV export (`ventas.csv`+`productos.csv`), Settings "Exportar datos" tab, ADMIN-only. Spec `docs/superpowers/specs/2026-09-11-tenant-data-csv-export-design.md`.
- [x] **KDS focused-ticket cleanup + manual reprint (r468)** — removed dead "Cliente"/"Anular" UI, `KitchenTicketPrintService`/`POST /printing/kitchen-orders/{orderId}/ticket` (KITCHEN) wires "Imprimir" as a reprint independent of auto-print.
- [x] **PRINT-JOB-CANCEL (r424)** — `PrintJobStatus.CANCELED` + `cancel`/`cancelAllPending` in `PrintDispatchService`, `POST /printing/jobs/{id}/cancel` + `/cancel-pending` (ADMIN+WAITER); `PrintingSettings` per-row **Cancelar** + header **Limpiar pendientes**. Stops `flushPendingFor` replaying stuck `PENDING` tickets onto an out-of-paper printer. No migration. Backend-only + frontend, not pushed to prod (awaits a tagged backend release).
- [x] **`/cash-shifts/current` empty 200 (r410)** — no open shift → 200 + empty body instead of 404, so the cash-register poll stops logging a console error. `findCurrentOpenShift(): Optional` + controller `orElse(null)`; `api.ts` `return data || null`. Backend change awaits a tagged release for prod.
- [x] **Frontend UX batch (r409 + r411 hotfix)** — `/admin` lands on analytics not inventory; section tours are request-only (no auto-start); arqueo difference no longer flashes on shift close (`CloseShiftDialog` unmounted with `cashShiftCurrent`). r409 wrongly removed the waiter "Registrar movimiento" button — **r411 restored it** and instead removed the dead TopNav "Nuevo registro" button (rendered with `actionType = null` on `/admin/cash-register`, `openModal(null)` fired nothing).
- [x] Core platform, EMB-PRINT, EMB-MOD, EMB-INV, restaurant onboarding, Ember Hub HUB-01, license heartbeat, EMBER-FIX, HPD-01..20, Landing SEO technical base — reports 1–329.
- [x] **FIX-QA** (22/23 live findings) — reports 361-364 (E-23 deferred).
- [x] **Platform console improvements A–D** — `updateRole` guard, retire/soft-delete tenants, Hub liveness/heartbeats in the console, console redesign + `GET /platform/stats`. Reports 383–385, PRs #80/#81/#82.
- [x] **Real-time bill bug cluster** — Bug X (EQUAL_PARTS divide-by-zero) + `@Transactional` handler. Reports 387-388, PRs #85/#86.
- [x] **Live QA findings Q1–Q6** — Q1a stale digital intent, Q1b loyalty reversal on refund, Q2 customer tax breakdown, Q3 `/menu/join` page, Q4 in-app QR scanner, Q5 print-agent setup docs, Q6 delete buttons. Reports 389–395, PRs #88/#89/#91–#95.
- [x] **Menu responsive** — report 396.
- [x] **Guest table-join (Cloud)** — report 397, PR #97.
- [x] **EMB-FEAT-HUB — Hub waiter-managed seats** — name-only seats + 3 WAITER endpoints + `ParticipantRenamed` + null sweep + loyalty guard; frontend `isHubBuild` fork (customer subsystem stripped, seat-name assign modal, seat controls, loyalty UI hidden). Report 398, merged in #98.
- [x] **Post-audit hardening** — seat-endpoint `@Transactional`, `App.hubBuild` de-flake, `addSeat` capacity 409, cruft cleanup, public `/join` guest code page. Report 399, merged in #99.
- [x] **Hub v2 prep** — optimistic-lock 409, `isHubBuild()` on `VITE_HUB_BUILD` + CI `build-hub` job, `V9` idempotent. Report 400, merged in #99.
- [x] Prod backend deployed to `v0.2.1` (Q1–Q6 backend + platform console). Reports 387/388, deploy incident recovered.
