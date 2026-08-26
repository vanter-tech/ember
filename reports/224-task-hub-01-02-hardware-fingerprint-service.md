# Report 224 — HUB-01-02: HardwareFingerprintService

## 1. Identification
- **Report:** 224
- **Task ID:** HUB-01-02
- **Predecessor Task:** HUB-01-01 (report 223)

## 2. Objective
Add `HardwareFingerprintService`, the OSHI-based per-machine fingerprint generator that Ember Hub's licensing will bind a `license.key` to (spec/plan Task 2 of `docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/license/HardwareFingerprintService.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/license/HardwareFingerprintServiceTest.java` (new)

## 4. What Changed?
`HardwareFingerprintService.currentFingerprint()` uses OSHI's `SystemInfo` to read the CPU's `ProcessorIdentifier.getProcessorID()` and the motherboard's `Baseboard.getSerialNumber()`, concatenates them (`cpuId + "|" + boardSerial`), and returns the SHA-256 hex digest (64 lowercase hex chars) via `MessageDigest`. The test asserts the fingerprint is non-blank, stable across two calls on the same run, and matches `[0-9a-f]{64}`. Written and applied exactly as specified in the plan's Task 2 (TDD: failing test confirmed first — compilation error since the class didn't exist — then the implementation made it pass).

## 5. Why It Changed?
This is the hardware-binding primitive `LicenseService` (Task 5) will consume to verify a `license.key` was issued for the machine currently running it, preventing a license file from being copied to a different PC. No Spring wiring yet — plain `new HardwareFingerprintService()`, matching the plan's staged, dependency-free early tasks.

## Verification
- `./mvnw test -Dtest=HardwareFingerprintServiceTest` — PASS
- `./mvnw test` (full suite) — PASS, 790/790 (up from 789/789 after report 223)
