# Report 256 — Task 10

## 1. Identification
- **Report number:** 256
- **Task ID:** Task 10 (tenth and final task of the `hub-license-activation` plan, `docs/superpowers/plans/2026-08-25-hub-license-activation.md`)
- **Predecessor task:** report 255 (PRINT-07 debugging subtask 6: fix NetworkPrinterSender false-positive)

## 2. Objective
Manual end-to-end verification of the full license→tenant→admin provisioning pipeline built across Tasks 1–9 (reports 239–248): issue a real Hub license from `/console`, activate a fresh Hub instance against it, confirm the restaurant/admin get seeded locally and the admin can log in, and confirm a second boot is idempotent (no re-activation call).

## 3. Modified Files
None under version control — verification only, same shape as HUB-01-11 (report 236). Two local, gitignored artifacts were changed as part of running the verification (not committed, not part of this task's diff):
- `.env` — `HUB_LICENSE_PRIVATE_KEY` set to a freshly generated real RSA-2048 PKCS8 key (was empty).
- `backend/src/main/resources/static/` — rebuilt via the Hub frontend build (`pnpm run build:hub` + manual copy, `--base=/app/`) to guarantee the bundle reflects every commit through Task 9, since its prior contents (from report 238's testing) couldn't be dated against Task 9's `Login.tsx` change with certainty.

## 4. What Changed?
Nothing in source; this documents what was verified, in the plan's own step order:

- **Step 1 (real key pair + cloud backend config):** generated a real RSA-2048 key pair via `openssl genpkey`. **Found+fixed a real tooling gotcha, not a code bug:** this machine's mingw64/Git-for-Windows `openssl genpkey -algorithm RSA ... -outform DER` does **not** emit PKCS8 despite genpkey's documented default — `openssl asn1parse` showed the raw PKCS1 `RSAPrivateKey` SEQUENCE (version/modulus/exponent directly, no `AlgorithmIdentifier`) at the top level, and `LicenseIssuingService`'s strict PKCS8 loader threw `InvalidKeySpecException: algid parse error, not a sequence` the first time the console's "Emitir licencia Hub" button was clicked (silent failure client-side — no download, no visible error, since that button has no failure toast per Task 8's plan). Fixed by explicitly re-wrapping with `openssl pkcs8 -topk8 -nocrypt -inform DER -outform DER` (confirmed via `asn1parse` afterward: proper `SEQUENCE { INTEGER, SEQUENCE { OID rsaEncryption, NULL }, OCTET STRING }`). **Flagged for anyone generating a real production signing key on Windows with this toolchain: `openssl genpkey -outform DER` alone is not sufficient, always pipe through `openssl pkcs8 -topk8 -nocrypt` and verify with `asn1parse` before trusting the output.** Set the corrected base64 key into `.env`, restarted the dev backend (no `hub` profile).
- **Step 2 (restaurant via `/console`):** user created restaurant `lacocinamia` (id `d2070312-b315-4bca-a6c1-1d65fa223e76`) with admin `fer1@lacocina.com` via the existing seeded `platform-admin@ember.local` operator login.
- **Step 3 (issue license):** after the Step 1 fix, "Emitir licencia Hub" downloaded `license.key` successfully.
- **Step 4 (boot test Hub):** reused the portable Postgres binaries extracted for HUB-01-11 (`C:\ember-hub-test\pgextract\pgsql\bin`) against a genuinely fresh data directory (`C:\ember-hub-test-2\`, port 5433, server port 8090, `EMBER_HUB_ACTIVATION_URL=http://localhost:8080/v1/hub-activations`). Launched via `mvnw spring-boot:run` with `SPRING_PROFILES_ACTIVE=hub` — confirmed this spawns a real detached Swing window titled "Ember Hub" (PID separate from the Maven wrapper process) rather than blocking the invoking shell, worth knowing for any future automated/scripted launch of this same entry point.
- **Step 5 (provisioning + login):** user clicked "Iniciar". Backend log: `HubProvisioningRunner - Provisioned restaurant d2070312-b315-4bca-a6c1-1d65fa223e76 (lacocinamia) locally.` Confirmed directly against the Hub's own local Postgres (`psql` via the portable binaries, port 5433): one `restaurants` row (`lacocinamia`, `ACTIVE`) and one `users` row (`fer1@lacocina.com`, `ADMIN`, matching `restaurant_id`). User clicked "Abrir en navegador" and logged in with the same admin email/password used in Step 2 — **succeeded.**
- **Step 6 (idempotency):** user clicked "Detener" — graceful Tomcat/Hikari shutdown confirmed in the log, port 8090/5433 released, `tasklist` confirmed **zero orphaned `postgres.exe`** processes (matches report 237's earlier fix). User clicked "Iniciar" again — log: `HubProvisioningRunner - Restaurant d2070312-b315-4bca-a6c1-1d65fa223e76 already provisioned locally, skipping activation call.` Confirmed the real cloud backend's own log has no second `POST /hub-activations` entry — genuinely zero network calls on the second boot, not just a client-side skip.

## 5. Why It Changed?
This closes the one remaining unverified step of the `hub-license-activation` plan (Tasks 1–9 were implemented and unit/integration tested, but the actual license→tenant→admin chain across two separate Spring Boot processes — the cloud console and a real portable-Postgres Hub — had never been driven end-to-end). It also caught a real gap in Task 8's UX (no failure feedback on the "Emitir licencia Hub" button) indirectly, by way of a genuine environment/tooling issue in key generation — worth a small follow-up someday (toast on `issueHubLicense` mutation error) but out of scope for this verification-only task.

## Known residual gaps (not blocking, out of Task 10's scope)
- Task 8's "Emitir licencia Hub" button has no error toast — a future signing-key misconfiguration on a real deploy would look like "nothing happened" again, same as this task's Step 1 symptom. Flagged, not fixed here.
- The Hub test instance (`C:\ember-hub-test-2\`, PID from this session) is left running/stopped in whatever state the user last clicked; not torn down as part of this task.
- `.env`'s `HUB_LICENSE_PRIVATE_KEY` is now permanently set to a real (if throwaway-for-dev) key — anyone issuing a Hub license from this dev backend from now on gets one signed by this key; `hub-public-key.der` used by any future test Hub must match it (the one at `C:\ember-hub-test\`'s original location is from a *different* HUB-01-11 throwaway keypair and is NOT compatible with licenses issued after this task).
