# Report 236 — HUB-01-11

## 1. Identification
- **Report number:** 236
- **Task ID:** HUB-01-11 (eleventh and final task of the HUB-01 plan, `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`)
- **Predecessor task:** report 235 (bugfix-hub-tray-icon-headless-mode)

## 2. Objective
Manual end-to-end verification of Ember Hub's licensing + portable-database bootstrap on real Windows hardware, against a real portable PostgreSQL distribution — the one part of HUB-01 the plan itself flags as having no CI coverage (spec §4: "no hay CI que cubra esto, es verificación manual").

## 3. Modified Files
None directly under this report — verification only, per the plan. Three real bugs surfaced during this task and were fixed as their own atomic tasks: reports 233, 234, 235 (see those reports for file lists). This report records the verification outcome.

## 4. What Changed?
Nothing new; this documents what was verified:

- **Postgres distribution used:** EDB "binaries only" PostgreSQL **16.6-1**, Windows x64 (`postgresql-16.6-1-windows-x64-binaries.zip` from `get.enterprisedb.com`) — matches `docker-compose.yml`'s `postgres:16` per the plan's version note. **HUB-03 should bundle this exact build.**
- **Step 1 (real binaries):** downloaded and extracted successfully; `initdb`/`pg_ctl`/`createdb` all present and functional.
- **Step 2 (test license):** generated a throwaway RSA-2048 keypair and a valid `license.key` via a temporary JUnit test calling `LicenseKeyParser.sign()` directly (no `openssl` available on this machine, so pure-Java `KeyPairGenerator` was used instead of the plan's literal `openssl genrsa` snippet — same X.509/PKCS8 formats, fully compatible with `LicenseKeyParser.loadPublicKey`). Test file deleted after use, never committed.
- **Step 3 (boot with `hub` profile):** succeeded end-to-end after fixing 3 real bugs found along the way (see below) — portable Postgres starts, `hub-state.json` is created with a correct `hardwareFingerprint`/`restaurantId`/`lastHeartbeatAt`, the full Spring context starts, and **the tray icon is confirmed visible by the user directly**.
- **Step 4 (grace period enforcement):** verified via `curl` with a real authenticated JWT (registered a throwaway customer via `POST /v1/auth/register`, since `GracePeriodInterceptor` only runs after Spring Security's `anyRequest().authenticated()` passes). Backdated `hub-state.json`'s `lastHeartbeatAt` by 5 days → `POST /v1/sessions/{id}/items` and `POST /v1/billing/sessions/{id}/request` both returned `403 {"error":"license_grace_period_expired",...}`. Restored the timestamp → both endpoints passed the interceptor again (reached normal controller validation, no longer 403).
- **Step 5 (three boot-error paths), all confirmed with actionable Spanish messages and non-zero exit, no raw stack trace:**
  - Port in use: started a second instance while the first still held the portable Postgres port → `"El puerto 5433 ya está en uso..."`.
  - Invalid license: pointed `EMBER_HUB_LICENSE_FILE` at a nonexistent path → `"No se encontró license.key en ..."`.
  - Wrong hardware: copied a real activated `hub-state.json` and altered its `hardwareFingerprint` → `"Esta licencia ya está activada en otra PC..."`.
- **Step 6:** this report + `PROGRESS.md` update.

## 5. Why It Changed?
This is the one path in HUB-01 with zero automated coverage by design — every prior HUB-01 task deferred its real-binary/real-desktop behavior here specifically (reports 228, 229, 230, 232 all say so explicitly). Running it for real was the only way to validate the actual integration, and it worked exactly as intended: it caught 3 genuine bugs that unit tests structurally could not reach (missing `CREATE DATABASE`, a Windows pipe-handle deadlock, and Spring's default headless mode) before they could ever reach a real restaurant's PC. All three are now fixed, tested (807/807), and independently committed (reports 233/234/235).

## Known residual gaps (not blocking, out of HUB-01's scope)
- PRINT-07-style hardware verification: no real customer-facing order flow was driven through the grace-period-protected endpoints (a throwaway customer JWT was used to reach the interceptor, not a full waiter/table/session business flow) — sufficient to prove the interceptor is wired correctly, but not a full business-flow smoke test.
- `LicenseService.recordHeartbeatSuccess` exists but nothing yet calls it periodically — HUB-01 only implements local grace-period *enforcement*; the actual cloud heartbeat that would call it belongs to a later Hub task (cloud sync), not yet built.
- This machine's own `docker-compose` Postgres (`ember-postgres-1`) occupies port 5432, so all portable-instance testing used port 5433+ instead of the plan's literal default — noted here so a future re-run on a clean machine isn't confused by the port choice.
