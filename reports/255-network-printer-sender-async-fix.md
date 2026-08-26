# Report 255

## Identification
- Report number: 255
- Task ID: PRINT-07 debugging, subtask 6 — "fix NetworkPrinterSender's false-positive PRINTED"
- Predecessor: Report 254

## Objective
Fix the bug flagged (not fixed) in report 250: a `NETWORK`-connection-type printer that's
offline or unreachable used to silently ack `PRINTED` instead of `ERROR`.

## Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/NetworkPrinterSender.java`
- `printing-agent/src/test/java/com/vanter/emberagent/NetworkPrinterSenderTest.java`

## What Changed?
Root cause (confirmed earlier via `javap` disassembly, report 250): `escpos-coffee`'s
`TcpIpOutputStream` is a `PipedOutputStream` whose constructor spawns a detached background
thread that opens the real `Socket` and pumps bytes from an internal `PipedInputStream` to it.
The caller's `EscPos` writes go into the in-memory pipe and always "succeed" regardless of
whether the TCP connection actually worked — any connect/write failure on that background
thread only reaches a logged `UncaughtExceptionHandler`, never the caller's `catch`.

`NetworkPrinterSender.print()` no longer uses `TcpIpOutputStream` at all: it opens a plain
`java.net.Socket` itself, connects synchronously with an explicit 5s timeout (`socket.connect(new
InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)` — the old code had no timeout of any kind,
raw or otherwise), and hands `EscPos` the socket's own `OutputStream` directly. Any connection
or write failure is now a normal synchronous `IOException` on the calling thread, exactly what
`PrintJobDispatcher`'s `catch` already expects — no code changed on the dispatcher side.

New test `print_connectionRefused_throwsIOException` (closed port, bound then immediately
released to guarantee "connection refused" rather than a possibly-hanging non-routable IP):
confirmed RED against the pre-fix code (assertion failure — nothing was thrown, silently
"succeeded"), GREEN after the fix. The existing `print_sendsBytesToTcpSocket` test (real
`ServerSocket` accepting bytes) still passes unmodified. Full `printing-agent` suite + `mvn
clean package` PASS.

## Why It Changed?
User-approved follow-up from this session's E2E verification review — not in active use today
(current setup is `WINDOWS_QUEUE`), but a real correctness gap that would have silently
misreported any future `NETWORK`-type printer's actual state, indistinguishable from a genuine
success.

## Verification
- `cd printing-agent && mvn test` — PASS, all tests including the new RED→GREEN case.
- `cd printing-agent && mvn clean package` — PASS, produced the real shaded jar.
- Restarted the real agent process (already running against the local dev backend from report
  254's verification) against the rebuilt jar — reconnected cleanly, `printer-80` (the L3210,
  `WINDOWS_QUEUE`/`DRIVER`) still resolves correctly; this fix only touches the unused `NETWORK`
  code path, no regression risk to today's working setup.
