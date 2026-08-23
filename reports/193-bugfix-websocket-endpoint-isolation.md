# Report 192 — Bugfix: tenant WebSocket connections rejected by the print-agent interceptor

## 1. Identification
- **Report number:** 193
- **Task ID:** bugfix (user-reported: customer "add item" never showed the floating items island, and "ver comanda" bounced back to the menu)
- **Predecessor Task:** INV-04 frontend half / Task 5 — Admin UI (report 192)

## 2. Objective
Fix a regression where **every tenant-facing STOMP WebSocket connection** (`/ws` — customer, waiter, kitchen, admin) was being rejected with `"Not a print-agent token"` and immediately closed, silently breaking all real-time features (collaborative cart sync, KDS, waiter floor updates, inventory low-stock alerts) since EMB-PRINT shipped (report 180).

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/config/WebSocketSessionAttributes.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentHandshakeInterceptor.java`
- Create: `backend/src/test/java/com/vanter/ember/config/WebSocketEndpointIsolationTest.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentWebSocketConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentChannelInterceptor.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/JwtChannelInterceptor.java`
- Modify: `backend/src/test/java/com/vanter/ember/printing/config/PrintAgentChannelInterceptorTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/JwtChannelInterceptorTest.java`

## 4. What Changed?

**Root cause:** `PrintAgentWebSocketConfig` (EMB-PRINT, report 180) registers a *second* `@Configuration` class annotated `@EnableWebSocketMessageBroker`, under the documented assumption that this gives the print-agent-only `/ws/print-agent` endpoint an isolated broker/channel from the tenant-facing `/ws` endpoint (`WebSocketConfig`). That assumption is wrong: Spring's `@EnableWebSocketMessageBroker` collects **every** `WebSocketMessageBrokerConfigurer` bean in the context and applies all of their `registerStompEndpoints`/`configureClientInboundChannel` callbacks onto **one shared `clientInboundChannel`**. So `PrintAgentChannelInterceptor` — meant only for `/ws/print-agent` — ran on every CONNECT frame from every endpoint, including `/ws`. A normal tenant JWT has no `typ: "print-agent"` claim, so the interceptor rejected it with `MessageDeliveryException(message, "Not a print-agent token")`, closing the connection right after it opened — exactly the "CONNECT en verde y después ERROR: message not a print-agent" the user observed. A unit test of either interceptor in isolation cannot catch this class of bug (each interceptor behaves correctly on its own) — only a full Spring context boot with a real STOMP client reproduces it, which is why it went undetected: no browser/live verification has happened on this branch since EMB-PRINT shipped.

**Fix:** endpoint-tag the WebSocket session at handshake time so interceptors registered on the shared channel can tell which endpoint a session actually connected through, instead of assuming they own the whole channel:
- `WebSocketSessionAttributes` (new, `config` package): shared constants for the session-attribute key/value pair.
- `PrintAgentHandshakeInterceptor` (new, `printing.config`): a `HandshakeInterceptor` registered only on `/ws/print-agent` (`PrintAgentWebSocketConfig.registerStompEndpoints`) that stamps `wsEndpoint=print-agent` into the session attributes during the handshake — before any STOMP frame is processed.
- `PrintAgentChannelInterceptor.preSend`: now returns the message untouched for any session **not** tagged `print-agent`, instead of unconditionally enforcing print-agent auth on every CONNECT.
- `JwtChannelInterceptor.preSend`: defense-in-depth — skips tenant-user JWT validation entirely for a session tagged `print-agent` (it was never meant to authenticate that traffic; `PrintAgentChannelInterceptor` owns it).

**Test:** `WebSocketEndpointIsolationTest` boots the full app on a random port, connects a real STOMP client (`WebSocketStompClient` + `StandardWebSocketClient`) to `/ws/websocket` with a genuine WAITER JWT, and asserts the CONNECT succeeds. This reproduced the bug (RED: `ConnectionLostException` after the server-side `MessageDeliveryException`) before the fix, and passes (GREEN) after it. `PrintAgentChannelInterceptorTest`'s three existing tests were updated to explicitly tag their accessors as print-agent sessions (matching how they now actually arrive in production via the handshake interceptor); two new tests (`connect_notPrintAgentEndpoint_passesThroughUntouched`, `connect_nullSessionAttributes_passesThroughUntouched`) prove non-print-agent traffic is left alone. `JwtChannelInterceptorTest` gained `connect_printAgentEndpoint_skipsTenantValidation`.

## 5. Why It Changed?
This is a full outage of every real-time feature in the tenant app, not just the customer cart — KDS order push, waiter floor updates, admin low-stock alerts (the feature just shipped in Task 5) were equally broken, silently, because nothing in this branch's recent sessions had browser/live-WS access to catch it. The fix restores the isolation the original EMB-PRINT design actually intended (print-agent auth stays completely separate from tenant auth) without reverting the two-endpoint architecture, since that separation (different auth mechanism, no `UserDetailsService` involvement for agents) is still the right design — it just needed to be enforced with an endpoint tag instead of an incorrect assumption about Spring's channel isolation.

## Verification
- `WebSocketEndpointIsolationTest` alone: RED (`ConnectionLostException`, server-side `MessageDeliveryException` from `JwtChannelInterceptor`/`PrintAgentChannelInterceptor`) confirmed before the fix, GREEN after.
- `cd backend && ./mvnw test` → **794/794 PASS** (full suite, +4 vs. report 191's 790: the new isolation test + 3 new/updated interceptor tests).
- Not yet verified: a live browser click-through of the customer add-item → island → comanda flow (still owed, same recurring gap — no `claude-in-chrome` tool this session). The user should retest in-browser to confirm the island now appears and "ver comanda" no longer bounces back.
