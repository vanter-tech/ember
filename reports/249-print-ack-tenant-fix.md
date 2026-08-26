# Report 249

## Identification
- Report number: 249
- Task ID: PRINT-07 debugging, subtask 1 — "fix the print-agent ACK path"
- Predecessor: Report 248

## Objective
Investigate why physical-printer verification (PRINT-07) never showed a job transition to
`PRINTED`/`ERROR`, and fix the root cause before touching any hardware-specific code.

## Modified Files
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentAckController.java`
- `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentChannelInterceptor.java`
- `backend/src/test/java/com/vanter/ember/printing/controller/PrintAgentAckFlowIntegrationTest.java` (new)

## What Changed?
Investigation (systematic-debugging, read-only first): queried the dev DB directly. All 4
`print_jobs` rows reached `SENT`/`PENDING`, none ever reached `PRINTED`/`ERROR`, and
`last_error` was empty on all of them — the backend broadcasts fine, but no ACK ever lands.
Traced the ACK path end to end and found two stacked defects, neither visible without a real
STOMP round trip:

1. `WebSocketConfig.configureMessageBroker` registers
   `setApplicationDestinationPrefixes("/app", "/app/print-agent")`. Spring strips the FIRST
   prefix that matches a destination, so the agent's `SEND /app/print-agent/ack` resolves to
   mapped destination `/print-agent/ack`, not `/ack` — `PrintAgentAckController`'s
   `@MessageMapping("/ack")` never matched. Fixed by remapping to
   `@MessageMapping("/print-agent/ack")`.
2. `PrintAgentChannelInterceptor` only ever bound `TenantContextHolder` around the SUBSCRIBE
   frame (in `afterSendCompletion`, on the CONNECT-handling thread). `clientInboundChannel`
   dispatches to `@MessageMapping` handlers via a pooled executor, so a later SEND frame (the
   ACK) runs on a different thread with no tenant bound — `PrintDispatchService.handleAck`'s
   `findById` silently scoped to the empty `NO_TENANT` Hibernate partition and could never find
   the real job, even after fix (1). Fixed by implementing `ExecutorChannelInterceptor` on the
   same interceptor bean: `beforeHandle`/`afterMessageHandled` bind/clear the tenant (read from
   the session attributes stashed at CONNECT) on the exact thread that invokes the handler —
   this is the Spring-documented guarantee `ChannelInterceptor#preSend` does not give.

Added `PrintAgentAckFlowIntegrationTest` (full `@SpringBootTest`, real STOMP client over
`/ws/print-agent`, mirrors `PrintAgentTokenFlowIntegrationTest`/`WebSocketEndpointIsolationTest`):
token exchange → CONNECT → SUBSCRIBE → real ACK SEND → polls `PrintJobRepository` until
`PRINTED`. Confirmed RED against the pre-fix code (assertion failure: `expected: PRINTED, was:
SENT`), confirmed GREEN after both fixes. Full backend suite (`./mvnw test`) still passes.

## Why It Changed?
Without a working ACK path the system is blind: a real printer failure looks identical to a
job that was simply never routed, since `last_error` never populates either way. This was
blocking all further PRINT-07 physical-printer debugging (next: renderMode for driver-backed
printers, agent-side logging, and per-job printer refresh), which needs actual `PRINTED`/`ERROR`
signal to be useful. Root-caused via `systematic-debugging` before any fix was attempted, per
DB evidence + code trace, not guesswork.
