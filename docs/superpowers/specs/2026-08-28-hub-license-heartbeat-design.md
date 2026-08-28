# Ember Hub — License Heartbeat (Sub-project A1) — Design

> Status: **approved design**, ready for an implementation plan.
> Part of Ember Hub v1 sync (`docs/superpowers/specs/ember_hub.md` §2.2, §2.7, §2.8).
> The v1-remaining Hub work was decomposed into 3 sub-projects: **A — cloud sync**,
> **B — packaging & lifecycle**, **C — local backup**. A was split further: **A1 — license
> heartbeat** (this doc) ships first as the minimal high-value piece; **A2 — data sync**
> (catalog/config pull + closed-sales upload) is a later spec that reuses A1's scheduler.

## 1. Motivation

`LicenseService` already computes a 4-day offline grace window (`isWithinGracePeriod`) and
already exposes `recordHeartbeatSuccess`, but **nothing ever calls it** — `HubState.lastHeartbeatAt`
is only set once, at activation. So today a Hub's grace clock starts at install and never resets,
and there is no channel for the cloud operator to stop a delinquent customer's Hub (the
`ember_hub.md` §2.2 "desactivación remota" promise the annual billing model depends on).

A1 adds the periodic outbound call that makes both real: a Hub that has had internet in the last
4 days keeps operating; a restaurant the operator suspends from `/console` stops after a short
courtesy grace.

## 2. Scope

**In:** a periodic Hub→cloud heartbeat; a new `permitAll` cloud endpoint that validates the
license and reports the restaurant's status; Hub-side state for a suspended-grace counter; an
extension of the existing write-block gate; a dashboard status line.

**Out — deferred to A2:** catalog/config delta pull (cloud→Hub), closed-sales upload
(Hub→cloud), and the cloud-side consumer of uploaded sales.

**Out — deferred to sub-project B:** acting on the `latestVersion` field (the auto-updater).
A1 includes the field in the response but the Hub ignores it.

## 3. Cloud side (main monolith, no `hub` profile)

### 3.1 Endpoint

`POST /hub-heartbeat` — new `permitAll` route (same trust model as `/hub-activations`:
authenticated by the license signature itself, no JWT). Add it to `RateLimitProperties.paths`,
matching `/hub-activations`. Add `@Profile("!hub")` to the controller (a Hub must never expose
this on its own LAN), matching `HubActivationController`.

Request body:

```json
{ "licenseKey": "<base64 signed license>", "hardwareFingerprint": "<fp>" }
```

Response body (HTTP 200):

```json
{ "status": "OK" | "SUSPENDED", "serverTime": "<ISO-8601 instant>", "latestVersion": "<string|null>" }
```

### 3.2 `HubHeartbeatService`

1. Parse + verify the license signature via `LicenseKeyParser.parseAndVerify` +
   `LicenseIssuingService.publicKey`. Invalid → `InvalidLicenseException`.
2. Load the `HubActivation` by `restaurantId` (from the license payload). Missing, **or**
   `hardwareFingerprint` != the value recorded at activation → `InvalidLicenseException`
   ("otra PC" message, reuse `HubActivationService`'s wording).
3. Load the `Restaurant` by id. Missing → `InvalidLicenseException`.
4. `status` = `OK` if `Restaurant.status == ACTIVE`, else `SUSPENDED`.
5. `latestVersion` = the current release version string the cloud knows about (for v1: a single
   configured value, `hub.latest-version`, default `null` — wired for real in sub-project B).
6. `serverTime` = `Instant.now()`.

`InvalidLicenseException` is already mapped to 400 by `GlobalExceptionHandler`. No new mapping.
The response body must **never** echo restaurant/admin data — only the three fields above.

### 3.3 Why only `Restaurant.status` for v1

No new entity or column. Suspending a restaurant from `/console` (EMB-PC, existing) already
gates every cloud tenant request; A1 makes it gate that tenant's Hub too. A per-hardware
"revoke this one PC" concept (a `revokedAt` on `HubActivation` + a `/console` button) is a
clean later addition for the §2.2 PC-reassignment flow, explicitly deferred (YAGNI for v1).

## 4. Hub side (`hub` profile)

### 4.1 Config (`HubProperties`)

- `heartbeatUrl` — `EMBER_HUB_HEARTBEAT_URL`, default `""`.
- `heartbeatIntervalMs` — `EMBER_HUB_HEARTBEAT_INTERVAL_MS`, default `300000` (5 min; aligns
  with `ember_hub.md` §2.7's 5–10 min so A2 reuses the same scheduler unchanged).
- `suspendedGraceHours` — `EMBER_HUB_SUSPENDED_GRACE_HOURS`, default `48`.

### 4.2 `HubState`

Gains one nullable field:

- `suspendedSince: Instant` — when the most recent heartbeat first returned `SUSPENDED`.
  Serialized into the existing `hub-state.json` via `HubStateStore`. Absent/`null` in an
  existing state file is read as `null` (no migration needed — it's a JSON document).

### 4.3 `HeartbeatScheduler` (`com.vanter.ember.hub.sync`, `@Profile("hub")`)

No `@Scheduled` exists in the codebase yet, so `@EnableScheduling` must be added — on a
`@Profile("hub")`-gated `@Configuration` (e.g. `HubBeansConfig`), **not** on the main application
class, so the cloud deployment gains no scheduler.


`@Scheduled(fixedDelayString = "${...heartbeatIntervalMs}")`, no `initialDelay` — Spring runs
it once shortly after startup, then every `heartbeatIntervalMs` after each run finishes, so a Hub
that just booted with connectivity gets a fresh heartbeat immediately. Each run:

1. If `heartbeatUrl` is blank → log once at `DEBUG`, return (dev / unconfigured Hub). Same
   convention as `HubProvisioningRunner` with a blank `activationUrl`.
2. Read current `HubState` from `HubStateStore`.
3. POST `{ licenseKey (read from the license file), hardwareFingerprint (from state) }` to
   `heartbeatUrl` via a `java.net.http.HttpClient` with `connectTimeout(10s)` and a per-request
   `timeout(15s)` — identical to `HubProvisioningRunner`.
4. On the response:

   | Result | Effect on `HubState` |
   |---|---|
   | `200` + `status:"OK"` | `lastHeartbeatAt = now` (via `LicenseService.recordHeartbeatSuccess`); `suspendedSince = null`. |
   | `200` + `status:"SUSPENDED"` | `lastHeartbeatAt` unchanged; `suspendedSince = now` **only if currently null**. |
   | any other (network error, timeout, 4xx, 5xx, unparseable body) | no state change; `WARN` log with the cause; return. |

5. Persist the mutated `HubState` via `HubStateStore` (only when it actually changed).

**Any exception is caught inside `run()`** — it must never propagate out of the `@Scheduled`
method, or Spring stops scheduling future runs.

### 4.4 Write-block gate

`LicenseService` gains:

```java
boolean isSuspendedGraceExpired(HubState state)  // suspendedSince != null && (now - suspendedSince) > SUSPENDED_GRACE
```

`GracePeriodInterceptor` currently blocks order/payment writes when
`!isWithinGracePeriod(state)`. It now blocks when
`!isWithinGracePeriod(state) || isSuspendedGraceExpired(state)`.

Two independent counters by design: **"couldn't ask" (4 days)** and **"was told no" (48h)** are
different situations with different tolerances. A reactivated restaurant (`status` back to
`ACTIVE`) gets `OK` on the next cycle → `suspendedSince` clears → it operates again with no
reinstall.

The interceptor's block response distinguishes the two reasons in its message: *"no connection
to the license server for N days"* vs *"license suspended — contact Vanter"*.

## 5. Dashboard

`HubDashboard.java` gains one read-only status line, sourced from `HubState`:

- `Licencia: OK · último contacto hace 3 min`
- `Licencia: SUSPENDIDA hace 12 h`
- `Licencia: sin contacto hace 2 d`

No new buttons.

## 6. Error handling summary

- Scheduler exceptions never escape `run()`.
- Blank `heartbeatUrl` → silent no-op (one `DEBUG` line).
- Connect/request timeouts bounded (10s / 15s) so a black-holing firewall can't hang the
  scheduler thread.
- Cloud: bad signature / fingerprint mismatch / unknown restaurant → `InvalidLicenseException`
  → 400. The Hub treats any 4xx exactly like a network failure (a failed cycle, not a block on
  its own) — the 4-day counter is the backstop.
- `/hub-heartbeat` added to `RateLimitProperties.paths`.

## 7. Testing

| Test | Covers |
|---|---|
| `HubHeartbeatServiceTest` | `OK` for valid sig+fingerprint+ACTIVE; `SUSPENDED` when `status != ACTIVE`; `InvalidLicenseException` for bad signature, fingerprint mismatch, unknown restaurant. |
| `HubHeartbeatControllerTest` | happy path + each 400. |
| `HeartbeatSchedulerTest` | `com.sun.net.httpserver.HttpServer` on an ephemeral port (as `HubProvisioningRunnerTest`) serving OK / SUSPENDED / 500 / (no response → timeout); asserts the `HubState` transition captured via `HubStateStore` for each. |
| `LicenseServiceTest` (extend) | `isSuspendedGraceExpired`: `null`, within 48h, past 48h. |
| `GracePeriodInterceptor` test (extend) | blocked when `suspendedSince` is past its grace even though the 4-day heartbeat window is fresh. |

Desktop paths (the dashboard label) are manual verification, consistent with the rest of
HUB-01.

## 8. New / touched files

**New (cloud):** `HubHeartbeatController`, `HubHeartbeatService`, request/response DTOs, their
tests.
**New (Hub):** `com.vanter.ember.hub.sync.HeartbeatScheduler` + test.
**Touched:** `HubProperties` (3 fields), `HubState` (1 field), `HubStateStore` (serialize the
new field), `LicenseService` (`isSuspendedGraceExpired` + wire `recordHeartbeatSuccess` caller),
`GracePeriodInterceptor` (second condition + message), `HubDashboard` (status line),
`HubBeansConfig` (`@EnableScheduling`, hub-profile only), `RateLimitProperties`/config
(`hub.latest-version`, path entry), `.env.example`.
