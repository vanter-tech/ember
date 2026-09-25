# Report 577 — TICKET-LOGO-UPLOAD-HARDENING

## 1. Identification
- **Report number:** 577
- **Task ID:** TICKET-LOGO-UPLOAD-HARDENING (commit 7; branch `feat/ticket-logo`)
- **Predecessor:** report 576 (KITCHEN-TICKET-LOGO)

## 2. Objective
Make the logo upload as safe as reasonably possible against malicious or contaminated images.

## 3. Modified Files
- Backend: `printing/logo/TicketLogoProcessor.java`, `printing/logo/TicketLogoService.java`; new test `TicketLogoUploadSecurityTest`
- Agent: `TicketLogoRenderer.java`, `TicketLogoClient.java`; new test `TicketLogoHardeningTest`

## 4. What Changed?
Already true before (kept): the original file is never stored, only a freshly encoded PNG; the storage key comes only from the tenant id (the uploaded file name is never used); ADMIN-only, plan-gated, 2 MB cap.

Added:
- **Signature check.** Only PNG, JPEG and GIF, by real file signature (not "anything ImageIO can read"): BMP, TIFF, SVG, HTML and renamed executables are refused.
- **Decompression-bomb guard.** Dimensions are read from the header before any pixel is decoded; more than 4096 px on a side or 12 megapixels is refused (a ~100-byte PNG claiming 30 000 x 30 000 is rejected instantly). GIFs with more than 100 frames are refused; only frame 0 is ever used.
- **Failure handling.** Decoder exceptions and `OutOfMemoryError` become a generic "Could not read the image" (400) without echoing decoder internals; `ImageIO` disk caching is disabled; the stored PNG is capped at 1 MB.
- **Declared type.** A declared content type that isn't png/jpeg/gif is refused (never trusted on its own); rejections are logged with tenant and reason only, never the content.
- **Agent side.** The agent also stops trusting the backend: bounded download (1 MB, streamed), PNG signature check, header dimensions capped at 1200 px before decoding; a refused image just means a ticket without logo.
- **Bug found by the new tests:** the GIF path was opened forward-only, so a GIF upload would have failed; fixed (`setInput(..., false, ...)`).

Tests prove: bomb PNG, over-cap dimensions, exe/HTML/SVG/BMP/TIFF refused; PHP appended after a PNG, a script in a PNG `tEXt` chunk, and a JPEG comment segment do **not** survive re-encoding; garbage and truncated files fail generically; legitimate PNG/JPEG/GIF are accepted and always come out as PNG; hostile names/types never reach storage.

## 5. Why It Changed?
The upload is untrusted input that ends up decoded by the JDK's image code both on the server and on the restaurant's PC.

Verification: backend `./mvnw test` 1591/1591; `printing-agent` 99/99. Not covered: an unknown (zero-day) flaw in the JDK's image decoders themselves — reduced by the size limits and by keeping the JDK updated, not eliminated. Not tried against a real MinIO or printer.
