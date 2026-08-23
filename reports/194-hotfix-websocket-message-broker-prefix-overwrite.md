# Report 194 — Hotfix: message broker prefixes silently overwritten, no tenant broadcast ever delivered

## 1. Identification
- **Report number:** 194
- **Task ID:** hotfix (follow-up to report 193 — user retested live and confirmed CONNECT/SUBSCRIBE now worked, transport-level heartbeats flowed, but no application message ever arrived: item-add never updated the customer's `ember-session` cache, and the waiter's participant count never updated)
- **Predecessor Task:** bugfix — WS endpoint isolation (report 193)

## 2. Objective
Fix the actual remaining reason WebSocket broadcasts (`ITEM_ADDED`, `PARTICIPANT_JOINED`, etc.) never reached any tenant client, even after report 193 fixed the CONNECT-level rejection.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/config/WebSocketConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentWebSocketConfig.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/WebSocketEndpointIsolationTest.java`

## 4. What Changed?

**Root cause:** the same "Spring merges every `WebSocketMessageBrokerConfigurer` bean onto one shared infrastructure" fact from report 193 also applies to `configureMessageBroker(MessageBrokerRegistry registry)` — and `MessageBrokerRegistry#enableSimpleBroker(...)` **replaces** the previous simple-broker registration rather than adding to it (it reassigns the registry's single `SimpleBrokerRegistration` field on each call). `PrintAgentWebSocketConfig.configureMessageBroker` called `registry.enableSimpleBroker("/topic/print-agent")`, and since its bean's callback ran after `WebSocketConfig`'s, it silently wiped out `WebSocketConfig`'s `registry.enableSimpleBroker("/topic", "/user")` registration. The live broker ended up only recognizing `/topic/print-agent/**` — so `messagingTemplate.convertAndSend("/topic/session/{id}", ...)` and `.../topic/waiter/{tenantId}` calls succeeded (no exception) but were silently dropped: no subscriber ever received them. This is why report 193's fix (interceptor isolation) was necessary but not sufficient — CONNECT/SUBSCRIBE and heartbeats all worked fine (protocol-level, unaffected by broker prefix config), but every actual application broadcast vanished.

**Fix:** `WebSocketConfig.configureMessageBroker` is now the ONE place that calls `enableSimpleBroker`/`setApplicationDestinationPrefixes`, registering both the tenant prefixes AND the print-agent prefixes in a single call: `registry.enableSimpleBroker("/topic", "/user", "/topic/print-agent")` / `registry.setApplicationDestinationPrefixes("/app", "/app/print-agent")`. `PrintAgentWebSocketConfig` no longer overrides `configureMessageBroker` at all (the interface method is a no-op default, per `WebSocketMessageBrokerConfigurer`), with a class-level Javadoc explaining why, pointing back at `WebSocketConfig`. This keeps the print-agent/tenant AUTH isolation intact (still fully enforced at the interceptor level from report 193) while fixing the routing-prefix collision, which is an orthogonal concern.

**Test:** added `topicBroadcast_isActuallyDeliveredToASubscriber` to `WebSocketEndpointIsolationTest` — connects, subscribes to an arbitrary `/topic/...` destination, has the server broadcast via the real `SimpMessagingTemplate` bean, and asserts the client actually receives the payload. This is the test that finally reproduced the user's exact symptom (RED: `TimeoutException`, no message ever arrives, despite a healthy connection) and confirms the fix (GREEN).

## 5. Why It Changed?
Report 193 fixed a real, severe bug (every tenant CONNECT rejected), but the user's live retest proved a second, independent bug was stacked on top of it from the same root design mistake (EMB-PRINT's false assumption that a second `@EnableWebSocketMessageBroker` config gets isolated infrastructure). Both bugs share the same underlying lesson, now documented in `PROGRESS.md`: any config touching `MessageBrokerRegistry` or `ChannelRegistration` must go through the ONE shared registration point, never assume a second config class is additive.

## Verification
- `WebSocketEndpointIsolationTest` (3 tests: raw-WS CONNECT, SockJS-faithful CONNECT, message delivery) — all RED→GREEN across this fix and report 193's.
- `cd backend && ./mvnw test` → **796/796 PASS** (full suite, +2 vs. report 193's 794: the new broadcast-delivery test plus its SockJS sibling added mid-investigation).
- **User confirmed live in-browser**: item add now updates the customer's `ember-session` cache and the floating items island appears; the waiter's participant count updates on join. This is the first task in the WS-isolation investigation with real browser confirmation, not just automated tests.
