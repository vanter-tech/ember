# Report 520 — HUB-LICENSE-HARDENING

## 1. Identification
- **Report number:** 520
- **Task ID:** HUB-LICENSE-HARDENING (self-audit of the Hub license lock)
- **Predecessor:** report 518 (bump 0.2.7 Hub backup release; report 519 lives on the unmerged `fix/hub-backup-ux` branch)

## 2. Objective
Close the cheap bypasses of the Hub license found in a self-review: editable `hub-state.json` (grace/suspension), unsigned heartbeat answers (fake server via `EMBER_HUB_HEARTBEAT_URL` in `hub.env`), delete-state-to-reset-grace, and clock rollback.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/HubState.java`
- `backend/src/main/java/com/vanter/ember/hub/license/HubStateStore.java`
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseKeyParser.java`
- `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- `backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java`
- `backend/src/main/java/com/vanter/ember/hub/sync/HeartbeatScheduler.java`
- `backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java`
- `backend/src/main/java/com/vanter/ember/licensing/service/LicenseIssuingService.java`
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatRequest.java`
- `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatResponse.java`
- Tests: `HubStateStoreTest`, `LicenseServiceTest`, `GracePeriodInterceptorTest`, `HeartbeatSchedulerTest`, `HubHeartbeatServiceTest`
- `PROGRESS.md`, `reports/520-task-hub-license-hardening.md`

## 4. What Changed?
- **Signed heartbeat.** The Hub sends a random `nonce` per request. The cloud answers with an RSA signature (same key pair as `license.key`) over `hb1|status|serverTime|nonce` (`LicenseKeyParser.signHeartbeat/verifyHeartbeat`). `HeartbeatScheduler` verifies before touching state; unsigned, foreign-key or replayed (other nonce) answers are ignored. `serverTime` is truncated to millis so the signed text equals the JSON wire text.
- **State integrity.** `hub-state.json` gets a `mac` (HMAC-SHA256, key = SHA-256(pepper + fingerprint)). A wrong MAC loads as fail-closed (`lastHeartbeatAt` and `suspendedSince` = epoch → writes blocked until a signed OK heartbeat). A file without MAC (legacy install or stripped MAC) keeps its identity but loses its heartbeat, so it is re-earned from the cloud. Instants are read as `BigDecimal` so nanoseconds survive the round trip.
- **No free grace.** `validateOrActivate` first-run state starts with `lastHeartbeatAt = EPOCH`; the first signed OK heartbeat grants the 4-day window. Deleting `hub-state.json` no longer resets it.
- **Clock rollback.** `HubState.lastSeenAt` only moves forward (advanced every 5-min scheduler cycle, online or not). `GracePeriodInterceptor` returns 403 `license_clock_rolled_back` when now < lastSeenAt − 10 min. A successful signed heartbeat whose `serverTime` is within 10 min of the local clock resets `lastSeenAt` (clears a false alarm after the user fixes their clock); otherwise the flag persists.
- Cloud fingerprint-per-restaurant binding (`HubActivationService`/`HubHeartbeatService`) already existed and is unchanged.

## 5. Why It Changed?
The lock relied on unsigned local JSON and an unsigned HTTP answer: anyone with file access could extend the grace window or clear a suspension in Notepad, or point the heartbeat URL at a fake server that always answers `OK`. Rebuilding on the cloud's private key moves the trust anchor to something the customer never holds. Verification: backend `./mvnw test` **1376/1376**.

**Deploy order:** cloud first. An old cloud sends no signature, and a new Hub rejects unsigned answers (grace would run out after 4 days). New cloud + old Hub is fine (extra field ignored).

**Residual risks (accepted):** the HMAC key is derivable by anyone who reads the code (raises the bar from Notepad to decompiling); patching the jar or the public key file; the cloud does not send `suspendedSince`, so deleting the state can restart the 48 h courtesy window of a suspended restaurant; a clock frozen (not rolled back) while offline.
