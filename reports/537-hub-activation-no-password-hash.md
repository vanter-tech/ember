# Report 537 — F-15: Hub activation no longer receives the admin's password hash

**Predecessor:** report 536 (HUB-LOCAL-CREDENTIAL-ROTATION / F-21, on a sibling branch not yet
merged — report numbers are assigned globally at commit time, independent of which branch).

## Objective
Close security finding F-15: `POST /hub-activations` returned the tenant admin's real bcrypt
password hash so the Hub could seed a matching local account. The Hub now generates its own
local admin password instead, shown once in the Hub's own UI — never received from the cloud,
never written to disk.

## Modified Files
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubActivationResponse.java`
- `backend/src/main/java/com/vanter/ember/licensing/service/HubActivationService.java`
- `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java`
- `backend/src/main/java/com/vanter/ember/hub/control/FirstRunCredentialHolder.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/control/DefaultHubOrchestrator.java`
- `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`
- `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- `backend/src/test/java/com/vanter/ember/licensing/service/HubActivationServiceTest.java`
- `backend/src/test/java/com/vanter/ember/licensing/controller/HubActivationControllerTest.java`
- `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java`
- `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`
- `backend/src/test/java/com/vanter/ember/hub/control/FirstRunCredentialHolderTest.java` (new)
- `frontend/src/lib/backend-types.ts`
- `ember-hub/ui/src/lib/types.ts`
- `ember-hub/ui/src/lib/api.ts`
- `ember-hub/ui/src/components/Dashboard.tsx`
- `ember-hub/ui/src/components/FirstRunCredentialsModal.tsx` (new)
- `ember-hub/ui/src/components/FirstRunCredentialsModal.test.tsx` (new)

## What Changed?
- `HubActivationResponse`/`HubActivationService`: `adminPasswordHash` removed entirely from the
  cloud's activation response.
- `HubProvisioningRunner`: generates a 20-character random local admin password (unambiguous
  alphabet, no `0/O/1/l/I`) on first activation, hashes it with the app's real `PasswordEncoder`
  (bcrypt) for the seeded `User` row, and hands the **plaintext** to a new
  `FirstRunCredentialHolder` — never persisted anywhere.
- `FirstRunCredentialHolder`: a plain in-memory holder shared between the embedded Spring context
  (where `HubProvisioningRunner` runs) and the outer sidecar process (where `HubControlServer`
  lives), wired via `SpringApplication.addInitializers(...)` registering it as a singleton bean
  before context refresh — `DefaultHubOrchestrator` owns the one instance, created once per
  process in `EmberApplication.runHubSidecar`.
- `HubControlServer`: new loopback-only `GET /api/first-run-credentials` (returns the pending
  `{email, password}`, both `null` once there's nothing to show) and
  `DELETE /api/first-run-credentials` (operator's acknowledgement — clears it from memory).
- `ember-hub/ui`: `Dashboard` polls the new endpoint alongside its existing status poll (only
  while nothing is already showing) and renders `FirstRunCredentialsModal` — email + password
  with a copy button, and a two-step "Continuar" → "Sí, ya la guardé" confirmation that warns
  explicitly that the password can't be shown again before it acknowledges (clearing it).

## Why It Changed?
F-15 (`AUDIT_BLUEPRINT.md`) was left open pending a redesign because transmitting the admin's
real bcrypt hash over the activation endpoint meant a compromised or logged response could be
cracked offline to recover the actual password — which also unlocks the cloud tenant, not just
the Hub. The user's own prior framing ("the Hub generates its own temporary password instead of
receiving the hash") set the direction; the open design question was how to surface that
generated password to the operator. A first draft wrote it to a `.txt` file next to
`hub-state.json` — the user rejected that (a plaintext credential sitting on disk indefinitely).
Landed instead on an in-memory hand-off shown once in the Hub's own UI, which the user approved.

**No data migration needed:** activation is a strictly one-time call per Hub, guarded by
`restaurantRepository.existsById(...)` — an already-activated Hub never calls this endpoint
again, so the contract change cannot affect it. A license issued but not yet activated simply
gets the new contract on its first-ever boot, as long as the cloud deploys before that Hub
build ships (same "deploy cloud first" convention as report 520).

**Known trade-off, stated in the UI's own copy:** the credential lives only in memory for the
life of the sidecar process. It survives a Detener/Iniciar cycle (the embedded Spring context
restarts; the holder, owned one level up, doesn't), but a full process restart before the
operator acknowledges it loses it for good — there's no "forgot password" flow yet (already a
separately deferred, known gap). Accepted as the cost of never touching disk.

## Verification
- `cd backend && ./mvnw test`: **1490/1490** on this branch (off `main` directly, not stacked on
  the sibling F-14/F-21 branches — so this count doesn't include their new tests), including the
  4 new `FirstRunCredentialHolderTest` and 4 new `HubControlServerTest` first-run-credential
  cases, excluding the same 2 pre-existing, unrelated port-59999 failures noted in reports
  535/536.
- `HubProvisioningRunnerTest`'s activation test uses a **real** `BCryptPasswordEncoder` (not
  mocked) and asserts `passwordEncoder.matches(credential.password(), savedUser.getPasswordHash())`
  — proves the saved hash actually corresponds to the plaintext handed to the UI, not just that
  some opaque string was saved.
- `HubActivationControllerTest` asserts `$.adminPasswordHash` and `$.adminPassword` both
  `doesNotExist()` in the JSON response — a real regression guard, not just relying on the DTO no
  longer compiling with that field.
- `cd frontend && pnpm run build` and `pnpm run lint`: clean (0 errors, same 15 pre-existing
  warnings, none touching these files).
- `cd ember-hub/ui && pnpm test`: **34/34** (4 new `FirstRunCredentialsModal` tests) and
  `pnpm run build`: clean.
- No Rust changes this task (the new endpoints reuse the existing loopback `fetch()` the UI
  already had wired up), so the `cargo` sandbox issue from report 536 doesn't apply here.

## Out of scope
- No "forgot password" recovery flow (pre-existing, separately deferred gap noted in
  `PROGRESS.md`) — if the generated credential is lost before acknowledgement and a full restart
  happens, there is currently no in-app way back in.
