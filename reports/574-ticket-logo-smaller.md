# Report 574 — TICKET-LOGO-SMALLER

## 1. Identification
- **Report number:** 574
- **Task ID:** TICKET-LOGO-SMALLER (commit 4; branch `feat/ticket-logo`)
- **Predecessor:** report 573 (TICKET-LOGO-PREVIEW)

## 2. Objective
The printed and previewed logo was too big; make it smaller, but not tiny.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/logo/TicketLogoProcessor.java`
- `backend/src/test/java/com/vanter/ember/printing/logo/TicketLogoProcessorTest.java`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx`

## 4. What Changed?
- `toBitonalPng` now bounds the logo to half the paper width (`PRINT_WIDTH_FRACTION = 0.5`: 192 dots on 58 mm, 288 on 80 mm) and 120 dots tall (was full width, 300 tall). Aspect ratio preserved, never upscaled, still centered by the agent. One constant to tune.
- The receipt preview no longer uses a fixed height: it sizes the image as the same share of the preview paper that the bitmap takes of the saved paper width, so it matches the print.
- Because processing happens at read time, logos already uploaded shrink without re-uploading. Processor tests updated (192x48 for a wide logo on 58 mm, height cap 120).

## 5. Why It Changed?
A full-width logo dominated the ticket; half the width is a compact header that still reads well.

Verification: backend `./mvnw test` 1564/1564; frontend `build` clean, `lint` 0 errors, `test:run` 232/233 (the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not checked visually or on a real printer; if half is still off, change `PRINT_WIDTH_FRACTION` / `PRINT_MAX_HEIGHT`.
